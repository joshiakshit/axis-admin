package com.ash.axis.admin.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ash.axis.admin.data.AdminUser
import com.ash.axis.admin.data.DangerousAction
import com.ash.axis.admin.data.UserAction
import com.ash.axis.admin.data.confirmationText

@Composable
@Suppress("LongMethod")
internal fun UsersScreen(
    state: AdminUiState,
    viewModel: AdminViewModel,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("pending") }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var detail by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val shown = matchingUsers(state.users, search, filter)
    val selectedPending = state.users.filter { it.admno in selected && it.status == "pending" }.map { it.admno }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Access requests", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${state.pendingCount} waiting · oldest first",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            OutlinedTextField(
                search,
                { search = it },
                label = { Text("Search users") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("pending", "approved", "banned", "all").forEach { status ->
                    val count = if (status == "all") state.users.size else state.users.count { it.status == status }
                    FilterChip(
                        selected = filter == status,
                        onClick = { filter = status },
                        label = { Text("${status.replaceFirstChar { it.uppercase() }} $count") },
                    )
                }
            }
            if (selectedPending.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = emptyList() }) { Text("Clear") }
                    Button(onClick = {
                        val ids = selectedPending.toList()
                        confirm = "Approve ${ids.size} selected requests?\n\n${ids.joinToString("\n")}" to {
                            viewModel.approveSelected(ids)
                            selected = emptyList()
                        }
                    }, enabled = selectedPending.none { it in state.busyUsers }, modifier = Modifier.weight(1f)) {
                        Text("Approve selected (${selectedPending.size})")
                    }
                }
            }
        }
        LazyColumn(
            state = rememberLazyListState(),
            contentPadding = listPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f),
        ) {
            if (shown.isEmpty() && state.usersLoaded) {
                item {
                    EmptyUsers(search, filter)
                }
            }
            items(shown, key = { it.admno }) { user ->
                UserRow(
                    user,
                    user.admno in state.busyUsers,
                    user.admno in selectedPending,
                    state.userErrors[user.admno],
                    toggle = { selected = toggleSelection(selected, user.admno) },
                    open = { detail = user.admno },
                    approve = { viewModel.setUserStatus(user.admno, UserAction.ALLOW) },
                )
            }
        }
    }
    state.users.firstOrNull { it.admno == detail }?.let { user ->
        UserDetails(user, user.admno in state.busyUsers, dismiss = { detail = null }) { action ->
            val run = {
                viewModel.setUserStatus(user.admno, action)
                detail = null
            }
            if (action == UserAction.ALLOW && user.status == "pending") {
                run()
            } else {
                confirm = when (action) {
                    UserAction.BAN -> confirmationText(DangerousAction.Ban(user.admno))
                    UserAction.KICK -> confirmationText(DangerousAction.Kick(user.admno))
                    UserAction.ALLOW -> "Restore access for ${user.name.ifBlank { user.admno }} (${user.admno})?"
                } to run
            }
        }
    }
    confirm?.let { (text, run) ->
        ConfirmDialog(text, { confirm = null }, {
            confirm = null
            run()
        })
    }
}

@Composable
private fun UserRow(
    user: AdminUser,
    busy: Boolean,
    selected: Boolean,
    error: String?,
    toggle: () -> Unit,
    open: () -> Unit,
    approve: () -> Unit,
) {
    Card(
        onClick = open,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (user.status == "pending") Checkbox(checked = selected, onCheckedChange = { toggle() }, enabled = !busy)
                Column(Modifier.weight(1f)) {
                    Text(user.name.ifBlank { user.admno }, style = MaterialTheme.typography.titleMedium)
                    Text(user.admno, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                StatusBadge(user.status)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (user.status == "pending") {
                        "Requested ${displayTime(user.createdAt)}"
                    } else {
                        "Seen ${displayTime(user.lastSeenAt)}"
                    },
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (user.status == "pending") {
                    Button(onClick = approve, enabled = !busy) {
                        Text(if (busy) "Approving…" else "Approve")
                    }
                } else {
                    TextButton(onClick = open) { Text("Details") }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun UserDetails(
    user: AdminUser,
    busy: Boolean,
    dismiss: () -> Unit,
    action: (UserAction) -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(user.name.ifBlank { user.admno }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatusBadge(user.status)
                DetailLine("Admission number", user.admno)
                DetailLine("Email", user.email)
                DetailLine("Role", user.role)
                DetailLine("Requested", displayTime(user.createdAt))
                DetailLine("Last seen", displayTime(user.lastSeenAt))
                DetailLine("First approved", displayTime(user.approvedAt))
                DetailLine("App version", "${user.appVersionName} (${user.appVersionCode})")
                DetailLine("Device", user.deviceModel)
                DetailLine("Android API", user.androidSdk.toString())
                DetailLine("Sessions", user.sessionCount.toString())
                if (user.role != "admin") {
                    if (user.status == "approved") {
                        OutlinedButton(onClick = { action(UserAction.KICK) }, enabled = !busy) {
                            Text("Return to pending")
                        }
                    }
                    if (user.status != "banned") {
                        TextButton(onClick = { action(UserAction.BAN) }, enabled = !busy) {
                            Text("Ban user", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (user.status != "approved") {
                Button(onClick = { action(UserAction.ALLOW) }, enabled = !busy) {
                    Text(if (user.status == "banned") "Restore access" else "Approve")
                }
            } else {
                TextButton(onClick = dismiss) { Text("Done") }
            }
        },
        dismissButton = { if (user.status != "approved") TextButton(onClick = dismiss) { Text("Close") } },
    )
}

@Composable
internal fun DetailLine(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value.ifBlank { "Not available" }, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun matchingUsers(
    users: List<AdminUser>,
    search: String,
    filter: String,
): List<AdminUser> =
    users.filter { user ->
        (filter == "all" || user.status == filter) &&
            (search.isBlank() || listOf(user.admno, user.name, user.email).any { it.contains(search, true) })
    }

@Composable
private fun EmptyUsers(
    search: String,
    filter: String,
) {
    Column(Modifier.padding(vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (search.isNotBlank()) {
                "No matching users"
            } else if (filter == "pending") {
                "You're all caught up"
            } else {
                "No $filter users"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            if (search.isNotBlank()) "Try another name or admission number." else "Pull down to check for changes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun toggleSelection(
    selected: List<String>,
    admno: String,
): List<String> = if (admno in selected) selected - admno else selected + admno
