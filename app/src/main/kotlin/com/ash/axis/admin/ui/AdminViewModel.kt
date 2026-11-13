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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject

enum class AdminPage(val title: String) { DASHBOARD("Overview"), USERS("Users"), CONFIG("Controls"), AUDIT("Activity") }

data class AdminUiState(
    val provisioned: Boolean = false,
    val signedIn: Boolean = false,
    val page: AdminPage = AdminPage.DASHBOARD,
    val connecting: Boolean = false,
    val loadingPages: Set<AdminPage> = emptySet(),
    val busyUsers: Set<String> = emptySet(),
    val userErrors: Map<String, String> = emptyMap(),
    val savingConfig: Boolean = false,
    val health: HealthResponse? = null,
    val healthUpdatedAt: String? = null,
    val stats: StatsResponse? = null,
    val statsError: String? = null,
    val users: List<AdminUser> = emptyList(),
    val usersLoaded: Boolean = false,
    val config: RemoteConfig? = null,
    val auditLog: List<AuditEntry> = emptyList(),
    val auditLoaded: Boolean = false,
    val auditCursor: Int? = null,
    val pageErrors: Map<AdminPage, String> = emptyMap(),
    val error: String? = null,
    val message: String? = null,
) {
    val loading: Boolean get() = connecting || page in loadingPages
    val pendingCount: Int get() = health?.pending ?: users.count { it.status == "pending" }
}

// Presentation boundary: preserve cancellation and turn other failures into visible recovery states.
@Suppress("TooGenericExceptionCaught")
@HiltViewModel
class AdminViewModel
    @Inject
    constructor(private val repository: AdminRepository) : ViewModel() {
        private val mutableState = MutableStateFlow(AdminUiState(provisioned = repository.isProvisioned()))
        val state: StateFlow<AdminUiState> = mutableState.asStateFlow()
        private val pageJobs = mutableMapOf<AdminPage, Job>()
        private val pageVersions = mutableMapOf<AdminPage, Int>()

        init {
            if (mutableState.value.provisioned) connect()
        }

        fun provision(token: String) = authenticate { repository.provision(token) }

        fun connect() =
            authenticate {
                if (!repository.hasSession()) repository.openPersonalDeviceSession()
            }

        private fun authenticate(block: suspend () -> Unit) {
            if (mutableState.value.connecting) return
            mutableState.update { it.copy(connecting = true, error = null) }
            viewModelScope.launch {
                try {
                    block()
                    mutableState.update { it.copy(provisioned = true, signedIn = true, error = null) }
                    refresh()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableState.update { it.copy(error = error.readableMessage()) }
                } finally {
                    mutableState.update { it.copy(connecting = false) }
                }
            }
        }

        fun forgetDevice() {
            viewModelScope.coroutineContext.cancelChildren()
            pageJobs.clear()
            repository.forgetDevice()
            mutableState.value = AdminUiState()
        }

        fun dismissMessage() = mutableState.update { it.copy(message = null, error = null) }

        fun select(page: AdminPage) {
            mutableState.update { it.copy(page = page, error = null, message = null) }
            refresh(page)
        }

        fun refresh(page: AdminPage = mutableState.value.page) {
            val version = (pageVersions[page] ?: 0) + 1
            pageVersions[page] = version
            pageJobs[page]?.cancel()
            mutableState.update { it.copy(loadingPages = it.loadingPages + page, pageErrors = it.pageErrors - page) }
            pageJobs[page] =
                viewModelScope.launch {
                    try {
                        when (page) {
                            AdminPage.DASHBOARD -> dashboard()
                            AdminPage.USERS -> updateUsers(repository.users())
                            AdminPage.CONFIG -> {
                                val config = repository.config()
                                mutableState.update { it.copy(config = config) }
                            }
                            AdminPage.AUDIT -> {
                                val response = repository.auditLog()
                                mutableState.update {
                                    it.copy(auditLog = response.entries, auditLoaded = true, auditCursor = response.nextCursor)
                                }
                            }
                        }
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        mutableState.update { it.copy(pageErrors = it.pageErrors + (page to error.readableMessage())) }
                    } finally {
                        if (pageVersions[page] == version) {
                            mutableState.update { it.copy(loadingPages = it.loadingPages - page) }
                        }
                    }
                }
        }

        private suspend fun dashboard() {
            val health = repository.health()
            mutableState.update { it.copy(health = health, healthUpdatedAt = Instant.now().toString()) }
            try {
                val stats = repository.stats()
                mutableState.update { it.copy(stats = stats, statsError = null) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update { it.copy(statsError = error.readableMessage()) }
            }
        }

        fun olderAudit() {
            val cursor = mutableState.value.auditCursor ?: return
            if (AdminPage.AUDIT in mutableState.value.loadingPages) return
            mutableState.update { it.copy(loadingPages = it.loadingPages + AdminPage.AUDIT) }
            viewModelScope.launch {
                try {
                    val response = repository.auditLog(cursor)
                    mutableState.update {
                        it.copy(
                            auditLog = (it.auditLog + response.entries).distinctBy { entry -> entry.id },
                            auditCursor = response.nextCursor,
                            pageErrors = it.pageErrors - AdminPage.AUDIT,
                        )
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableState.update { it.copy(pageErrors = it.pageErrors + (AdminPage.AUDIT to error.readableMessage())) }
                } finally {
                    mutableState.update { it.copy(loadingPages = it.loadingPages - AdminPage.AUDIT) }
                }
            }
        }

        private fun updateUsers(users: List<AdminUser>) {
            mutableState.update { state ->
                state.copy(users = users.sortedWith(userOrder), usersLoaded = true, health = state.health?.withUsers(users))
            }
        }

        fun setUserStatus(
            admno: String,
            action: UserAction,
        ) = changeUsers(listOf(admno), action)

        fun approveSelected(admnos: List<String>) {
            val pending = mutableState.value.users.filter { it.admno in admnos && it.status == "pending" }.map { it.admno }
            changeUsers(pending, UserAction.ALLOW)
        }

        private fun changeUsers(
            admnos: List<String>,
            action: UserAction,
        ) {
            val targets = admnos.distinct().filterNot { it in mutableState.value.busyUsers }
            if (targets.isEmpty()) return
            listOf(AdminPage.USERS, AdminPage.DASHBOARD).forEach { page ->
                pageVersions[page] = (pageVersions[page] ?: 0) + 1
                pageJobs[page]?.cancel()
            }
            mutableState.update { it.copy(loadingPages = it.loadingPages - setOf(AdminPage.USERS, AdminPage.DASHBOARD)) }
            mutableState.update { it.copy(busyUsers = it.busyUsers + targets, userErrors = it.userErrors - targets.toSet()) }
            viewModelScope.launch {
                var changed = 0
                try {
                    targets.forEach { admno ->
                        try {
                            val user = repository.setUserStatus(admno, action)
                            updateUsers(mutableState.value.users.map { if (it.admno == admno) user else it })
                            changed++
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            mutableState.update { it.copy(userErrors = it.userErrors + (admno to error.readableMessage())) }
                        }
                    }
                    mutableState.update {
                        it.copy(
                            message =
                                if (changed == targets.size) {
                                    "Updated $changed ${if (changed == 1) "user" else "users"}"
                                } else {
                                    "Updated $changed of ${targets.size}. Review the errors below."
                                },
                        )
                    }
                } finally {
                    mutableState.update { it.copy(busyUsers = it.busyUsers - targets.toSet()) }
                }
            }
        }

        fun saveConfig(patch: ConfigPatch) {
            if (mutableState.value.savingConfig) return
            mutableState.update { it.copy(savingConfig = true, error = null) }
            viewModelScope.launch {
                try {
                    val config = repository.saveConfig(patch)
                    mutableState.update { it.copy(config = config, message = "Changes saved") }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    mutableState.update { it.copy(error = error.readableMessage()) }
                } finally {
                    mutableState.update { it.copy(savingConfig = false) }
                }
            }
        }

        private companion object {
            val userOrder = compareBy<AdminUser> { if (it.status == "pending") 0 else 1 }.thenBy { it.createdAt }
        }
    }

private fun HealthResponse.withUsers(users: List<AdminUser>): HealthResponse =
    copy(
        users = users.size,
        pending = users.count { it.status == "pending" },
        approved = users.count { it.status == "approved" },
        banned = users.count { it.status == "banned" },
    )

fun Exception.readableMessage(): String =
    when (this) {
        is HttpException ->
            when (code()) {
                401 -> "The app token is invalid or has been replaced. Enter the current token."
                403 -> "This administrator does not have permission for this action."
                404 -> "This feature is unavailable on the current backend."
                409, 412 -> "Settings changed on the server. Refresh and review your draft before saving."
                in 500..599 -> "The service is unavailable. Please try again."
                else -> "The request failed (${code()}). Please try again."
            }
        is IOException -> "Unable to connect. Check your connection and try again."
        else -> message ?: "Something went wrong. Please try again."
    }
