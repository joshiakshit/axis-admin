package com.ash.axis.admin.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ash.axis.admin.R
import com.ash.axis.admin.data.AdminUser
import com.ash.axis.admin.data.AuditEntry
import com.ash.axis.admin.data.ConfigPatch
import com.ash.axis.admin.data.DangerousAction
import com.ash.axis.admin.data.RemoteConfig
import com.ash.axis.admin.data.UserAction
import com.ash.axis.admin.data.confirmationText

private val ScreenPadding = 18.dp
private val ItemSpacing = 12.dp
private val CardPadding = 16.dp
private val BottomNavClearance = 112.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun AxisAdminApp(viewModel: AdminViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it) } }
    LaunchedEffect(state.page) { snackbar.currentSnackbarData?.dismiss() }

    AdminTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            if (!state.provisioned) {
                ProvisionScreen(state, viewModel::provision)
            } else if (!state.signedIn) {
                SetupScreen(state, viewModel::connect)
            } else {
                Box(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        AdminHeader()
                        if (state.loading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        Crossfade(targetState = state.page, label = "page") { page ->
                            PullToRefreshBox(
                                isRefreshing = state.loading,
                                onRefresh = { viewModel.refresh() },
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                when (page) {
                                    AdminPage.DASHBOARD -> DashboardScreen(state)
                                    AdminPage.USERS -> UsersScreen(state.users, viewModel)
                                    AdminPage.CONFIG -> ConfigScreen(state.config, viewModel::saveConfig)
                                    AdminPage.AUDIT -> AuditLogScreen(state.auditLog)
                                }
                            }
                        }
                    }

                    FloatingNavBar(
                        page = state.page,
                        pendingCount = state.users.count { it.status == "pending" },
                        onSelect = viewModel::select,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )

                    SnackbarHost(
                        snackbar,
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = BottomNavClearance),
                    )
                }
            }
        }
    }
}

@Composable
private fun AdminHeader() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = ScreenPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_axis_logo),
            contentDescription = "Axis",
            modifier = Modifier.size(width = 24.dp, height = 18.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FloatingNavBar(
    page: AdminPage,
    pendingCount: Int,
    onSelect: (AdminPage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgColor = MaterialTheme.colorScheme.background
    val barColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)
    val borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                bgColor.copy(alpha = 0.7f),
                                bgColor.copy(alpha = 0.95f),
                            ),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = barColor,
            border = BorderStroke(0.5.dp, borderColor),
            shadowElevation = 12.dp,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier =
                    Modifier
                        .height(58.dp)
                        .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                AdminPage.entries.forEach { item ->
                    NavBarItem(
                        label = item.name.lowercase().replaceFirstChar { it.uppercase() },
                        icon = item.icon,
                        selected = item == page,
                        badge = if (item == AdminPage.USERS && pendingCount > 0) pendingCount else null,
                        onClick = { onSelect(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int?,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.06f else 1f,
        animationSpec =
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "nav_scale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.5f,
        label = "nav_alpha",
    )
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        modifier = Modifier.width(72.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
            ) {
                if (badge != null) {
                    BadgedBox(badge = { Badge { Text(badge.toString()) } }) {
                        Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = tint)
                    }
                } else {
                    Icon(icon, contentDescription = label, modifier = Modifier.size(20.dp), tint = tint)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 9.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) tint else tint.copy(alpha = 0.6f),
            )
        }
    }
}

private val AdminPage.icon: ImageVector
    get() =
        when (this) {
            AdminPage.DASHBOARD -> Icons.Default.Dashboard
            AdminPage.USERS -> Icons.Default.People
            AdminPage.CONFIG -> Icons.Default.Tune
            AdminPage.AUDIT -> Icons.Default.History
        }

// ---- Screens ----

@Composable
private fun ProvisionScreen(
    state: AdminUiState,
    provision: (String) -> Unit,
) {
    var token by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().padding(ScreenPadding), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
            Text("Axis Admin", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Enter the admin app token to provision this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                token,
                { token = it },
                label = { Text("Admin app token") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(
                onClick = { provision(token) },
                enabled = token.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Provision") }
        }
    }
}

@Composable
private fun SetupScreen(
    state: AdminUiState,
    connect: () -> Unit,
) {
    Box(Modifier.fillMaxSize().padding(ScreenPadding), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
            Text("Axis Admin", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Connecting with this device's admin credential.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.loading) CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (!state.loading) {
                Button(
                    onClick = connect,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Retry") }
            }
        }
    }
}

private val listPadding = PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = BottomNavClearance)

@Composable
private fun DashboardScreen(state: AdminUiState) {
    val health = state.health ?: return
    val stats = state.stats
    LazyColumn(
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeader("Overview") }
        item { ServiceCard(health.service, health.apkUploaded) }
        item {
            StatRow(
                left = StatValue("USERS", health.users.toString(), "Total accounts"),
                right = StatValue("PENDING", health.pending.toString(), "Need review", Color(0xFFF59E0B)),
            )
        }
        item {
            StatRow(
                left = StatValue("APPROVED", health.approved.toString(), "Can sign in", Color(0xFF34C759)),
                right = StatValue("BANNED", health.banned.toString(), "Access blocked", Color(0xFFEF4444)),
            )
        }
        if (stats != null) {
            item { SectionHeader("Activity") }
            item {
                ActivityRow(
                    listOf(
                        StatValue("24 HOURS", stats.activeLastDay.toString(), "Active"),
                        StatValue("7 DAYS", stats.activeLastWeek.toString(), "Active"),
                        StatValue("30 DAYS", stats.activeLastMonth.toString(), "Active"),
                    ),
                )
            }
            if (stats.versionDistribution.isNotEmpty()) {
                item { SectionHeader("Version distribution") }
                item { MetricList(stats.versionDistribution.map { it.version to it.count.toString() }) }
            }
            if (stats.deviceDistribution.isNotEmpty()) {
                item { SectionHeader("Top devices") }
                item { MetricList(stats.deviceDistribution.map { it.device to it.count.toString() }) }
            }
            if (stats.sdkDistribution.isNotEmpty()) {
                item { SectionHeader("Android SDK levels") }
                item { MetricList(stats.sdkDistribution.map { "API ${it.sdk}" to it.count.toString() }) }
            }
        }
        if (health.metrics.isNotEmpty()) {
            item { SectionHeader("Aggregate metrics") }
            item { MetricList(health.metrics.map { (label, value) -> label to value.toString() }) }
        }
    }
}

private data class StatValue(
    val label: String,
    val value: String,
    val detail: String,
    val tone: Color? = null,
)

@Composable
private fun ServiceCard(
    service: String,
    apkUploaded: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(Color(0xFF34C759), RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("SERVICE ONLINE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(service, style = MaterialTheme.typography.titleMedium)
            }
            StatusBadge(if (apkUploaded) "APK ready" else "No APK")
        }
    }
}

@Composable
private fun StatRow(
    left: StatValue,
    right: StatValue,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        CompactStatCard(left, Modifier.weight(1f))
        CompactStatCard(right, Modifier.weight(1f))
    }
}

@Composable
private fun ActivityRow(stats: List<StatValue>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        stats.forEach { CompactStatCard(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun CompactStatCard(
    stat: StatValue,
    modifier: Modifier = Modifier,
) {
    val tone = stat.tone ?: MaterialTheme.colorScheme.primary
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(stat.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tone, letterSpacing = 0.8.sp)
            Text(
                stat.value,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(stat.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(10.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MetricList(metrics: List<Pair<String, String>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = CardPadding, vertical = 5.dp)) {
            metrics.forEachIndexed { index, (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (index < metrics.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
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
    Column(Modifier.padding(horizontal = ScreenPadding)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Users", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { pendingAction = DangerousAction.ApproveAll to viewModel::approveAll },
                shape = RoundedCornerShape(12.dp),
            ) { Text("Approve all") }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            search,
            { search = it },
            label = { Text("Search") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all", "pending", "approved", "banned").forEach { status ->
                FilterChip(selected = filter == status, onClick = { filter = status }, label = { Text(status) })
            }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = BottomNavClearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
private fun StatusBadge(status: String) {
    val color by animateColorAsState(
        targetValue =
            when (status) {
                "approved", "allow", "APK ready" -> Color(0xFF34C759)
                "pending", "approve-all" -> Color(0xFFF59E0B)
                "banned", "ban", "No APK" -> Color(0xFFEF4444)
                "kick" -> Color(0xFFED6C02)
                "config-update" -> Color(0xFF6E90C0)
                else -> MaterialTheme.colorScheme.outline
            },
        label = "statusColor",
    )
    Surface(
        color = color.copy(alpha = 0.14f),
        contentColor = color,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            status,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun UserCard(
    user: AdminUser,
    act: (UserAction) -> Unit,
) {
    val highlight = user.status == "pending"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (highlight) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.name.ifBlank { user.admno },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(user.status)
            }
            Text(
                "${user.admno} · ${user.role}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (user.appVersionName.isNotBlank() || user.deviceModel.isNotBlank()) {
                Text(
                    "${user.appVersionName} (${user.appVersionCode}) · ${user.deviceModel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Sessions: ${user.sessionCount} · Last seen: ${user.lastSeenAt.take(10)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    val minimumCode = minimum.toIntOrNull()
    val newestCode = latestCode.toIntOrNull()
    val validCodes = minimumCode != null && newestCode != null
    val patch =
        ConfigPatch(
            notice = notice.takeIf { it != config.notice },
            minSupportedVersionCode = minimumCode?.takeIf { it != config.minSupportedVersionCode },
            latestVersionCode = newestCode?.takeIf { it != config.latestVersionCode },
            latestVersionName = latestName.takeIf { it != config.latestVersionName },
            updateUrl = updateUrl.takeIf { it != config.updateUrl },
            autoApprovePrefix = prefixes.takeIf { it != config.autoApprovePrefix },
            killSwitch = killSwitch.takeIf { it != config.killSwitch },
            message = killMessage.takeIf { it != config.message },
        )
    val hasChanges = patch != ConfigPatch()
    LazyColumn(
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Config", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Changes apply to every Axis user. Review them before saving.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            ConfigCard(
                title = "App release",
                description = "Control supported builds and where users download updates.",
            ) {
                ConfigValueField(
                    label = "Minimum build code",
                    help = "Older builds will be blocked and asked to update.",
                    value = minimum,
                    change = { minimum = it },
                    keyboardType = KeyboardType.Number,
                    error = minimumCode == null,
                )
                ConfigDivider()
                ConfigValueField(
                    label = "Latest build code",
                    help = "The build number currently available to users.",
                    value = latestCode,
                    change = { latestCode = it },
                    keyboardType = KeyboardType.Number,
                    error = newestCode == null,
                )
                ConfigDivider()
                ConfigValueField(
                    label = "Version name",
                    help = "The readable version shown in update messages.",
                    value = latestName,
                    change = { latestName = it },
                )
                ConfigDivider()
                ConfigValueField(
                    label = "Download link",
                    help = "Axis opens this link when an update is required.",
                    value = updateUrl,
                    change = { updateUrl = it },
                    keyboardType = KeyboardType.Uri,
                    singleLine = false,
                )
            }
        }
        item {
            ConfigCard(
                title = "Student communication",
                description = "Set a notice or approve known admission-number groups automatically.",
            ) {
                ConfigValueField(
                    label = "Notice",
                    help = "Shown inside Axis. Leave blank when there is no announcement.",
                    value = notice,
                    change = { notice = it },
                    placeholder = "No active notice",
                    singleLine = false,
                )
                ConfigDivider()
                ConfigValueField(
                    label = "Auto-approve prefixes",
                    help = "Matching admission numbers skip the pending approval queue.",
                    value = prefixes,
                    change = { prefixes = it },
                    placeholder = "No automatic approvals",
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = if (killSwitch) Color(0xFF251214) else MaterialTheme.colorScheme.surface,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Emergency block", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Temporarily stop all students from opening Axis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(killSwitch, { killSwitch = it })
                    }
                    if (killSwitch) {
                        ConfigValueField(
                            label = "Message shown to students",
                            help = "Explain why access is blocked and when it may return.",
                            value = killMessage,
                            change = { killMessage = it },
                            placeholder = "Axis is temporarily unavailable",
                            singleLine = false,
                        )
                    } else {
                        Text(
                            "Axis is available. Students can sign in normally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF34C759),
                        )
                    }
                }
            }
        }
        item {
            Button(
                onClick = {
                    val nextMinimum = patch.minSupportedVersionCode
                    val dangerous =
                        when {
                            nextMinimum != null && nextMinimum > config.minSupportedVersionCode ->
                                DangerousAction.RaiseMinimum(config.minSupportedVersionCode, nextMinimum)
                            patch.killSwitch != null -> DangerousAction.SetKillSwitch(patch.killSwitch)
                            else -> null
                        }
                    if (dangerous == null) save(patch) else confirmation = confirmationText(dangerous) to { save(patch) }
                },
                enabled = hasChanges && validCodes,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (hasChanges) "Save changes" else "No changes") }
        }
        item {
            Text(
                "Last updated ${config.updatedAt.take(19).replace("T", " ")}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    confirmation?.let { (message, run) ->
        ConfirmDialog(message, { confirmation = null }, {
            confirmation = null
            run()
        })
    }
}

@Composable
private fun AuditLogScreen(entries: List<AuditEntry>) {
    LazyColumn(
        contentPadding = listPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeader("Audit log") }
        if (entries.isEmpty()) {
            item {
                Text(
                    "No entries yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(entries, key = { it.id }) { entry ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                Column(Modifier.padding(CardPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusBadge(entry.action)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            entry.createdAt.take(19).replace("T", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "By ${entry.actorAdmno}" +
                            if (entry.targetAdmno.isNotBlank()) " on ${entry.targetAdmno}" else "",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (entry.detail.isNotBlank()) {
                        Text(
                            entry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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
        if (error) Text("Enter a valid number", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun ConfigDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
        confirmButton = { Button(onClick = confirm, shape = RoundedCornerShape(12.dp)) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}
