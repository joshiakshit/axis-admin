package com.ash.axis.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.admin.data.AdminRepository
import com.ash.axis.admin.data.AdminUser
import com.ash.axis.admin.data.ConfigPatch
import com.ash.axis.admin.data.HealthResponse
import com.ash.axis.admin.data.LoginMethod
import com.ash.axis.admin.data.RemoteConfig
import com.ash.axis.admin.data.UserAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AdminPage { DASHBOARD, USERS, CONFIG }

data class AdminUiState(
    val signedIn: Boolean = false,
    val otpRequested: Boolean = false,
    val username: String = "",
    val page: AdminPage = AdminPage.DASHBOARD,
    val loading: Boolean = false,
    val health: HealthResponse? = null,
    val users: List<AdminUser> = emptyList(),
    val config: RemoteConfig? = null,
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class AdminViewModel
    @Inject
    constructor(
        private val repository: AdminRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AdminUiState(signedIn = repository.hasSession()))
        val state: StateFlow<AdminUiState> = mutableState.asStateFlow()

        init {
            if (mutableState.value.signedIn) loadDashboard()
        }

        fun requestOtp(
            contact: String,
            method: LoginMethod,
        ) = runOperation {
            val username = repository.requestOtp(contact.trim(), method)
            mutableState.update { it.copy(otpRequested = true, username = username, message = "OTP sent") }
        }

        fun completeLogin(
            contact: String,
            otp: String,
        ) = runOperation {
            repository.completeLogin(contact.trim(), otp.trim(), mutableState.value.username)
            mutableState.update { it.copy(signedIn = true, otpRequested = false, page = AdminPage.DASHBOARD) }
            loadDashboard()
        }

        fun logout() {
            repository.logout()
            mutableState.value = AdminUiState()
        }

        fun select(page: AdminPage) {
            mutableState.update { it.copy(page = page, error = null, message = null) }
            when (page) {
                AdminPage.DASHBOARD -> loadDashboard()
                AdminPage.USERS -> loadUsers()
                AdminPage.CONFIG -> loadConfig()
            }
        }

        fun loadDashboard() =
            runOperation {
                mutableState.update { it.copy(health = repository.health()) }
            }

        fun loadUsers() =
            runOperation {
                mutableState.update { it.copy(users = repository.users().sortedWith(userOrder)) }
            }

        fun loadConfig() =
            runOperation {
                mutableState.update { it.copy(config = repository.config()) }
            }

        fun setUserStatus(
            admno: String,
            action: UserAction,
        ) = runOperation {
            val updated = repository.setUserStatus(admno, action)
            mutableState.update { state ->
                state.copy(users = state.users.map { if (it.admno == admno) updated else it }.sortedWith(userOrder))
            }
        }

        fun approveAll() =
            runOperation {
                val count = repository.approveAll()
                mutableState.update { it.copy(message = "Approved $count users") }
                loadUsers()
            }

        fun saveConfig(patch: ConfigPatch) =
            runOperation {
                mutableState.update { it.copy(config = repository.saveConfig(patch), message = "Config saved") }
            }

        @Suppress("TooGenericExceptionCaught")
        private fun runOperation(block: suspend () -> Unit) {
            viewModelScope.launch {
                mutableState.update { it.copy(loading = true, error = null, message = null) }
                try {
                    block()
                } catch (error: Exception) {
                    mutableState.update {
                        it.copy(
                            signedIn = repository.hasSession(),
                            error = error.message ?: "Request failed",
                        )
                    }
                } finally {
                    mutableState.update { it.copy(loading = false) }
                }
            }
        }

        private companion object {
            val userOrder = compareBy<AdminUser> { if (it.status == "pending") 0 else 1 }.thenByDescending { it.lastSeenAt }
        }
    }
