package com.ash.axis.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ash.axis.admin.data.AdminUser
import com.ash.axis.admin.data.ConfigPatch
import com.ash.axis.admin.data.DangerousAction
import com.ash.axis.admin.data.LoginMethod
import com.ash.axis.admin.data.RemoteConfig
import com.ash.axis.admin.data.UserAction
import com.ash.axis.admin.data.confirmationText

@Composable
fun AxisAdminApp(viewModel: AdminViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    MaterialTheme {
        if (!state.signedIn) {
            LoginScreen(state, viewModel)
        } else {
            Scaffold(
                bottomBar = { AdminNavigation(state.page, viewModel::select) },
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Axis Admin", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = viewModel::logout) { Text("Logout") }
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    when (state.page) {
                        AdminPage.DASHBOARD -> DashboardScreen(state)
                        AdminPage.USERS -> UsersScreen(state.users, viewModel)
                        AdminPage.CONFIG -> ConfigScreen(state.config, viewModel::saveConfig)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginScreen(
    state: AdminUiState,
    viewModel: AdminViewModel,
) {
    var contact by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var useEmail by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Axis Admin", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Sign in with an authorized iCloudEMS identity.")
            OutlinedTextField(contact, { contact = it }, label = { Text(if (useEmail) "Email" else "Phone") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(useEmail, { useEmail = it })
                Text("Use email")
            }
            if (state.otpRequested) {
                OutlinedTextField(
                    otp,
                    { otp = it },
                    label = { Text("OTP") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                Button(onClick = { viewModel.completeLogin(contact, otp) }, enabled = otp.isNotBlank() && !state.loading) {
                    Text("Verify and sign in")
                }
            } else {
                Button(
                    onClick = { viewModel.requestOtp(contact, if (useEmail) LoginMethod.EMAIL else LoginMethod.PHONE) },
                    enabled = contact.isNotBlank() && !state.loading,
                ) { Text("Request OTP") }
            }
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun AdminNavigation(
    page: AdminPage,
    select: (AdminPage) -> Unit,
) {
    NavigationBar {
        AdminPage.entries.forEach { item ->
            NavigationBarItem(
                selected = item == page,
                onClick = { select(item) },
                icon = { Text(item.name.take(1)) },
                label = { Text(item.name.lowercase().replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

@Composable
private fun DashboardScreen(state: AdminUiState) {
    val health = state.health ?: return
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Dashboard", style = MaterialTheme.typography.titleLarge) }
        items(
            listOf(
                "Service" to health.service,
                "Users" to health.users.toString(),
                "Pending" to health.pending.toString(),
                "Approved" to health.approved.toString(),
                "Banned" to health.banned.toString(),
                "APK uploaded" to if (health.apkUploaded) "Yes" else "No",
            ),
        ) { (label, value) -> MetricCard(label, value) }
        if (health.metrics.isNotEmpty()) {
            item { Text("Aggregate metrics", style = MaterialTheme.typography.titleMedium) }
            items(health.metrics.toList()) { (label, value) -> MetricCard(label, value.toString()) }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun UsersScreen(
    users: List<AdminUser>,
    viewModel: AdminViewModel,
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var pendingAction by remember { mutableStateOf<Pair<DangerousAction, () -> Unit>?>(null) }
    val shown =
        users.filter { user ->
            (filter == "all" || user.status == filter) &&
                (search.isBlank() || user.admno.contains(search, true) || user.name.contains(search, true))
        }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Users", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = {
                pendingAction = DangerousAction.ApproveAll to viewModel::approveAll
            }) { Text("Approve all") }
        }
        OutlinedTextField(search, { search = it }, label = { Text("Search") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all", "pending", "approved", "banned").forEach { status ->
                FilterChip(selected = filter == status, onClick = { filter = status }, label = { Text(status) })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown, key = { it.admno }) { user ->
                UserCard(user) { action ->
                    val run = { viewModel.setUserStatus(user.admno, action) }
                    pendingAction =
                        when (action) {
                            UserAction.KICK -> DangerousAction.Kick(user.admno) to run
                            UserAction.BAN -> DangerousAction.Ban(user.admno) to run
                            UserAction.ALLOW -> null
                        }
                    if (action == UserAction.ALLOW) run()
                }
            }
        }
    }
    pendingAction?.let { (action, run) ->
        ConfirmDialog(confirmationText(action), { pendingAction = null }, {
            pendingAction = null
            run()
        })
    }
}

@Composable
private fun UserCard(
    user: AdminUser,
    act: (UserAction) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(user.name.ifBlank { user.admno }, fontWeight = FontWeight.Bold)
            Text("${user.admno} · ${user.status} · ${user.role}")
            Text("${user.appVersionName} (${user.appVersionCode}) · ${user.deviceModel}")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (user.status != "approved") TextButton(onClick = { act(UserAction.ALLOW) }) { Text("Approve") }
                if (user.role != "admin" && user.status == "approved") {
                    TextButton(onClick = { act(UserAction.KICK) }) { Text("Kick") }
                }
                if (user.role != "admin" && user.status != "banned") {
                    TextButton(onClick = { act(UserAction.BAN) }) { Text("Ban") }
                }
            }
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun ConfigScreen(
    config: RemoteConfig?,
    save: (ConfigPatch) -> Unit,
) {
    if (config == null) return
    var notice by remember { mutableStateOf(config.notice) }
    var minimum by remember { mutableStateOf(config.minSupportedVersionCode.toString()) }
    var latestCode by remember { mutableStateOf(config.latestVersionCode.toString()) }
    var latestName by remember { mutableStateOf(config.latestVersionName) }
    var updateUrl by remember { mutableStateOf(config.updateUrl) }
    var prefixes by remember { mutableStateOf(config.autoApprovePrefix) }
    var killSwitch by remember { mutableStateOf(config.killSwitch) }
    var killMessage by remember { mutableStateOf(config.message) }
    var confirmation by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LaunchedEffect(config.updatedAt) {
        notice = config.notice
        minimum = config.minSupportedVersionCode.toString()
        latestCode = config.latestVersionCode.toString()
        latestName = config.latestVersionName
        updateUrl = config.updateUrl
        prefixes = config.autoApprovePrefix
        killSwitch = config.killSwitch
        killMessage = config.message
    }
    val buildPatch = {
        ConfigPatch(
            notice = notice.takeIf { it != config.notice },
            minSupportedVersionCode = minimum.toIntOrNull()?.takeIf { it != config.minSupportedVersionCode },
            latestVersionCode = latestCode.toIntOrNull()?.takeIf { it != config.latestVersionCode },
            latestVersionName = latestName.takeIf { it != config.latestVersionName },
            updateUrl = updateUrl.takeIf { it != config.updateUrl },
            autoApprovePrefix = prefixes.takeIf { it != config.autoApprovePrefix },
            killSwitch = killSwitch.takeIf { it != config.killSwitch },
            message = killMessage.takeIf { it != config.message },
        )
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Remote config", style = MaterialTheme.typography.titleLarge) }
        item { ConfigField("Notice", notice) { notice = it } }
        item { ConfigField("Minimum version code", minimum) { minimum = it } }
        item { ConfigField("Latest version code", latestCode) { latestCode = it } }
        item { ConfigField("Latest version name", latestName) { latestName = it } }
        item { ConfigField("Update URL", updateUrl) { updateUrl = it } }
        item { ConfigField("Auto-approve prefixes", prefixes) { prefixes = it } }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kill switch", modifier = Modifier.weight(1f))
                Switch(killSwitch, { killSwitch = it })
            }
        }
        item { ConfigField("Kill-switch message", killMessage) { killMessage = it } }
        item {
            Button(onClick = {
                val patch = buildPatch()
                val nextMinimum = patch.minSupportedVersionCode
                val dangerous =
                    when {
                        nextMinimum != null && nextMinimum > config.minSupportedVersionCode ->
                            DangerousAction.RaiseMinimum(config.minSupportedVersionCode, nextMinimum)
                        patch.killSwitch != null -> DangerousAction.SetKillSwitch(patch.killSwitch)
                        else -> null
                    }
                if (dangerous == null) save(patch) else confirmation = confirmationText(dangerous) to { save(patch) }
            }) { Text("Save changed fields") }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    confirmation?.let { (message, run) ->
        ConfirmDialog(message, { confirmation = null }, {
            confirmation = null
            run()
        })
    }
}

@Composable
private fun ConfigField(
    label: String,
    value: String,
    change: (String) -> Unit,
) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ConfirmDialog(
    message: String,
    dismiss: () -> Unit,
    confirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Confirm action") },
        text = { Text(message) },
        confirmButton = { Button(onClick = confirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
