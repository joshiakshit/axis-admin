package com.ash.axis.admin.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
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
    fun `personal device login rejects a non-admin response and clears storage`() {
        every { store.readDeviceCredential() } returns "device-token"
        coEvery { adminApi.createDeviceSession(any()) } returns
            AxisSession("approved", "user", "21001", "User", "user-token")

        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.openPersonalDeviceSession() }
        }
        verify { store.clear() }
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
    fun `authorization failure clears the Axis session`() {
        every { store.read() } returns jwt(mapOf("exp" to 9_999_999_999))
        coEvery { adminApi.listUsers(any()) } throws httpException(403)

        assertThrows(HttpException::class.java) { runBlocking { repository.users() } }
        verify { store.clear() }
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
