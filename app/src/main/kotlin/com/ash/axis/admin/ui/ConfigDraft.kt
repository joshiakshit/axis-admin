package com.ash.axis.admin.ui

import com.ash.axis.admin.data.ConfigPatch
import com.ash.axis.admin.data.RemoteConfig
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
internal data class ConfigDraft(
    val minimum: String,
    val latestCode: String,
    val latestName: String,
    val updateUrl: String,
    val notice: String,
    val prefixes: String,
    val killSwitch: Boolean,
    val message: String,
    val appVersion: String,
) {
    fun errors(): Set<String> =
        buildSet {
            val min = minimum.toIntOrNull()
            val latest = latestCode.toIntOrNull()
            if (min == null || min < 0) add("minimum")
            if (latest == null || latest < 0) add("latestCode")
            if (latest != null && min != null && latest < min) add("latestCode")
            if (updateUrl.isNotBlank() && !validDownloadUrl(updateUrl)) add("updateUrl")
            if (appVersion.isBlank()) add("appVersion")
            if (prefixes.isNotBlank() && prefixes.split(',').any { !it.trim().matches(Regex("[A-Za-z0-9_-]+")) }) add("prefixes")
        }

    fun patch(base: RemoteConfig): ConfigPatch =
        ConfigPatch(
            minSupportedVersionCode = minimum.toIntOrNull()?.takeIf { it != base.minSupportedVersionCode },
            latestVersionCode = latestCode.toIntOrNull()?.takeIf { it != base.latestVersionCode },
            latestVersionName = latestName.takeIf { it != base.latestVersionName },
            updateUrl = updateUrl.takeIf { it != base.updateUrl }, notice = notice.takeIf { it != base.notice },
            autoApprovePrefix = prefixes.takeIf { it != base.autoApprovePrefix },
            killSwitch = killSwitch.takeIf { it != base.killSwitch }, message = message.takeIf { it != base.message },
            appVersion = appVersion.takeIf { it != base.appVersion }, expectedUpdatedAt = base.updatedAt,
        )

    fun review(base: RemoteConfig): String =
        buildList {
            if (minimum != base.minSupportedVersionCode.toString()) {
                add("Minimum build: ${base.minSupportedVersionCode} → $minimum")
                if ((minimum.toIntOrNull() ?: 0) > base.minSupportedVersionCode) add("Builds below $minimum will be blocked.")
            }
            if (latestCode != base.latestVersionCode.toString()) add("Latest build: ${base.latestVersionCode} → $latestCode")
            if (latestName != base.latestVersionName) add("Version name: $latestName")
            if (updateUrl != base.updateUrl) add("Download link: ${updateUrl.ifBlank { "Removed" }}")
            if (notice != base.notice) add("Notice: ${notice.ifBlank { "Removed" }}")
            if (prefixes != base.autoApprovePrefix) add("Automatic approval: ${prefixes.ifBlank { "Disabled" }}")
            if (appVersion != base.appVersion) add("OTP compatibility version: $appVersion")
            if (killSwitch != base.killSwitch) add(if (killSwitch) "BLOCK ALL STUDENT ACCESS" else "Restore student access")
            if (message != base.message) add("Block message: ${message.ifBlank { "Removed" }}")
        }.joinToString("\n\n")

    companion object {
        fun from(config: RemoteConfig): ConfigDraft =
            ConfigDraft(
                config.minSupportedVersionCode.toString(), config.latestVersionCode.toString(), config.latestVersionName,
                config.updateUrl, config.notice, config.autoApprovePrefix, config.killSwitch, config.message, config.appVersion,
            )
    }
}

private fun validDownloadUrl(value: String): Boolean =
    runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null
    }.getOrDefault(false)
