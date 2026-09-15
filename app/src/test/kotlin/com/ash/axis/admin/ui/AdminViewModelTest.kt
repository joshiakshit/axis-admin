package com.ash.axis.admin.ui

import com.ash.axis.admin.data.AdminRepository
import com.ash.axis.admin.data.HealthResponse
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
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
}
