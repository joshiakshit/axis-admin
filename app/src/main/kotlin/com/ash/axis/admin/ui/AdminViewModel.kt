package com.ash.axis.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ash.axis.admin.data.AdminRepository
import com.ash.axis.admin.data.AdminUser
import com.ash.axis.admin.data.AuditEntry
import com.ash.axis.admin.data.ConfigPatch
import com.ash.axis.admin.data.HealthResponse
import com.ash.axis.admin.data.RemoteConfig
import com.ash.axis.admin.data.StatsResponse
import com.ash.axis.admin.data.UserAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AdminPage { DASHBOARD, USERS, CONFIG, AUDIT }

data class AdminUiState(
    val provisioned: Boolean = false,
    val signedIn: Boolean = false,
    val page: AdminPage = AdminPage.DASHBOARD,
    val loading: Boolean = false,
    val health: HealthResponse? = null,
    val stats: StatsResponse? = null,
    val users: List<AdminUser> = emptyList(),
    val config: RemoteConfig? = null,
    val auditLog: List<AuditEntry> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class AdminViewModel
    @Inject
    constructor(
        private val repository: AdminRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AdminUiState(provisioned = repository.isProvisioned()))
        val state: StateFlow<AdminUiState> = mutableState.asStateFlow()

        init {
            if (mutableState.value.provisioned) connect()
        }

        fun provision(token: String) {
            repository.provision(token)
            mutableState.update { it.copy(provisioned = true, error = null) }
            connect()
        }

        fun connect() =
            runOperation {
                if (!repository.hasSession()) repository.openPersonalDeviceSession()
                mutableState.update {
                    it.copy(
                        provisioned = true,
                        signedIn = true,
                        page = AdminPage.DASHBOARD,
                        health = repository.health(),
                    )
                }
            }

        fun select(page: AdminPage) {
            mutableState.update { it.copy(page = page, error = null, message = null) }
            refresh(page)
        }

        fun refresh(page: AdminPage = mutableState.value.page) {
            when (page) {
                AdminPage.DASHBOARD -> loadDashboard()
                AdminPage.USERS -> loadUsers()
                AdminPage.CONFIG -> loadConfig()
                AdminPage.AUDIT -> loadAuditLog()
            }
        }

        fun loadDashboard() =
            runOperation {
                val health = repository.health()
                val stats = repository.stats()
                mutableState.update { it.copy(health = health, stats = stats) }
            }

        fun loadUsers() =
            runOperation {
                mutableState.update { it.copy(users = repository.users().sortedWith(userOrder)) }
            }

        fun loadConfig() =
            runOperation {
                mutableState.update { it.copy(config = repository.config()) }
            }

        fun loadAuditLog() =
            runOperation {
                mutableState.update { it.copy(auditLog = repository.auditLog()) }
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
