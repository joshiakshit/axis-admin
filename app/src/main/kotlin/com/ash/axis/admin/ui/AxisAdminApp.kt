package com.ash.axis.admin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

private val AdminPage.icon: ImageVector
    get() =
        when (this) {
            AdminPage.DASHBOARD -> Icons.Default.Dashboard
            AdminPage.USERS -> Icons.Default.People
            AdminPage.CONFIG -> Icons.Default.Tune
            AdminPage.AUDIT -> Icons.Default.History
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun AxisAdminApp(viewModel: AdminViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val pages = rememberSaveableStateHolder()
    var forget by remember { mutableStateOf(false) }
    var device by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<AdminPage?>(null) }
    LaunchedEffect(state.message, state.error, state.signedIn) {
        if (!state.signedIn) return@LaunchedEffect
        (state.error ?: state.message)?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    BackHandler(enabled = state.signedIn && state.page != AdminPage.DASHBOARD) {
        if (state.page == AdminPage.CONFIG && dirty) {
            destination = AdminPage.DASHBOARD
        } else {
            viewModel.select(AdminPage.DASHBOARD)
        }
    }
    AdminTheme {
        if (!state.signedIn) {
            Surface(Modifier.fillMaxSize(), color = Color.Black) {
                AccessScreen(state, viewModel::provision, viewModel::connect) { forgetDevice(pages, viewModel) }
            }
        } else {
            Scaffold(
                containerColor = Color.Black,
                topBar = { AdminTopBar(state, { viewModel.refresh() }, { device = true }, { forget = true }) },
                bottomBar = {
                    AdminNavigationBar(state) { page ->
                        if (state.page == AdminPage.CONFIG && dirty && page != state.page) {
                            destination = page
                        } else if (page != state.page) {
                            viewModel.select(page)
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { insets ->
                AdminPageContent(state, viewModel, pages, { dirty = it }, Modifier.padding(insets))
            }
        }
        if (forget) {
            ConfirmDialog(
                "Remove the saved app token and session from this device? You will need the current token to connect again.",
                { forget = false },
                {
                    forget = false
                    dirty = false
                    forgetDevice(pages, viewModel)
                },
            )
        }
        DeviceAccessDialog(device) { device = false }
        destination?.let { page ->
            ConfirmDialog("Discard your unsaved changes and leave Controls?", { destination = null }, {
                pages.removeState(AdminPage.CONFIG.name)
                dirty = false
                destination = null
                viewModel.select(page)
            })
        }
    }
}

@Composable
private fun AccessScreen(
    state: AdminUiState,
    provision: (String) -> Unit,
    connect: () -> Unit,
    reset: () -> Unit,
) {
    var token by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()
            .verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        AxisMark()
        Spacer(Modifier.height(24.dp))
        Text("Axis Admin", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.provisioned) "Reconnect with this device's saved credential." else "Connect your device to manage Axis.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (!state.provisioned) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("App token") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.connecting,
            )
        }
        state.error?.let { Text(it, modifier = Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (state.provisioned) connect() else provision(token) },
            enabled = !state.connecting && (state.provisioned || token.isNotBlank()),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        ) {
            Text(
                if (state.connecting) {
                    "Connecting…"
                } else if (state.provisioned) {
                    "Reconnect"
                } else {
                    "Connect device"
                },
            )
        }
        if (state.provisioned) TextButton(onClick = reset, enabled = !state.connecting) { Text("Use another app token") }
        Spacer(Modifier.height(16.dp))
        Text(
            "Version ${com.ash.axis.admin.BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ErrorNotice(
    message: String,
    retry: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = retry) { Text("Retry") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTopBar(
    state: AdminUiState,
    refresh: () -> Unit,
    openDevice: () -> Unit,
    forgetDevice: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AxisMark()
                Column {
                    Text(
                        "AXIS ADMIN",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(state.page.title, style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
        actions = {
            IconButton(onClick = { refresh() }, enabled = !state.loading) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh ${state.page.title}")
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Device options")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Device access") }, onClick = {
                        menu = false
                        openDevice()
                    })
                    DropdownMenuItem(text = { Text("Forget this device") }, onClick = {
                        menu = false
                        forgetDevice()
                    })
                }
            }
        },
    )
}

@Composable
private fun AdminNavigationBar(
    state: AdminUiState,
    select: (AdminPage) -> Unit,
) {
    NavigationBar(containerColor = Color.Black, tonalElevation = 0.dp) {
        AdminPage.entries.forEach { page ->
            NavigationBarItem(
                selected = state.page == page,
                onClick = { select(page) },
                icon = {
                    BadgedBox(badge = {
                        if (page == AdminPage.USERS && state.pendingCount > 0) Badge { Text("${state.pendingCount}") }
                    }) { Icon(page.icon, contentDescription = null) }
                },
                label = { Text(page.title) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminPageContent(
    state: AdminUiState,
    viewModel: AdminViewModel,
    pages: androidx.compose.runtime.saveable.SaveableStateHolder,
    dirtyChanged: (Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        if (state.loading || state.savingConfig) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.pageErrors[state.page]?.let { message ->
            ErrorNotice(message) { viewModel.refresh() }
        }
        PullToRefreshBox(
            isRefreshing = state.page in state.loadingPages,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            pages.SaveableStateProvider(state.page.name) {
                when (state.page) {
                    AdminPage.DASHBOARD -> DashboardScreen(state) { viewModel.select(AdminPage.USERS) }
                    AdminPage.USERS -> UsersScreen(state, viewModel)
                    AdminPage.CONFIG -> ConfigScreen(state.config, state.savingConfig, dirtyChanged, viewModel::saveConfig)
                    AdminPage.AUDIT -> AuditLogScreen(state, viewModel::olderAudit)
                }
            }
        }
    }
}

@Composable
private fun DeviceAccessDialog(
    visible: Boolean,
    dismiss: () -> Unit,
) {
    if (visible) {
        AlertDialog(
            onDismissRequest = { dismiss() },
            title = { Text("Device access") },
            text = {
                Text(
                    "Axis Admin ${com.ash.axis.admin.BuildConfig.VERSION_NAME}\n" +
                        "Connected to your Axis backend. The app token is stored encrypted on this device.",
                )
            },
            confirmButton = { TextButton(onClick = { dismiss() }) { Text("Done") } },
        )
    }
}

private fun forgetDevice(
    pages: androidx.compose.runtime.saveable.SaveableStateHolder,
    viewModel: AdminViewModel,
) {
    AdminPage.entries.forEach { pages.removeState(it.name) }
    viewModel.forgetDevice()
}
