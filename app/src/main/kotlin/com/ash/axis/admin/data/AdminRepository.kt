package com.ash.axis.admin.data

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
        fun isProvisioned(): Boolean = store.readDeviceCredential() != null

        fun provision(token: String) = store.saveDeviceCredential(token.trim())

        suspend fun openPersonalDeviceSession(): AxisSession {
            val credential = store.readDeviceCredential() ?: error("This installation is not provisioned")
            val session =
                adminApi.createDeviceSession("Bearer $credential")
            val axisToken = session.sessionToken
            if (session.status != "approved" || session.role != "admin" || axisToken.isNullOrBlank()) {
                store.clear()
                error("This identity is not an approved Axis administrator")
            }
            store.save(axisToken)
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

        fun logout() = store.clear()

        suspend fun users(): List<AdminUser> = authorized { adminApi.listUsers(it).users }

        suspend fun health(): HealthResponse = authorized { adminApi.health(it) }

        suspend fun stats(): StatsResponse = authorized { adminApi.stats(it) }

        suspend fun auditLog(): List<AuditEntry> = authorized { adminApi.auditLog(it).entries }

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
            val token = store.read() ?: error("Admin session required")
            return try {
                block("Bearer $token")
            } catch (error: HttpException) {
                if (error.code() == 401 || error.code() == 403) store.clear()
                throw error
            }
        }

        private fun tokenExpiry(token: String): Long =
            runCatching {
                val payload = String(Base64.getUrlDecoder().decode(token.split(".")[1]))
                Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)?.toLong() ?: 0
            }.getOrDefault(0)
    }
