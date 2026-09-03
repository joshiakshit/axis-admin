package com.ash.axis.admin.data

import android.os.Build
import com.ash.axis.admin.BuildConfig
import retrofit2.HttpException
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository
    @Inject
    constructor(
        private val adminApi: AdminApi,
        private val authApi: IcloudAuthApi,
        private val store: SessionStore,
    ) {
        suspend fun requestOtp(
            contact: String,
            method: LoginMethod,
        ): String {
            val response =
                authApi.requestOtp(
                    mapOf(
                        "method" to method.apiValue,
                        "contact" to contact,
                        "lastmodifiedby" to contact,
                        "deviceid" to deviceId(),
                        "appversion" to BuildConfig.VERSION_NAME,
                    ),
                )
            return response.data?.username ?: contact
        }

        suspend fun completeLogin(
            contact: String,
            otp: String,
            username: String,
        ): AxisSession {
            val response =
                authApi.validateOtp(
                    mapOf(
                        "otp" to otp,
                        "contact" to contact,
                        "username" to username,
                        "lastmodifiedby" to contact,
                        "deviceid" to deviceId(),
                        "appversion" to BuildConfig.VERSION_NAME,
                    ),
                )
            val tokens = response.data?.token ?: error(response.data?.message ?: "Login did not return tokens")
            val session =
                adminApi.createSession(
                    AdminSessionRequest(
                        accessToken = tokens.accessToken,
                        refreshToken = tokens.refreshToken,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                        androidSdk = Build.VERSION.SDK_INT,
                    ),
                )
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

        private fun deviceId(): String = "axis-admin-${Build.MODEL}"
    }
