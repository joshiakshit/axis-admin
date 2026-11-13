package com.ash.axis.admin.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import retrofit2.HttpException
import retrofit2.Response
import java.util.Base64

class AdminRepositoryTest {
    private val adminApi = mockk<AdminApi>()
    private val store = mockk<SessionStore>(relaxed = true)
    private val repository = AdminRepository(adminApi, store)

    @Test
    fun `personal device login stores an approved admin Axis session`() =
        runBlocking {
            every { store.readDeviceCredential() } returns "device-token"
            coEvery { adminApi.createDeviceSession("Bearer device-token") } returns
                AxisSession("approved", "admin", "21000", "Admin", "axis-token")

            repository.openPersonalDeviceSession()

            verify { store.save("axis-token") }
        }

    @Test
    fun `personal device login rejects a non-admin response`() {
        every { store.readDeviceCredential() } returns "device-token"
        coEvery { adminApi.createDeviceSession(any()) } returns
            AxisSession("approved", "user", "21001", "User", "user-token")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.openPersonalDeviceSession() }
        }
        verify(exactly = 0) { store.save(any()) }
    }

    @Test
    fun `personal device login requires a provisioned credential`() {
        every { store.readDeviceCredential() } returns null

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.openPersonalDeviceSession() }
        }
        coVerify(exactly = 0) { adminApi.createDeviceSession(any()) }
    }

    @Test
    fun `expired stored session is cleared`() {
        val token = jwt(mapOf("exp" to 1))
        every { store.read() } returns token

        assertFalse(repository.hasSession(nowEpochSeconds = 2))
        verify { store.clear() }
    }

    @Test
    fun `permission denial does not destroy a valid session`() {
        every { store.read() } returns jwt(mapOf("exp" to 9_999_999_999))
        coEvery { adminApi.listUsers(any()) } throws httpException(403)

        assertThrows(HttpException::class.java) { runBlocking { repository.users() } }
        verify(exactly = 0) { store.clear() }
        coVerify(exactly = 0) { adminApi.createDeviceSession(any()) }
    }

    @Test
    fun `invalid provisioning does not replace a working credential`() =
        runBlocking {
            coEvery { adminApi.createDeviceSession(any()) } throws httpException(401)
            assertThrows(HttpException::class.java) { runBlocking { repository.provision("bad") } }
            verify(exactly = 0) { store.saveDeviceCredential(any()) }
            verify(exactly = 0) { store.save(any()) }
        }

    @Test
    fun `an expired session renews before requesting users`() =
        runBlocking {
            val expired = jwt(mapOf("exp" to 1))
            val renewed = jwt(mapOf("exp" to 9_999_999_999))
            every { store.read() } returns expired andThen renewed
            every { store.readDeviceCredential() } returns "device-token"
            coEvery { adminApi.createDeviceSession(any()) } returns AxisSession("approved", "admin", sessionToken = renewed)
            coEvery { adminApi.listUsers("Bearer $renewed") } returns UsersResponse()

            repository.users()

            coVerify(exactly = 1) { adminApi.createDeviceSession("Bearer device-token") }
            coVerify(exactly = 1) { adminApi.listUsers("Bearer $renewed") }
            coVerify(exactly = 0) { adminApi.listUsers("Bearer $expired") }
        }

    @Test
    fun `an unauthorized request retries only once with a renewed session`() =
        runBlocking {
            val original = jwt(mapOf("exp" to 9_999_999_999))
            val renewed = jwt(mapOf("exp" to 9_999_999_998))
            var saved = original
            every { store.read() } answers { saved }
            every { store.save(any()) } answers { saved = firstArg() }
            every { store.readDeviceCredential() } returns "device-token"
            coEvery { adminApi.createDeviceSession(any()) } returns AxisSession("approved", "admin", sessionToken = renewed)
            coEvery { adminApi.listUsers(any()) } throws httpException(401)

            assertThrows(HttpException::class.java) { runBlocking { repository.users() } }

            coVerify(exactly = 1) { adminApi.createDeviceSession(any()) }
            coVerify(exactly = 2) { adminApi.listUsers(any()) }
        }

    @Test
    fun `concurrent expired requests share a single session renewal`() =
        runBlocking {
            val renewed = jwt(mapOf("exp" to 9_999_999_999))
            var saved: String? = jwt(mapOf("exp" to 1))
            val gate = CompletableDeferred<Unit>()
            every { store.read() } answers { saved }
            every { store.clear() } answers { saved = null }
            every { store.save(any()) } answers { saved = firstArg() }
            every { store.readDeviceCredential() } returns "device-token"
            coEvery { adminApi.createDeviceSession(any()) } coAnswers {
                gate.await()
                AxisSession("approved", "admin", sessionToken = renewed)
            }
            coEvery { adminApi.listUsers("Bearer $renewed") } returns UsersResponse()
            val first = async { repository.users() }
            val second = async { repository.users() }
            yield()
            gate.complete(Unit)
            first.await()
            second.await()
            coVerify(exactly = 1) { adminApi.createDeviceSession(any()) }
            coVerify(exactly = 2) { adminApi.listUsers("Bearer $renewed") }
        }

    @Test
    fun `user actions call their exact endpoints`() =
        runBlocking {
            every { store.read() } returns jwt(mapOf("exp" to 9_999_999_999))
            val user = AdminUser(admno = "21001", status = "pending")
            coEvery { adminApi.allow(any(), any()) } returns user.copy(status = "approved")
            coEvery { adminApi.kick(any(), any()) } returns user
            coEvery { adminApi.ban(any(), any()) } returns user.copy(status = "banned")

            repository.setUserStatus("21001", UserAction.ALLOW)
            repository.setUserStatus("21001", UserAction.KICK)
            repository.setUserStatus("21001", UserAction.BAN)

            coVerify(exactly = 1) { adminApi.allow("21001", any()) }
            coVerify(exactly = 1) { adminApi.kick("21001", any()) }
            coVerify(exactly = 1) { adminApi.ban("21001", any()) }
        }

    @Test
    fun `dangerous actions state their exact effect`() {
        assertEquals("Ban 21001. They cannot sign in until approved.", confirmationText(DangerousAction.Ban("21001")))
        assertEquals("Kick 21001. Their access returns to pending.", confirmationText(DangerousAction.Kick("21001")))
        assertTrue(confirmationText(DangerousAction.RaiseMinimum(4, 5)).contains("below version code 5"))
        assertTrue(confirmationText(DangerousAction.SetKillSwitch(true)).contains("block all student app access"))
    }

    private fun httpException(code: Int): HttpException =
        HttpException(Response.error<Unit>(code, "error".toResponseBody("text/plain".toMediaType())))

    private fun jwt(payload: Map<String, Any>): String {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val json = """{"exp":${payload["exp"]}}"""
        return "${encoder.encodeToString("{}".toByteArray())}.${encoder.encodeToString(json.toByteArray())}.sig"
    }
}
