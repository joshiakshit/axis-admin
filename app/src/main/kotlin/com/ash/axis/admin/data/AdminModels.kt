package com.ash.axis.admin.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AxisSession(
    val status: String = "",
    val role: String = "",
    val admno: String = "",
    val name: String = "",
    val sessionToken: String? = null,
)

@Serializable
data class AdminUser(
    val admno: String = "",
    val name: String = "",
    val email: String = "",
    val status: String = "",
    val role: String = "user",
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("last_seen_at") val lastSeenAt: String = "",
    @SerialName("first_seen_at") val firstSeenAt: String = "",
    @SerialName("approved_at") val approvedAt: String = "",
    @SerialName("app_version_name") val appVersionName: String = "",
    @SerialName("app_version_code") val appVersionCode: Int = 0,
    @SerialName("device_model") val deviceModel: String = "",
    @SerialName("android_sdk") val androidSdk: Int = 0,
    @SerialName("session_count") val sessionCount: Int = 0,
)

@Serializable
data class UsersResponse(val users: List<AdminUser> = emptyList())

@Serializable
data class HealthResponse(
    val service: String = "",
    val users: Int = 0,
    val pending: Int = 0,
    val approved: Int = 0,
    val banned: Int = 0,
    val apkUploaded: Boolean = false,
    val metrics: Map<String, Int> = emptyMap(),
)

@Serializable
data class RemoteConfig(
    val appVersion: String = "",
    val minSupportedVersionCode: Int = 0,
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val updateUrl: String = "",
    val killSwitch: Boolean = false,
    val message: String = "",
    val notice: String = "",
    val autoApprovePrefix: String = "",
    val updatedAt: String = "",
)

@Serializable
data class ConfigPatch(
    val appVersion: String? = null,
    val minSupportedVersionCode: Int? = null,
    val latestVersionCode: Int? = null,
    val latestVersionName: String? = null,
    val updateUrl: String? = null,
    val killSwitch: Boolean? = null,
    val message: String? = null,
    val notice: String? = null,
    val autoApprovePrefix: String? = null,
)

@Serializable
data class ApproveAllResponse(val approved: Int = 0)

@Serializable
data class VersionBucket(val version: String = "", val count: Int = 0)

@Serializable
data class DeviceBucket(val device: String = "", val count: Int = 0)

@Serializable
data class SdkBucket(val sdk: Int = 0, val count: Int = 0)

@Serializable
data class StatsResponse(
    val totalUsers: Int = 0,
    val activeLastDay: Int = 0,
    val activeLastWeek: Int = 0,
    val activeLastMonth: Int = 0,
    val versionDistribution: List<VersionBucket> = emptyList(),
    val deviceDistribution: List<DeviceBucket> = emptyList(),
    val sdkDistribution: List<SdkBucket> = emptyList(),
)

@Serializable
data class AuditEntry(
    val id: Int = 0,
    @SerialName("actor_admno") val actorAdmno: String = "",
    val action: String = "",
    @SerialName("target_admno") val targetAdmno: String = "",
    val detail: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class AuditLogResponse(val entries: List<AuditEntry> = emptyList())

enum class UserAction { ALLOW, KICK, BAN }

sealed interface DangerousAction {
    data class Ban(val admno: String) : DangerousAction

    data class Kick(val admno: String) : DangerousAction

    data object ApproveAll : DangerousAction

    data class RaiseMinimum(val oldCode: Int, val newCode: Int) : DangerousAction

    data class SetKillSwitch(val enabled: Boolean) : DangerousAction
}

fun confirmationText(action: DangerousAction): String =
    when (action) {
        is DangerousAction.Ban -> "Ban ${action.admno}. They cannot sign in until approved."
        is DangerousAction.Kick -> "Kick ${action.admno}. Their access returns to pending."
        DangerousAction.ApproveAll -> "Approve every pending user. They can sign in immediately."
        is DangerousAction.RaiseMinimum ->
            "Raise the minimum from ${action.oldCode} to ${action.newCode}. Versions below version code ${action.newCode} will be blocked."
        is DangerousAction.SetKillSwitch ->
            if (action.enabled) {
                "Enable the kill switch. This will block all student app access."
            } else {
                "Disable the kill switch. Student app access will resume."
            }
    }
