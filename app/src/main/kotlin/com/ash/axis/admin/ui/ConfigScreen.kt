package com.ash.axis.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ash.axis.admin.data.ConfigPatch
import com.ash.axis.admin.data.RemoteConfig
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Composable
@Suppress("LongMethod")
internal fun ConfigScreen(
    config: RemoteConfig?,
    saving: Boolean,
    dirty: (Boolean) -> Unit,
    save: (ConfigPatch) -> Unit,
) {
    if (config == null) return
    var draftJson by rememberSaveable { mutableStateOf(Json.encodeToString(ConfigDraft.from(config))) }
    var baselineJson by rememberSaveable { mutableStateOf(Json.encodeToString(config)) }
    var review by remember { mutableStateOf(false) }
    var reset by remember { mutableStateOf(false) }
    val draft = Json.decodeFromString<ConfigDraft>(draftJson)
    val baseline = Json.decodeFromString<RemoteConfig>(baselineJson)
    val changed = draft != ConfigDraft.from(baseline)
    val conflict = config.updatedAt != baseline.updatedAt && draft != ConfigDraft.from(config)
    val errors = draft.errors()
    LaunchedEffect(changed) { dirty(changed) }
    LaunchedEffect(config) {
        if (!changed || draft == ConfigDraft.from(config)) {
            baselineJson = Json.encodeToString(config)
            draftJson = Json.encodeToString(ConfigDraft.from(config))
        }
    }
    val edit: (ConfigDraft) -> Unit = { draftJson = Json.encodeToString(it) }
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Remote controls", style = MaterialTheme.typography.headlineSmall)
                Text(
                    if (changed) "Unsaved changes" else "Everything is up to date",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = { review = true }, enabled = canSaveDraft(changed, errors, saving, conflict)) {
                Text(if (saving) "Saving…" else "Review")
            }
        }
        LazyColumn(contentPadding = listPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (conflict) {
                item {
                    Text(
                        "Settings changed on the server. Your draft has been kept. Reload before making further changes.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = { reset = true }) { Text("Reload server settings") }
                }
            }
            item { ReleaseControls(draft, errors, edit) }
            item { AnnouncementControls(draft, edit) }
            item { AccessControls(draft, errors, edit) }
            item { EmergencyControls(draft, edit) }
            if (changed) {
                item {
                    OutlinedButton(onClick = { reset = true }, modifier = Modifier.fillMaxWidth(), enabled = !saving) {
                        Text("Discard draft")
                    }
                }
            }
            item {
                Text(
                    "Server updated ${displayTime(config.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (review) {
        AlertDialog(
            onDismissRequest = { review = false },
            title = { Text("Review changes") },
            text = { Text(draft.review(baseline), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                Button(onClick = {
                    review = false
                    save(draft.patch(baseline))
                }, enabled = !saving && !conflict) {
                    Text("Save changes")
                }
            },
            dismissButton = { TextButton(onClick = { review = false }) { Text("Keep editing") } },
        )
    }
    if (reset) {
        ConfirmDialog("Discard this draft and use the latest server settings?", { reset = false }, {
            baselineJson = Json.encodeToString(config)
            draftJson = Json.encodeToString(ConfigDraft.from(config))
            reset = false
        })
    }
}

@Composable
private fun ConfigCard(
    title: String,
    description: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun ConfigValueField(
    label: String,
    help: String,
    value: String,
    change: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    error: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        BasicTextField(
            value = value,
            onValueChange = change,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 2,
            maxLines = if (singleLine) 1 else 3,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        if (error) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(10.dp),
                    ).padding(horizontal = 12.dp, vertical = 11.dp),
            decorationBox = { innerField ->
                Box {
                    if (value.isBlank() && placeholder.isNotBlank()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerField()
                }
            },
        )
        if (error) Text("Check this value", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ReleaseControls(
    draft: ConfigDraft,
    errors: Set<String>,
    edit: (ConfigDraft) -> Unit,
) {
    ConfigCard("Releases", "Choose the supported Axis builds and download location.") {
        ConfigValueField(
            "Minimum build code",
            "Builds below this code must update.",
            draft.minimum,
            { edit(draft.copy(minimum = it)) },
            keyboardType = KeyboardType.Number,
            error = "minimum" in errors,
        )
        ConfigValueField(
            "Latest build code",
            "Must be at least the minimum build code.",
            draft.latestCode,
            { edit(draft.copy(latestCode = it)) },
            keyboardType = KeyboardType.Number,
            error = "latestCode" in errors,
        )
        ConfigValueField(
            "Version name",
            "The version users see in Axis.",
            draft.latestName,
            { edit(draft.copy(latestName = it)) },
        )
        ConfigValueField(
            "Download link",
            "Use an HTTPS link, or leave blank.",
            draft.updateUrl,
            { edit(draft.copy(updateUrl = it)) },
            keyboardType = KeyboardType.Uri,
            error = "updateUrl" in errors,
        )
    }
}

@Composable
private fun AnnouncementControls(
    draft: ConfigDraft,
    edit: (ConfigDraft) -> Unit,
) {
    ConfigCard("Announcements", "A notice for everyone using Axis.") {
        ConfigValueField(
            "Notice",
            "Leave blank to remove the notice.",
            draft.notice,
            { edit(draft.copy(notice = it)) },
            singleLine = false,
        )
    }
}

@Composable
private fun AccessControls(
    draft: ConfigDraft,
    errors: Set<String>,
    edit: (ConfigDraft) -> Unit,
) {
    ConfigCard("Access rules", "Matching new accounts skip the review queue.") {
        ConfigValueField(
            "Auto-approve prefixes",
            "Comma-separated admission number prefixes. Blank disables this rule.",
            draft.prefixes,
            { edit(draft.copy(prefixes = it)) },
            error = "prefixes" in errors,
        )
        ConfigValueField(
            "OTP compatibility version",
            "Upstream sign-in version; separate from the Axis release version.",
            draft.appVersion,
            { edit(draft.copy(appVersion = it)) },
            error = "appVersion" in errors,
        )
    }
}

@Composable
private fun EmergencyControls(
    draft: ConfigDraft,
    edit: (ConfigDraft) -> Unit,
) {
    ConfigCard("Emergency access", "Control availability for all student app users.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Block student access", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (draft.killSwitch) "All students will be blocked after saving." else "Student access is enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(draft.killSwitch, { edit(draft.copy(killSwitch = it)) })
        }
        ConfigValueField(
            "Message shown while blocked",
            "Explain why access is unavailable.",
            draft.message,
            { edit(draft.copy(message = it)) },
            singleLine = false,
        )
    }
}

private fun canSaveDraft(
    changed: Boolean,
    errors: Set<String>,
    saving: Boolean,
    conflict: Boolean,
): Boolean = changed && errors.isEmpty() && !saving && !conflict
