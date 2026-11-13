package com.ash.axis.admin.ui

import com.ash.axis.admin.data.AdminRepository
import com.ash.axis.admin.data.AdminUser
import com.ash.axis.admin.data.HealthResponse
import com.ash.axis.admin.data.UserAction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `saved provisioning is visible before automatic connection completes`() {
        val repository = mockk<AdminRepository>()
        every { repository.isProvisioned() } returns true
        every { repository.hasSession(any()) } returns true
        coEvery { repository.health() } returns HealthResponse()

        val viewModel = AdminViewModel(repository)

        assertTrue(viewModel.state.value.provisioned)
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.state.value.signedIn)
    }

    @Test
    fun `missing analytics preserves successful health refresh`() {
        val repository = mockk<AdminRepository>()
        every { repository.isProvisioned() } returns false
        coEvery { repository.health() } returns HealthResponse(pending = 4)
        coEvery { repository.stats() } throws IllegalStateException("Analytics unavailable")
        val vm = AdminViewModel(repository)
        vm.refresh()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(4, vm.state.value.health?.pending)
        assertEquals("Analytics unavailable", vm.state.value.statsError)
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.pageErrors.isEmpty())
    }

    @Test
    fun `invalid token leaves provisioning editable`() {
        val repository = mockk<AdminRepository>()
        every { repository.isProvisioned() } returns false
        coEvery { repository.provision(any()) } throws IllegalStateException("Invalid token")
        val vm = AdminViewModel(repository)
        vm.provision("wrong")
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(vm.state.value.provisioned)
        assertFalse(vm.state.value.signedIn)
        assertFalse(vm.state.value.connecting)
        assertEquals("Invalid token", vm.state.value.error)
    }

    @Test
    fun `duplicate approval taps are ignored and pending counts update`() {
        val repository = mockk<AdminRepository>()
        val user = AdminUser(admno = "21001", status = "pending")
        val result = CompletableDeferred<AdminUser>()
        every { repository.isProvisioned() } returns false
        coEvery { repository.users() } returns listOf(user)
        coEvery { repository.setUserStatus(user.admno, UserAction.ALLOW) } coAnswers { result.await() }
        val vm = AdminViewModel(repository)
        vm.select(AdminPage.USERS)
        dispatcher.scheduler.advanceUntilIdle()
        vm.setUserStatus(user.admno, UserAction.ALLOW)
        vm.setUserStatus(user.admno, UserAction.ALLOW)
        dispatcher.scheduler.runCurrent()
        assertTrue(user.admno in vm.state.value.busyUsers)
        result.complete(user.copy(status = "approved"))
        dispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { repository.setUserStatus(user.admno, UserAction.ALLOW) }
        assertEquals(0, vm.state.value.pendingCount)
        assertTrue(vm.state.value.busyUsers.isEmpty())
    }
}
