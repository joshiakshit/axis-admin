package com.ash.axis.admin.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository
    @Inject
    constructor(
        private val adminApi: AdminApi,
        private val store: SessionStore,
    ) {
        private val sessionMutex = Mutex()

        fun isProvisioned(): Boolean = store.readDeviceCredential() != null

        suspend fun provision(token: String) =
            sessionMutex.withLock {
                val credential = token.trim()
                require(credential.isNotBlank()) { "Enter the app token" }
                val session = createSession(credential)
                store.saveDeviceCredential(credential)
                store.save(requireNotNull(session.sessionToken))
            }

        suspend fun openPersonalDeviceSession(): AxisSession {
            val credential = store.readDeviceCredential() ?: error("This installation is not provisioned")
            val session = createSession(credential)
            store.save(requireNotNull(session.sessionToken))
            return session
        }

        private suspend fun createSession(credential: String): AxisSession {
            val session = adminApi.createDeviceSession("Bearer $credential")
            val axisToken = session.sessionToken
            if (session.status != "approved" || session.role != "admin" || axisToken.isNullOrBlank()) {
                error("This identity is not an approved Axis administrator")
            }
            return session
        }

        fun hasSession(nowEpochSeconds: Long = System.currentTimeMillis() / 1000): Boolean {
            val token = store.read() ?: return false
            val expiry = tokenExpiry(token)
            if (expiry <= nowEpochSeconds) {
                store.clear()
                return false
            }
            return true
        }

        fun forgetDevice() = store.forgetDevice()

        suspend fun users(): List<AdminUser> = authorized { adminApi.listUsers(it).users }

        suspend fun health(): HealthResponse = authorized { adminApi.health(it) }

        suspend fun stats(): StatsResponse = authorized { adminApi.stats(it) }

        suspend fun auditLog(before: Int? = null): AuditLogResponse = authorized { adminApi.auditLog(it, before) }

        suspend fun config(): RemoteConfig = authorized { adminApi.getConfig(it) }

        suspend fun saveConfig(patch: ConfigPatch): RemoteConfig = authorized { adminApi.putConfig(it, patch) }

        suspend fun approveAll(): Int = authorized { adminApi.approveAll(it).approved }

        suspend fun setUserStatus(
            admno: String,
            action: UserAction,
        ): AdminUser =
            authorized { authorization ->
                when (action) {
                    UserAction.ALLOW -> adminApi.allow(admno, authorization)
                    UserAction.KICK -> adminApi.kick(admno, authorization)
                    UserAction.BAN -> adminApi.ban(admno, authorization)
                }
            }

        private suspend fun <T> authorized(block: suspend (String) -> T): T {
            val token =
                sessionMutex.withLock {
                    if (!hasSession()) openPersonalDeviceSession()
                    store.read() ?: error("Admin session required")
                }
            return try {
                block("Bearer $token")
            } catch (error: HttpException) {
                if (error.code() != 401) throw error
                val renewed =
                    sessionMutex.withLock {
                        if (store.read() == token || !hasSession()) {
                            store.clear()
                            openPersonalDeviceSession()
                        }
                        store.read() ?: error("Admin session required")
                    }
                try {
                    block("Bearer $renewed")
                } catch (retryError: HttpException) {
                    if (retryError.code() == 401) store.clear()
                    throw retryError
                }
            }
        }

        private fun tokenExpiry(token: String): Long =
            runCatching {
                val payload = String(Base64.getUrlDecoder().decode(token.split(".")[1]))
                Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)?.toLong() ?: 0
            }.getOrDefault(0)
    }
