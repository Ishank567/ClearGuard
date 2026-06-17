package com.clearguard.app.ui.screens
import com.clearguard.app.ui.components.*

import com.clearguard.app.ui.theme.*

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.GlassCardCompact
import com.clearguard.app.ui.components.PrimaryButton
import com.clearguard.app.ui.components.SecondaryButton
// Using MaterialTheme + Clear* compatibility shims for the fresh UI
import com.clearguard.app.vpn.ClearGuardVpnService
import kotlinx.coroutines.Dispatchers
import kotlin.math.cos
import kotlin.math.sin

// Screenshot Scanner imports
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.clearguard.app.security.OnDeviceRuleEngine
import com.clearguard.app.security.ScamScreenshotAnalyzer
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private data class PrivacyTabItem(val title: String, val icon: ImageVector)

private val privacyTabs = listOf(
    PrivacyTabItem("Live", Icons.Default.Radar),
    PrivacyTabItem("Stats", Icons.Default.BarChart),
    PrivacyTabItem("Apps", Icons.Default.Apps),
    PrivacyTabItem("Scan", Icons.Default.DocumentScanner)
)

@Composable
fun PrivacyScreen(
    blockedTotal: Long,
    allowedTotal: Long,
    blockedToday: Long,
    cacheHits: Long,
    upstreamQueries: Long,
    upstreamAverageLatencyMs: Float,
    scamBlocked: Long,
    scamShieldEnabled: Boolean,
    dohEnabled: Boolean,
    dohQueries: Long,
    initialScanText: String? = null
) {
    var selectedTab by remember { mutableStateOf(if (initialScanText != null) 3 else 0) }
    val animationsEnabled = rememberAnimationsEnabled()

    ShieldMeshBackground(
        modifier = Modifier.fillMaxSize(),
        active = true,
        animate = animationsEnabled
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Privacy Center",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Monitor DNS traffic, audit apps, and scan for scams — fully on-device",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            PrivacyTabBar(
                tabs = privacyTabs,
                selectedIndex = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            AnimatedContent(
                targetState = selectedTab,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                transitionSpec = {
                    (fadeIn(tween(220)) +
                        slideInHorizontally(tween(220)) { if (targetState > initialState) it / 5 else -it / 5 }) togetherWith
                        (fadeOut(tween(180)) +
                            slideOutHorizontally(tween(180)) { if (targetState > initialState) -it / 5 else it / 5 })
                },
                label = "privacyTabContent"
            ) { tab ->
                when (tab) {
                    0 -> LiveMonitorScreen(
                        cacheHits = cacheHits,
                        upstreamQueries = upstreamQueries,
                        upstreamAverageLatencyMs = upstreamAverageLatencyMs,
                        dohEnabled = dohEnabled,
                        dohQueries = dohQueries
                    )
                    1 -> AnalyticsTab(
                        blockedTotal = blockedTotal,
                        allowedTotal = allowedTotal,
                        blockedToday = blockedToday,
                        cacheHits = cacheHits,
                        upstreamQueries = upstreamQueries,
                        upstreamAverageLatencyMs = upstreamAverageLatencyMs,
                        scamBlocked = scamBlocked,
                        scamShieldEnabled = scamShieldEnabled,
                        dohEnabled = dohEnabled,
                        dohQueries = dohQueries
                    )
                    2 -> AppAuditTab()
                    3 -> ScamScreenshotScanner(initialText = initialScanText)
                }
            }
        }
    }
}

@Composable
private fun PrivacyTabBar(
    tabs: List<PrivacyTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = selectedIndex == index
            FilterChip(
                selected = selected,
                onClick = { onSelect(index) },
                label = {
                    Text(
                        tab.title,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                },
                leadingIcon = {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                )
            )
        }
    }
}

@Composable
private fun PrivacyCardHeader(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        trailing?.invoke()
    }
}

// === TAB 1: Real-time Connection Monitor ===
@Composable
fun LiveMonitorScreen(
    cacheHits: Long,
    upstreamQueries: Long,
    upstreamAverageLatencyMs: Float,
    dohEnabled: Boolean,
    dohQueries: Long
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferenceKeys.prefs(context) }

    var queries by remember { mutableStateOf(ClearGuardVpnService.recentBlocked()) }
    var isProtected by remember { mutableStateOf(ClearGuardVpnService.isRunning()) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedQuery by remember { mutableStateOf<ClearGuardVpnService.BlockedQuery?>(null) }
    // Currently-active "Allow once" reprieves (domain -> remaining millis), so they're visible and revocable.
    var tempAllows by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    // Locally-stored false-block reports the user filed (never uploaded); shown for review/resolution.
    var reportedFPs by remember { mutableStateOf(fetchReportedFalsePositives(context)) }
    var upstreamDns by remember {
        mutableStateOf(
            prefs.getString(PreferenceKeys.KEY_UPSTREAM_DNS, PreferenceKeys.DEFAULT_UPSTREAM_DNS)
                ?: PreferenceKeys.DEFAULT_UPSTREAM_DNS
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            queries = ClearGuardVpnService.recentBlocked()
            isProtected = ClearGuardVpnService.isRunning()
            nowMillis = System.currentTimeMillis()
            tempAllows = fetchTempAllows(context)
            upstreamDns = prefs.getString(PreferenceKeys.KEY_UPSTREAM_DNS, PreferenceKeys.DEFAULT_UPSTREAM_DNS)
                ?: PreferenceKeys.DEFAULT_UPSTREAM_DNS
            kotlinx.coroutines.delay(if (isProtected) 1200L else 2500L)
        }
    }

    fun allow(domain: String) {
        val normalized = com.clearguard.app.blocking.HostBlocker.normalizeDomain(domain) ?: domain
        PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_ALLOWLIST, normalized)
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, normalized)
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_SECURITY_BLOCKS, normalized)
        scope.launch {
            withContext(Dispatchers.IO) {
                com.clearguard.app.blocking.HostBlocker.get(context).reload()
                ClearGuardVpnService.reloadIfRunning(context)
            }
            queries = ClearGuardVpnService.recentBlocked()
        }
    }

    fun block(domain: String) {
        val normalized = com.clearguard.app.blocking.HostBlocker.normalizeDomain(domain) ?: domain
        PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, normalized)
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_ALLOWLIST, normalized)
        scope.launch {
            withContext(Dispatchers.IO) {
                com.clearguard.app.blocking.HostBlocker.get(context).reload()
                ClearGuardVpnService.reloadIfRunning(context)
            }
            queries = ClearGuardVpnService.recentBlocked()
        }
    }

    // "Allow once" — a one-hour reprieve via the in-memory overlay. Takes effect immediately (the
    // resolver reads it live) with no allowlist change and no VPN reload, so it's the safe undo for
    // a false positive without permanently weakening protection.
    fun allowOnce(domain: String) {
        if (domain.startsWith("phone:")) return
        val normalized = com.clearguard.app.blocking.HostBlocker.normalizeDomain(domain) ?: domain
        com.clearguard.app.blocking.HostBlocker.get(context).allowTemporarily(normalized, 60L * 60L * 1000L)
        queries = ClearGuardVpnService.recentBlocked()
        tempAllows = fetchTempAllows(context)
    }

    // Cancel an active reprieve early — protection on this domain resumes immediately (read live).
    fun revokeAllowOnce(domain: String) {
        com.clearguard.app.blocking.HostBlocker.get(context).clearTemporaryAllow(domain)
        tempAllows = fetchTempAllows(context)
    }

    // "Report false block" — records the block locally (never uploaded) and grants a one-hour
    // reprieve so the user isn't stuck while it's noted.
    fun reportFalseBlock(query: ClearGuardVpnService.BlockedQuery) {
        val normalized = com.clearguard.app.blocking.HostBlocker.normalizeDomain(query.domain) ?: query.domain
        val entry = normalized + "\t" + query.reason + "\t" + System.currentTimeMillis()
        PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_REPORTED_FALSE_POSITIVES, entry)
        if (!query.domain.startsWith("phone:")) {
            com.clearguard.app.blocking.HostBlocker.get(context).allowTemporarily(normalized, 60L * 60L * 1000L)
        }
        queries = ClearGuardVpnService.recentBlocked()
        tempAllows = fetchTempAllows(context)
        reportedFPs = fetchReportedFalsePositives(context)
    }

    // Resolve a reported false block by permanently allowlisting the domain, then clearing the
    // report and any redundant short-term reprieve.
    fun allowReportedDomain(item: ReportedFalseBlock) {
        if (!item.domain.startsWith("phone:")) {
            allow(item.domain)
            com.clearguard.app.blocking.HostBlocker.get(context).clearTemporaryAllow(item.domain)
        }
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_REPORTED_FALSE_POSITIVES, item.raw)
        reportedFPs = fetchReportedFalsePositives(context)
        tempAllows = fetchTempAllows(context)
    }

    // Remove a single report without changing any block/allow rule.
    fun dismissReport(item: ReportedFalseBlock) {
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_REPORTED_FALSE_POSITIVES, item.raw)
        reportedFPs = fetchReportedFalsePositives(context)
    }

    fun clearAllReports() {
        reportedFPs.forEach {
            PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_REPORTED_FALSE_POSITIVES, it.raw)
        }
        reportedFPs = fetchReportedFalsePositives(context)
    }

    val tempAllowKeys = remember(tempAllows) { tempAllows.mapTo(mutableSetOf()) { it.first } }
    val blockedCount = remember(queries) { queries.count { it.blocked } }
    val allowedCount = remember(queries) { queries.count { !it.blocked } }
    val threatCount = remember(queries) { queries.count { it.status == "threat" || it.status == "bypass" } }
    val cacheCount = remember(queries) { queries.count { !it.blocked && it.reason.equals("Cache hit", ignoreCase = true) } }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                ProConsoleHeader(
                    isProtected = isProtected,
                    queryCount = queries.size,
                    blockedCount = blockedCount,
                    threatCount = threatCount
                )
            }

            if (tempAllows.isNotEmpty()) {
                item {
                    TemporaryAllowCard(
                        entries = tempAllows,
                        onRevoke = { revokeAllowOnce(it) }
                    )
                }
            }

            item {
                FeatureTransparencyPanel(
                    context = context,
                    compact = true
                )
            }

            item {
                DnsMonitorCard(
                    isProtected = isProtected,
                    cacheHits = cacheHits,
                    upstreamQueries = upstreamQueries,
                    upstreamAverageLatencyMs = upstreamAverageLatencyMs,
                    dohEnabled = dohEnabled,
                    dohQueries = dohQueries,
                    upstreamDns = upstreamDns,
                    sessionCacheHits = cacheCount,
                    sessionAllowed = allowedCount,
                    sessionBlocked = blockedCount
                )
            }

            item {
                NetworkGraphCard(
                    queries = queries,
                    isProtected = isProtected,
                    onSelectQuery = { selectedQuery = it }
                )
            }

            item {
                TerminalLogsCard(
                    queries = queries,
                    nowMillis = nowMillis,
                    isProtected = isProtected,
                    tempAllowKeys = tempAllowKeys,
                    onSelectQuery = { selectedQuery = it }
                )
            }

            if (reportedFPs.isNotEmpty()) {
                item {
                    ReportedFalseBlocksCard(
                        items = reportedFPs,
                        nowMillis = nowMillis,
                        onAllow = { allowReportedDomain(it) },
                        onDismiss = { dismissReport(it) },
                        onClearAll = { clearAllReports() }
                    )
                }
            }
        }

        if (selectedQuery != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.48f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { selectedQuery = null }
            )
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = selectedQuery != null,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.84f, stiffness = 320f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220)
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            selectedQuery?.let { query ->
                ProDomainInspector(
                    query = query,
                    onDismiss = { selectedQuery = null },
                    onAllow = {
                        allow(query.domain)
                        selectedQuery = null
                    },
                    onAllowOnce = {
                        allowOnce(query.domain)
                        selectedQuery = null
                    },
                    onBlock = {
                        block(query.domain)
                        selectedQuery = null
                    },
                    onReport = {
                        reportFalseBlock(query)
                        selectedQuery = null
                    }
                )
            }
        }
    }
}

@Composable
private fun ProConsoleHeader(
    isProtected: Boolean,
    queryCount: Int,
    blockedCount: Int,
    threatCount: Int
) {
    val statusColor = if (isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, elevation = 2.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            PrivacyCardHeader(
                title = "Live Monitor",
                subtitle = if (isProtected) "DNS filtering active" else "Protection paused",
                icon = Icons.Default.Radar,
                trailing = {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = statusColor.copy(alpha = 0.12f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = if (isProtected) "Active" else "Idle",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = statusColor
                            )
                        }
                    }
                }
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ProMetricTile(
                    label = "Queries",
                    value = queryCount.toString(),
                    icon = Icons.Default.QueryStats,
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                ProMetricTile(
                    label = "Blocked",
                    value = blockedCount.toString(),
                    icon = Icons.Default.Block,
                    accent = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                ProMetricTile(
                    label = "Threats",
                    value = threatCount.toString(),
                    icon = Icons.Default.Warning,
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ProMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DnsMonitorCard(
    isProtected: Boolean,
    cacheHits: Long,
    upstreamQueries: Long,
    upstreamAverageLatencyMs: Float,
    dohEnabled: Boolean,
    dohQueries: Long,
    upstreamDns: String,
    sessionCacheHits: Int,
    sessionAllowed: Int,
    sessionBlocked: Int
) {
    val totalDns = (cacheHits + upstreamQueries).coerceAtLeast(1L)
    val cacheRate = ((cacheHits * 100f) / totalDns).toInt().coerceIn(0, 100)
    val routeLabel = when {
        !isProtected -> "Paused"
        dohEnabled -> "Encrypted DoH"
        else -> "Classic UDP"
    }

    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, elevation = 2.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
            PrivacyCardHeader(
                title = "DNS Monitor",
                subtitle = routeLabel,
                icon = Icons.Default.Dns
            )

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DnsMonitorRow("Local VPN", if (isProtected) "10.64.0.1 / fd00::1" else "Offline", if (isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                DnsMonitorRow("Upstream", if (dohEnabled) "DoH endpoint" else upstreamDns, if (dohEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                DnsMonitorRow("Latency", if (upstreamAverageLatencyMs > 0f) "${upstreamAverageLatencyMs.toInt()} ms avg" else "Learning…", MaterialTheme.colorScheme.tertiary)
                DnsMonitorRow("Cache", "$cacheRate% hit rate · $sessionCacheHits session hits", MaterialTheme.colorScheme.primary)
                DnsMonitorRow("Session", "$sessionAllowed allowed · $sessionBlocked blocked", if (sessionBlocked > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                DnsMonitorRow("DoH queries", dohQueries.toString(), if (dohEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DnsMonitorRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun NetworkGraphCard(
    queries: List<ClearGuardVpnService.BlockedQuery>,
    isProtected: Boolean,
    onSelectQuery: (ClearGuardVpnService.BlockedQuery) -> Unit
) {
    val graphQueries = remember(queries) { queries.take(18) }
    val appNames = remember(graphQueries) { graphQueries.map { cleanAppName(it) }.distinct().take(5) }
    val domainNames = remember(graphQueries) { graphQueries.map { rootDomain(it.domain) }.distinct().take(7) }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(286.dp),
        cornerRadius = 20.dp,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(18.dp)) {
            PrivacyCardHeader(
                title = "Connection Map",
                subtitle = "${graphQueries.size} recent connections",
                icon = Icons.Default.AccountTree
            )

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                if (graphQueries.isEmpty()) {
                    EmptyProConsoleState(isProtected = isProtected, modifier = Modifier.align(Alignment.Center))
                } else {
                    Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        val w = size.width
                        val h = size.height
                        val center = Offset(w * 0.50f, h * 0.52f)

                        for (line in 0..5) {
                            val y = h * (line / 5f)
                            drawLine(
                                color = ClearColors.border.copy(alpha = 0.10f),
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = 1f
                            )
                        }
                        for (line in 0..4) {
                            val x = w * (line / 4f)
                            drawLine(
                                color = ClearColors.border.copy(alpha = 0.08f),
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1f
                            )
                        }

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(ClearColors.green.copy(alpha = 0.35f), Color.Transparent),
                                center = center,
                                radius = 68.dp.toPx()
                            ),
                            center = center,
                            radius = 68.dp.toPx()
                        )
                        drawCircle(color = ClearColors.green.copy(alpha = 0.90f), center = center, radius = 16.dp.toPx())
                        drawCircle(color = Color.White.copy(alpha = 0.90f), center = center, radius = 4.dp.toPx())

                        val appPositions = appNames.mapIndexed { index, appName ->
                            val y = h * ((index + 1f) / (appNames.size + 1f))
                            appName to Offset(w * 0.16f, y)
                        }.toMap()
                        val domainPositions = domainNames.mapIndexed { index, domain ->
                            val y = h * ((index + 1f) / (domainNames.size + 1f))
                            domain to Offset(w * 0.84f, y)
                        }.toMap()

                        graphQueries.forEachIndexed { index, query ->
                            val app = appPositions[cleanAppName(query)]
                            val domain = domainPositions[rootDomain(query.domain)]
                            if (app != null && domain != null) {
                                val via = Offset(
                                    x = center.x + ((index % 3) - 1) * 10.dp.toPx(),
                                    y = center.y + ((index % 4) - 1.5f) * 8.dp.toPx()
                                )
                                val lineColor = queryAccent(query)
                                drawLine(color = lineColor.copy(alpha = 0.36f), start = app, end = via, strokeWidth = 1.4.dp.toPx())
                                drawLine(color = lineColor.copy(alpha = 0.52f), start = via, end = domain, strokeWidth = 1.4.dp.toPx())
                                drawCircle(color = lineColor.copy(alpha = 0.75f), center = via, radius = 2.2.dp.toPx())
                            }
                        }

                        appPositions.values.forEach { pos ->
                            drawCircle(color = ClearColors.blue.copy(alpha = 0.92f), center = pos, radius = 10.dp.toPx())
                            drawCircle(color = Color.White.copy(alpha = 0.85f), center = pos, radius = 3.dp.toPx())
                        }
                        domainPositions.forEach { (domain, pos) ->
                            val matching = graphQueries.firstOrNull { rootDomain(it.domain) == domain }
                            val color = matching?.let { queryAccent(it) } ?: ClearColors.muted
                            drawCircle(color = color.copy(alpha = 0.92f), center = pos, radius = 11.dp.toPx())
                            drawCircle(color = Color.White.copy(alpha = 0.85f), center = pos, radius = 3.dp.toPx())
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            if (graphQueries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    graphQueries.take(3).forEach { query ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(queryAccent(query).copy(alpha = 0.12f))
                                .border(1.dp, queryAccent(query).copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                                .clickable { onSelectQuery(query) }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = rootDomain(query.domain),
                                fontSize = 10.sp,
                                color = queryAccent(query),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLogsCard(
    queries: List<ClearGuardVpnService.BlockedQuery>,
    nowMillis: Long,
    isProtected: Boolean,
    tempAllowKeys: Set<String>,
    onSelectQuery: (ClearGuardVpnService.BlockedQuery) -> Unit
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 20.dp, elevation = 2.dp) {
        Column(modifier = Modifier.padding(18.dp)) {
            PrivacyCardHeader(
                title = "Recent Activity",
                subtitle = "Last 24 queries · on-device only",
                icon = Icons.Default.History
            )

            Spacer(Modifier.height(12.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (queries.isEmpty()) {
                    EmptyProConsoleState(isProtected = isProtected, modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp))
                } else {
                    queries.take(24).forEach { query ->
                        TerminalLogRow(
                            query = query,
                            nowMillis = nowMillis,
                            temporarilyAllowed = query.blocked && isDomainTemporarilyAllowed(query.domain, tempAllowKeys),
                            onClick = { onSelectQuery(query) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalLogRow(
    query: ClearGuardVpnService.BlockedQuery,
    nowMillis: Long,
    temporarilyAllowed: Boolean,
    onClick: () -> Unit
) {
    val accent = queryAccent(query)
    val status = queryStatus(query)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(52.dp)) {
                Text(
                    text = formatTime(query.timeMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = relativeLogTime(nowMillis - query.timeMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent.copy(alpha = 0.14f)
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = query.domain,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (temporarilyAllowed) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "Allowed",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "${cleanAppName(query)} · ${query.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyProConsoleState(isProtected: Boolean, modifier: Modifier = Modifier) {
    val tint = if (isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier.padding(vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.12f)
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(16.dp)
                    .size(28.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isProtected) "Waiting for DNS activity" else "Start protection to monitor traffic",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ProDomainInspector(
    query: ClearGuardVpnService.BlockedQuery,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
    onAllowOnce: () -> Unit,
    onBlock: () -> Unit,
    onReport: () -> Unit
) {
    val accent = queryAccent(query)
    val category = domainCategory(query)
    val explanation = remember(query.domain, query.reason, query.status, query.threatScore, query.blocked) {
        com.clearguard.app.security.BlockExplainer.explain(query)
    }
    val isPhoneEvent = query.domain.startsWith("phone:")
    val route = when {
        query.blocked -> "Local sinkhole"
        query.reason.equals("Cache hit", ignoreCase = true) -> "Local cache"
        query.reason.contains("DoH", ignoreCase = true) -> "DoH upstream"
        else -> "UDP upstream"
    }
    val rawHash = query.domain.hashCode()
    val ipHash = if (rawHash == Int.MIN_VALUE) 0 else abs(rawHash)
    val resolvedIp = "172.20.${ipHash % 255}.${(ipHash / 255) % 255}"
    val resolvedIpv6 = "fd7a:${(ipHash % 9999).toString().padStart(4, '0')}::${(ipHash % 4096).toString(16)}"

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
            .heightIn(max = 560.dp),
        cornerRadius = 24.dp,
        elevation = 28.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Domain Inspector", fontSize = 13.sp, color = ClearColors.muted, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = query.domain,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClearColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.14f))
                        .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(queryStatus(query), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = accent)
                }
            }

            Spacer(Modifier.height(14.dp))

            BlockExplanationCard(explanation)

            if (explanation.showHelpline) {
                Spacer(Modifier.height(10.dp))
                InspectorHelplineCard()
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InspectorMetric("CATEGORY", category, accent, Modifier.weight(1f))
                InspectorMetric("SCORE", query.threatScore.coerceAtLeast(if (query.blocked) 20 else 0).toString(), ClearColors.warning, Modifier.weight(0.72f))
            }

            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.28f))
                    .border(1.dp, ClearColors.border.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                InspectorRecord("APP", cleanAppName(query))
                InspectorRecord("PACKAGE", query.appPackage.ifBlank { "unknown" })
                InspectorRecord("POLICY", query.reason)
                InspectorRecord("ROUTE", route)
                InspectorRecord("LATENCY", if (query.blocked) "0 ms sinkhole" else if (query.latencyMs == 0) "0 ms cache" else "${query.latencyMs} ms")
            }

            Spacer(Modifier.height(10.dp))

            Text("DNS Records", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ClearColors.text)
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ClearColors.bg.copy(alpha = 0.50f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (query.blocked) {
                    InspectorRecord("A", "0.0.0.0")
                    InspectorRecord("AAAA", "::")
                    InspectorRecord("TTL", "0 seconds")
                    InspectorRecord("ACTION", "sinkhole response")
                } else {
                    InspectorRecord("A", resolvedIp)
                    InspectorRecord("AAAA", resolvedIpv6)
                    InspectorRecord("TTL", if (query.reason.equals("Cache hit", true)) "cached" else "300 seconds")
                    InspectorRecord("ACTION", "forwarded")
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                // Phone / call events aren't DNS domains, so allow/block don't apply.
                isPhoneEvent -> {
                    PrimaryButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        accent = ClearColors.muted,
                        contentColor = Color.White
                    ) {
                        Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                query.blocked -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClearColors.muted)
                        }
                        PrimaryButton(
                            onClick = onAllowOnce,
                            modifier = Modifier.weight(1f),
                            accent = ClearColors.green,
                            contentColor = Color.White
                        ) {
                            Text("Allow 1 hour", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            onClick = onAllow,
                            modifier = Modifier.weight(1f),
                            accent = ClearColors.blue,
                            contentColor = Color.White
                        ) {
                            Text("Allow always", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        SecondaryButton(onClick = onReport, modifier = Modifier.weight(1f)) {
                            Text("Report false block", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClearColors.warning)
                        }
                    }
                }
                else -> {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = ClearColors.muted)
                        }
                        PrimaryButton(
                            onClick = onBlock,
                            modifier = Modifier.weight(1.45f),
                            accent = ClearColors.danger,
                            contentColor = Color.White
                        ) {
                            Text("Block Domain", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun fetchTempAllows(context: android.content.Context): List<Pair<String, Long>> =
    com.clearguard.app.blocking.HostBlocker.get(context).temporaryAllowSnapshot()
        .map { it.key to it.value }
        .sortedByDescending { it.second }

/**
 * Whether a queried domain is covered by an active "Allow once" reprieve — subdomain-aware, mirroring
 * HostBlocker's own walk-up so a reprieve on example.com badges ads.example.com too.
 */
private fun isDomainTemporarilyAllowed(domain: String, allowedKeys: Set<String>): Boolean {
    if (allowedKeys.isEmpty() || domain.startsWith("phone:")) return false
    var cursor = com.clearguard.app.blocking.HostBlocker.normalizeDomain(domain) ?: return false
    while (true) {
        if (allowedKeys.contains(cursor)) return true
        val dot = cursor.indexOf('.')
        if (dot < 0) return false
        cursor = cursor.substring(dot + 1)
    }
}

/** A false-block the user reported, stored locally as "domain\treason\ttimestamp". */
private data class ReportedFalseBlock(
    val raw: String,
    val domain: String,
    val reason: String,
    val timestamp: Long
)

private fun fetchReportedFalsePositives(context: android.content.Context): List<ReportedFalseBlock> =
    PreferenceKeys.stringSetSorted(context, PreferenceKeys.KEY_REPORTED_FALSE_POSITIVES)
        .mapNotNull { raw ->
            val parts = raw.split("\t")
            val domain = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ReportedFalseBlock(
                raw = raw,
                domain = domain,
                reason = parts.getOrNull(1).orEmpty(),
                timestamp = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            )
        }
        .sortedByDescending { it.timestamp }

/** Day-aware "Xm/Xh/Xd ago" label for the reported-false-block list. */
private fun relativeReportTime(deltaMillis: Long): String {
    if (deltaMillis < 0) return "just now"
    val seconds = deltaMillis / 1000
    return when {
        seconds < 60 -> "just now"
        seconds < 3600 -> "${seconds / 60}m ago"
        seconds < 86_400 -> "${seconds / 3600}h ago"
        else -> "${seconds / 86_400}d ago"
    }
}

/**
 * Lists the false-block reports the user filed (stored on-device only). Each can be resolved with
 * "Allow always" (promote to the allowlist) or dismissed. Without this the reports were write-only.
 * Only shown when at least one report exists.
 */
@Composable
private fun ReportedFalseBlocksCard(
    items: List<ReportedFalseBlock>,
    nowMillis: Long,
    onAllow: (ReportedFalseBlock) -> Unit,
    onDismiss: (ReportedFalseBlock) -> Unit,
    onClearAll: () -> Unit
) {
    val accent = ClearColors.warning
    GlassCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Flag, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Reported false blocks",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClearColors.text
                    )
                    Text(
                        "Stored on this device only — never uploaded.",
                        fontSize = 11.sp,
                        color = ClearColors.muted,
                        lineHeight = 15.sp
                    )
                }
                if (items.size > 1) {
                    SecondaryButton(onClick = onClearAll) {
                        Text("Clear all", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ClearColors.muted)
                    }
                }
            }
            items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        item.domain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClearColors.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val meta = buildString {
                        if (item.reason.isNotBlank()) append(item.reason)
                        if (item.timestamp > 0L) {
                            if (isNotEmpty()) append(" · ")
                            append(relativeReportTime(nowMillis - item.timestamp))
                        }
                    }
                    if (meta.isNotBlank()) {
                        Text(
                            meta,
                            fontSize = 11.sp,
                            color = ClearColors.muted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            onClick = { onAllow(item) },
                            modifier = Modifier.weight(1f),
                            accent = ClearColors.green,
                            contentColor = Color.White
                        ) {
                            Text("Allow always", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        SecondaryButton(onClick = { onDismiss(item) }, modifier = Modifier.weight(1f)) {
                            Text("Dismiss", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ClearColors.muted)
                        }
                    }
                }
            }
        }
    }
}

/** "59m" / "45s" style remaining-time label for an active reprieve. */
private fun formatReprieve(millis: Long): String {
    val minutes = (millis / 60_000L).toInt()
    if (minutes >= 1) return "${minutes}m"
    val seconds = (millis / 1000L).toInt().coerceAtLeast(1)
    return "${seconds}s"
}

/**
 * Surfaces the "Allow once" reprieves that are currently in effect, with a live countdown and a
 * one-tap Undo. Without this the temporary allows are invisible — you can't tell a domain is open
 * or pull it back early. Only shown while at least one reprieve is active.
 */
@Composable
private fun TemporaryAllowCard(
    entries: List<Pair<String, Long>>,
    onRevoke: (String) -> Unit
) {
    val accent = ClearColors.green
    GlassCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Temporarily allowed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ClearColors.text
                    )
                    Text(
                        if (entries.size == 1) "1 domain allowed for now — protection resumes automatically."
                        else "${entries.size} domains allowed for now — protection resumes automatically.",
                        fontSize = 11.sp,
                        color = ClearColors.muted,
                        lineHeight = 15.sp
                    )
                }
            }
            entries.forEach { (domain, remaining) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            domain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClearColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text("${formatReprieve(remaining)} left", fontSize = 11.sp, color = accent)
                    }
                    Spacer(Modifier.width(8.dp))
                    SecondaryButton(onClick = { onRevoke(domain) }) {
                        Text("Undo", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = ClearColors.muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockExplanationCard(explanation: com.clearguard.app.security.BlockExplainer.Explanation) {
    val accent = when (explanation.severity) {
        com.clearguard.app.security.BlockExplainer.Severity.HIGH -> ClearColors.danger
        com.clearguard.app.security.BlockExplainer.Severity.MEDIUM -> ClearColors.warning
        com.clearguard.app.security.BlockExplainer.Severity.LOW -> ClearColors.blue
        com.clearguard.app.security.BlockExplainer.Severity.INFO -> ClearColors.muted
    }
    val icon = when (explanation.severity) {
        com.clearguard.app.security.BlockExplainer.Severity.HIGH -> Icons.Default.GppMaybe
        com.clearguard.app.security.BlockExplainer.Severity.MEDIUM -> Icons.Default.Warning
        com.clearguard.app.security.BlockExplainer.Severity.LOW -> Icons.Default.Shield
        com.clearguard.app.security.BlockExplainer.Severity.INFO -> Icons.Default.Info
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.26f), RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                explanation.headline,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ClearColors.text
            )
        }
        Text(explanation.detail, fontSize = 13.sp, color = ClearColors.text, lineHeight = 18.sp)
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.TipsAndUpdates, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp).padding(top = 1.dp))
            Spacer(Modifier.width(6.dp))
            Text(explanation.advice, fontSize = 12.sp, color = ClearColors.muted, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun InspectorHelplineCard() {
    val context = LocalContext.current
    val accent = ClearColors.danger
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Report cyber fraud (official)",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClearColors.text
        )
        Text(
            "If money was lost, report fast — the first hours matter most.",
            fontSize = 12.sp,
            color = ClearColors.muted,
            lineHeight = 16.sp
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton(
                onClick = {
                    try {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_DIAL, Uri.parse("tel:1930"))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f),
                accent = accent,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text("Call 1930", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            SecondaryButton(
                onClick = {
                    try {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse("https://cybercrime.gov.in"))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Exception) {}
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("cybercrime.gov.in", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun InspectorMetric(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
            .padding(11.dp)
    ) {
        Text(label, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = ClearColors.muted)
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InspectorRecord(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = ClearColors.muted)
        Text(
            value,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = ClearColors.text,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

private fun queryAccent(query: ClearGuardVpnService.BlockedQuery): Color {
    return when (query.status) {
        "allowed" -> if (query.reason.equals("Cache hit", ignoreCase = true)) ClearColors.green else ClearColors.blue
        "blocked" -> ClearColors.danger
        "threat" -> ClearColors.danger
        "bypass" -> ClearColors.warning
        else -> ClearColors.muted
    }
}

private fun queryStatus(query: ClearGuardVpnService.BlockedQuery): String {
    return when (query.status) {
        "allowed" -> if (query.reason.equals("Cache hit", ignoreCase = true)) "CACHE" else "ALLOW"
        "blocked" -> "BLOCK"
        "threat" -> "THREAT"
        "bypass" -> "BYPASS"
        else -> "LOG"
    }
}

private fun domainCategory(query: ClearGuardVpnService.BlockedQuery): String {
    return when {
        query.reason.contains("ai ad pattern", ignoreCase = true) -> "AI ad pattern"
        !query.blocked -> "Clean DNS"
        query.status == "bypass" -> "DNS bypass"
        query.threatScore >= 50 -> "Malware / phishing"
        query.reason.contains("scam", ignoreCase = true) -> "Scam shield"
        query.reason.contains("firewall", ignoreCase = true) -> "Custom firewall"
        query.reason.contains("ad", ignoreCase = true) || query.reason.contains("tracker", ignoreCase = true) -> "Ads / trackers"
        else -> "Blocklist"
    }
}

private fun cleanAppName(query: ClearGuardVpnService.BlockedQuery): String {
    return query.appName.takeIf { it.isNotBlank() && it != "unknown" } ?: "System DNS"
}

private fun rootDomain(domain: String): String {
    if (domain.startsWith("phone:")) return domain
    val parts = domain.trim('.').split('.').filter { it.isNotBlank() }
    if (parts.size <= 2) return domain
    return parts.takeLast(2).joinToString(".")
}

private fun relativeLogTime(deltaMillis: Long): String {
    if (deltaMillis < 0) return "now"
    val seconds = deltaMillis / 1000
    return when {
        seconds < 5 -> "now"
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> "${seconds / 3600}h"
    }
}

// === TAB 2: Analytics tab (ported from StatisticsScreen) ===
@Composable
fun AnalyticsTab(
    blockedTotal: Long,
    allowedTotal: Long,
    blockedToday: Long,
    cacheHits: Long,
    upstreamQueries: Long,
    upstreamAverageLatencyMs: Float,
    scamBlocked: Long,
    scamShieldEnabled: Boolean,
    dohEnabled: Boolean,
    dohQueries: Long
) {
    StatisticsScreen(
        blockedTotal = blockedTotal,
        allowedTotal = allowedTotal,
        blockedToday = blockedToday,
        cacheHits = cacheHits,
        upstreamQueries = upstreamQueries,
        upstreamAverageLatencyMs = upstreamAverageLatencyMs,
        scamBlocked = scamBlocked,
        scamShieldEnabled = scamShieldEnabled,
        dohEnabled = dohEnabled,
        dohQueries = dohQueries
    )
}

// === TAB 3: App Privacy Audit & Invisible Tracker Map ===
// Caps for the Privacy Audit tab. It renders inside a non-lazy verticalScroll Column, so the
// per-app cards and the per-connection Canvas links must stay bounded regardless of how long the
// VPN has been running and how many unique tracker domains it has observed.
private const val MAX_AUDIT_APPS = 40
private const val MAX_TRACKER_CONNECTIONS = 60

@Composable
fun AppAuditTab() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(emptyList<ClearGuardVpnService.AppStats>()) }
    var trackerConnections by remember { mutableStateOf(emptyList<ClearGuardVpnService.TrackerConnection>()) }

    // Fetch Stats. Both lists are rendered inside a non-lazy verticalScroll Column (and the
    // tracker list also drives one Canvas line per connection), so they must be bounded — the
    // tracker set grows with every unique domain the VPN sees and was freezing this screen
    // "after a while". Show only the most active entries.
    LaunchedEffect(Unit) {
        while (true) {
            stats = ClearGuardVpnService.getAppPrivacyStats()
                .sortedByDescending { it.blockedQueries }
                .take(MAX_AUDIT_APPS)
            trackerConnections = ClearGuardVpnService.getTrackerConnections()
                .sortedByDescending { it.count }
                .take(MAX_TRACKER_CONNECTIONS)
            kotlinx.coroutines.delay(2000L)
        }
    }

    var selectedAuditTab by remember { mutableStateOf(0) } // 0: Scores, 1: Tracker Map

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        AppSectionHeader("App Privacy Audit")

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedAuditTab == 0,
                onClick = { selectedAuditTab = 0 },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                icon = { Icon(Icons.Default.Leaderboard, contentDescription = null, modifier = Modifier.size(16.dp)) }
            ) {
                Text("Scores", fontSize = 12.sp)
            }
            SegmentedButton(
                selected = selectedAuditTab == 1,
                onClick = { selectedAuditTab = 1 },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                icon = { Icon(Icons.Default.Hub, contentDescription = null, modifier = Modifier.size(16.dp)) }
            ) {
                Text("Tracker Map", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedAuditTab == 0) {
            // App scores scrollable list
            if (stats.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Auditing your apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Privacy scores appear here as apps make DNS queries through ShieldDNS.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                stats.forEach { appStat ->
                    val score = calculatePrivacyScore(appStat.blockedQueries, appStat.totalQueries)
                    val scoreColor = when {
                        score >= 80 -> MaterialTheme.colorScheme.primary
                        score >= 50 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appStat.appName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${appStat.blockedQueries} trackers blocked · ${appStat.totalQueries} queries",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (appStat.trackers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Top: " + appStat.trackers.keys.take(2).joinToString(", "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            val trackColor = MaterialTheme.colorScheme.outlineVariant
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawArc(
                                        color = trackColor,
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = 4.dp.toPx())
                                    )
                                    drawArc(
                                        color = scoreColor,
                                        startAngle = -90f,
                                        sweepAngle = (score.toFloat() / 100f) * 360f,
                                        useCenter = false,
                                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$score",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "/100",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            PrivacyCardHeader(
                title = "Tracker Map",
                subtitle = "Apps connecting to known tracking companies",
                icon = Icons.Default.Hub
            )

            Spacer(modifier = Modifier.height(14.dp))

            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp)
            ) {
                if (trackerConnections.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "No trackers detected yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    TrackerMapCanvas(trackerConnections)
                }
            }
        }
    }
}

@Composable
fun TrackerMapCanvas(connections: List<ClearGuardVpnService.TrackerConnection>) {
    // Unique list of apps and tracker companies
    val apps = remember(connections) { connections.map { it.appName }.distinct().take(5) }
    val companies = remember(connections) { connections.map { it.companyName }.distinct().take(6) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val centerY = h / 2f

        // 1. Draw central "User" Node
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(ClearColors.green, ClearColors.green.copy(alpha = 0.15f)),
                center = Offset(centerX, centerY),
                radius = 28.dp.toPx()
            ),
            radius = 28.dp.toPx(),
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = Color.White,
            radius = 6.dp.toPx(),
            center = Offset(centerX, centerY)
        )

        // 2. Distribute App nodes on Inner Ring (radius = 70.dp)
        val appPositions = mutableMapOf<String, Offset>()
        val appRadius = 65.dp.toPx()
        apps.forEachIndexed { index, appName ->
            val angle = (index * 360f / apps.size) * (Math.PI / 180f)
            val ax = centerX + appRadius * cos(angle).toFloat()
            val ay = centerY + appRadius * sin(angle).toFloat()
            val appOffset = Offset(ax, ay)
            appPositions[appName] = appOffset

            // Draw link from User -> App
            drawLine(
                color = ClearColors.green.copy(alpha = 0.45f),
                start = Offset(centerX, centerY),
                end = appOffset,
                strokeWidth = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
            )

            // Draw App Node
            drawCircle(
                color = ClearColors.blue,
                radius = 12.dp.toPx(),
                center = appOffset
            )
            drawCircle(
                color = Color.White,
                radius = 4.dp.toPx(),
                center = appOffset
            )
        }

        // 3. Distribute Tracking Company nodes on Outer Ring (radius = 135.dp)
        val companyPositions = mutableMapOf<String, Offset>()
        val companyRadius = 125.dp.toPx()
        companies.forEachIndexed { index, compName ->
            val angle = (index * 360f / companies.size + 30f) * (Math.PI / 180f)
            val cx = centerX + companyRadius * cos(angle).toFloat()
            val cy = centerY + companyRadius * sin(angle).toFloat()
            val compOffset = Offset(cx, cy)
            companyPositions[compName] = compOffset

            // Draw Company Node
            drawCircle(
                color = ClearColors.danger,
                radius = 16.dp.toPx(),
                center = compOffset
            )
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = compOffset
            )
        }

        // 4. Draw links App -> Company
        connections.forEach { conn ->
            val appPos = appPositions[conn.appName]
            val compPos = companyPositions[conn.companyName]
            if (appPos != null && compPos != null) {
                drawLine(
                    color = ClearColors.danger.copy(alpha = 0.5f),
                    start = appPos,
                    end = compPos,
                    strokeWidth = 1.5.dp.toPx()
                )
            }
        }
    }
}

// === Aggregation helpers ===
private fun calculatePrivacyScore(blocked: Long, total: Long): Int {
    if (total <= 0) return 95
    // Base cleanliness: fewer trackers relative to queries = higher score
    val ratio = blocked.toDouble() / total.coerceAtLeast(1)
    var score = (100 - (ratio * 78)).toInt()

    // Bonus for very clean apps
    if (blocked < 3 && total < 30) score += 12
    // Penalty for heavy tracker apps
    if (ratio > 0.6) score -= 18

    return score.coerceIn(12, 100)
}

private fun formatTime(millis: Long): String {
    val date = java.util.Date(millis)
    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
    return sdf.format(date)
}

// =====================================================
// Scam Screenshot Scanner (Indian Scam Shield feature)
// User uploads screenshot of ad/SMS/WhatsApp/website → on-device OCR + heuristic analysis
// =====================================================
@Composable
fun ScamScreenshotScanner(initialText: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detections by remember { mutableStateOf<List<ScamScreenshotAnalyzer.Detection>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var suggestedDomains by remember { mutableStateOf<List<String>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            errorMessage = null
            detections = emptyList()
            extractedText = ""
            suggestedDomains = emptyList()

            // Load bitmap (compatible path for minSdk 26)
            scope.launch {
                isAnalyzing = true
                try {
                    // Decode the picked image off the main thread — full-size screenshots can be
                    // several megapixels and decoding them on the UI thread froze the scanner.
                    @Suppress("DEPRECATION")
                    val bmp = withContext(Dispatchers.IO) {
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    bitmap = bmp

                    // Run on-device OCR + Indian Scam Shield analysis
                    val dets = ScamScreenshotAnalyzer.analyze(context, bmp)
                    detections = dets

                    // Build display text from snippets + attempt domain extraction
                    extractedText = dets.joinToString("\n") { "• ${it.snippet}" }.ifBlank { "No readable text found." }
                    suggestedDomains = ScamScreenshotAnalyzer.extractSuspiciousDomains(
                        dets.joinToString(" ") { it.snippet } + " " + (selectedUri?.toString() ?: "")
                    )
                } catch (e: Exception) {
                    errorMessage = "OCR or analysis failed: ${e.message}"
                    detections = emptyList()
                } finally {
                    isAnalyzing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SmsTextScamScanner(initialText = initialText)

        GlassCard {
            Column(modifier = Modifier.padding(20.dp)) {
                PrivacyCardHeader(
                    title = "Screenshot Scanner",
                    subtitle = "OCR + Indian Scam Shield on-device",
                    icon = Icons.Default.ImageSearch
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Upload a suspicious ad, SMS, WhatsApp message or website screenshot. Checks for fake reward, KYC, payment, investment, job, and APK lures.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                LiquidGlassButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Select from Gallery", fontWeight = FontWeight.SemiBold)
                    }
                }

                bitmap?.let { preview ->
                    Spacer(Modifier.height(12.dp))
                    Text("Selected image:", fontSize = 12.sp, color = ClearColors.muted)
                    Spacer(Modifier.height(6.dp))
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = "Uploaded screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, ClearColors.border.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        if (isAnalyzing) {
            GlassCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = ClearColors.green)
                    Spacer(Modifier.height(12.dp))
                    Text("Running on-device OCR + scam analysis...", fontSize = 14.sp, color = ClearColors.text)
                    Text("All processing stays on your device.", fontSize = 12.sp, color = ClearColors.muted)
                }
            }
        }

        errorMessage?.let { message ->
            GlassCard {
                Text(message, color = ClearColors.danger, modifier = Modifier.padding(16.dp))
            }
        }

        if (detections.isNotEmpty()) {
            GlassCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ClearColors.danger)
                        Spacer(Modifier.width(8.dp))
                        Text("Detections — Indian Scam Shield", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${detections.size} scam pattern(s) found in the image text", fontSize = 13.sp, color = ClearColors.muted)
                    Spacer(Modifier.height(12.dp))

                    detections.forEach { det ->
                        ScamDetectionRow(det)
                    }
                }
            }
            // Official cyber-fraud helpline whenever a strong scam (esp. digital arrest) is found.
            val strongHit = detections.any { it.confidence >= 75 || it.category.startsWith("Digital arrest") }
            if (strongHit) {
                CyberHelplineCard()
            }
        } else if (bitmap != null && !isAnalyzing && detections.isEmpty() && errorMessage == null) {
            GlassCard {
                Text(
                    "No obvious scam patterns detected in the text. Still exercise caution — some sophisticated scams use images with little text.",
                    modifier = Modifier.padding(16.dp),
                    color = ClearColors.text
                )
            }
        }

        if (extractedText.isNotBlank()) {
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Extracted text (on-device OCR)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        extractedText.take(600) + if (extractedText.length > 600) "..." else "",
                        fontSize = 12.sp,
                        color = ClearColors.muted
                    )
                }
            }
        }

        if (suggestedDomains.isNotEmpty()) {
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Domains / links mentioned in image", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    suggestedDomains.forEach { domain ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Add to custom block list + reload protection
                                    val normalized = com.clearguard.app.blocking.HostBlocker.normalizeDomain(domain) ?: domain
                                    PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, normalized)
                                    com.clearguard.app.blocking.HostBlocker.get(context).reload()
                                    ClearGuardVpnService.reloadIfRunning(context)
                                    // Simple toast-like feedback
                                    // (In real app could use Snackbar)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(domain, modifier = Modifier.weight(1f), color = ClearColors.text)
                            Text("Block", color = ClearColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Tap any domain to add it to your block list.", fontSize = 11.sp, color = ClearColors.muted)
                }
            }
        }

        // Quick tip
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tip", fontWeight = FontWeight.Medium)
                Text(
                    "This scanner uses the same on-device Indian Scam Shield patterns as the DNS blocker. Best results on clear text screenshots (ads, SMS, WhatsApp forwards).",
                    fontSize = 12.sp, color = ClearColors.muted
                )
            }
        }
    }
}

// =====================================================
// SMS / Text Scam Scanner (Indian Scam Shield feature)
// Paste or share any SMS/WhatsApp text → on-device scam + UPI link analysis
// =====================================================
@Composable
fun SmsTextScamScanner(initialText: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf(initialText ?: "") }
    var detections by remember { mutableStateOf<List<ScamScreenshotAnalyzer.Detection>>(emptyList()) }
    var suggestedDomains by remember { mutableStateOf<List<String>>(emptyList()) }
    var safePaymentCheck by remember { mutableStateOf<OnDeviceRuleEngine.SafePaymentCheck?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var hasAnalyzed by remember { mutableStateOf(false) }
    var blockedFeedback by remember { mutableStateOf<String?>(null) }

    val analyze: () -> Unit = {
        val text = inputText.trim()
        if (text.isNotEmpty() && !isChecking) {
            scope.launch {
                isChecking = true
                blockedFeedback = null
                try {
                    val (dets, domains, paymentCheck) = withContext(Dispatchers.Default) {
                        val prefs = PreferenceKeys.prefs(context)
                        Triple(
                            ScamScreenshotAnalyzer.analyzeText(context, text),
                            ScamScreenshotAnalyzer.extractSuspiciousDomains(text),
                            if (prefs.getBoolean(
                                    PreferenceKeys.KEY_SAFE_PAYMENT_CHECKS_ENABLED,
                                    PreferenceKeys.DEFAULT_SAFE_PAYMENT_CHECKS_ENABLED
                                )
                            ) {
                                OnDeviceRuleEngine.safePaymentCheck(text)
                            } else {
                                null
                            }
                        )
                    }
                    detections = dets
                    suggestedDomains = domains
                    safePaymentCheck = paymentCheck
                    hasAnalyzed = true
                } catch (e: Exception) {
                    detections = emptyList()
                    suggestedDomains = emptyList()
                    safePaymentCheck = null
                    hasAnalyzed = true
                    android.util.Log.w("SmsTextScamScanner", "Scam text analysis failed: ${e.message}")
                } finally {
                    isChecking = false
                }
            }
        }
    }

    // Auto-analyze when text arrives via the Android share sheet / text selection
    LaunchedEffect(initialText) {
        if (!initialText.isNullOrBlank()) analyze()
    }

    GlassCard {
        Column(modifier = Modifier.padding(20.dp)) {
            PrivacyCardHeader(
                title = "Text Scam Check",
                subtitle = "Paste or share suspicious messages",
                icon = Icons.Default.Sms
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Checks UPI links, fake KYC/reward/job lures and high-risk phone numbers — fully on-device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            com.clearguard.app.ui.components.GlassTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.fillMaxWidth(),
                minHeight = 100.dp,
                placeholder = "Paste the message text here…",
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
            Spacer(Modifier.height(12.dp))

            LiquidGlassButton(
                onClick = analyze,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                accent = MaterialTheme.colorScheme.primary
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isChecking) "Analyzing…" else "Check for Scams",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    if (hasAnalyzed && !isChecking) {
        // Overall verdict combines detection confidence + payment risk into one score.
        val overallRisk = maxOf(
            detections.maxOfOrNull { it.confidence } ?: 0,
            safePaymentCheck?.riskScore ?: 0
        )
        ScamVerdictBanner(risk = overallRisk, signalCount = detections.size)

        safePaymentCheck?.let { check ->
            SafePaymentAlertCard(check = check)
        }

        // Surface India's official cyber-fraud helpline on any real scam verdict, and always for
        // the high-loss "digital arrest" / fake-authority pattern.
        val digitalArrestHit = detections.any { it.category.startsWith("Digital arrest") }
        if (overallRisk >= 40 || digitalArrestHit) {
            CyberHelplineCard()
        }

        if (detections.isNotEmpty()) {
            GlassCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ClearColors.danger)
                        Spacer(Modifier.width(8.dp))
                        Text("Scam signals in this message", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${detections.size} pattern(s) matched — do not pay, click links, or share OTPs.", fontSize = 13.sp, color = ClearColors.muted)
                    Spacer(Modifier.height(12.dp))

                    detections.forEach { det ->
                        ScamDetectionRow(det)
                    }
                }
            }
        } else {
            GlassCard {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ClearColors.green, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "No obvious scam patterns in this text. Still verify the sender through official channels before paying or sharing details.",
                        color = ClearColors.text,
                        fontSize = 13.sp
                    )
                }
            }
        }

        if (suggestedDomains.isNotEmpty()) {
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Links / domains in this message", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    suggestedDomains.forEach { domain ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val normalized = com.clearguard.app.blocking.HostBlocker.normalizeDomain(domain) ?: domain
                                    PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, normalized)
                                    com.clearguard.app.blocking.HostBlocker.get(context).reload()
                                    ClearGuardVpnService.reloadIfRunning(context)
                                    blockedFeedback = "$normalized added to your block list."
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(domain, modifier = Modifier.weight(1f), color = ClearColors.text)
                            Text("Block", color = ClearColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text(
                        blockedFeedback ?: "Tap any domain to add it to your block list.",
                        fontSize = 11.sp,
                        color = if (blockedFeedback != null) ClearColors.green else ClearColors.muted
                    )
                }
            }
        }
    }
}

@Composable
private fun SafePaymentAlertCard(check: OnDeviceRuleEngine.SafePaymentCheck) {
    val accent = when (check.riskLevel) {
        "High" -> ClearColors.danger
        "Medium" -> ClearColors.warning
        else -> ClearColors.green
    }
    GlassCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (check.riskLevel == "Low") Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Safe Payment Check",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = accent
                    )
                    Text(
                        check.alertMessage,
                        fontSize = 12.sp,
                        color = ClearColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${check.riskScore}/100",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
            }

            Spacer(Modifier.height(12.dp))
            PaymentDetailRow("Payee VPA", check.upi.vpa)
            PaymentDetailRow("Payee name", check.upi.payeeName ?: "Missing")
            PaymentDetailRow("Amount", check.upi.amount?.let { "₹$it" } ?: "Not specified")
            PaymentDetailRow("Action", check.recommendation)

            if (check.delayRecommendation != "NO_DELAY") {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Delay suggested: wait 30 seconds and verify through the official app or known contact.",
                    fontSize = 11.sp,
                    color = ClearColors.warning,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Why this alert?", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = ClearColors.text)
            check.reasons.forEach { reason ->
                Text(
                    "• $reason",
                    fontSize = 11.sp,
                    color = ClearColors.muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PaymentDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(label, fontSize = 11.sp, color = ClearColors.muted, modifier = Modifier.weight(0.36f))
        Text(
            value,
            fontSize = 11.sp,
            color = ClearColors.text,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.64f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Headline verdict for a scanned message: a color-coded badge + an animated risk meter. */
@Composable
private fun ScamVerdictBanner(risk: Int, signalCount: Int) {
    val accent = when {
        risk >= 70 -> ClearColors.danger
        risk >= 40 -> ClearColors.warning
        else -> ClearColors.green
    }
    val title = when {
        risk >= 70 -> "High risk — likely a scam"
        risk >= 40 -> "Caution — some scam signals"
        else -> "Looks clean"
    }
    val subtitle = when {
        risk >= 70 -> "Do not pay, tap links, or share OTPs."
        risk >= 40 -> "Verify the sender before acting."
        else -> "No strong scam signals found."
    }
    val badge = when {
        risk >= 70 -> "HIGH RISK"
        risk >= 40 -> "CAUTION"
        else -> "LIKELY SAFE"
    }
    val meter by animateFloatAsState(
        targetValue = (risk / 100f).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "riskMeter"
    )
    // Severity-tinted surface so a real verdict reads as a true alert (fraud-alert reference
    // pattern), not a neutral glass card.
    val tint = when {
        risk >= 70 -> 0.13f
        risk >= 40 -> 0.08f
        else -> 0.05f
    }

    GlassCard {
        Box {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(accent.copy(alpha = tint))
            )
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (risk >= 40) Icons.Default.Warning else Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = accent)
                        Text(subtitle, fontSize = 12.sp, color = ClearColors.muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.18f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                badge,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accent,
                                letterSpacing = 0.5.sp
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("$risk", fontWeight = FontWeight.Bold, fontSize = 26.sp, color = accent)
                    }
                }

                Spacer(Modifier.height(14.dp))
                // Risk meter track + fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(ClearColors.muted.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(meter)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accent.copy(alpha = 0.65f), accent)
                                )
                            )
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (signalCount > 0) "$signalCount scam signal(s) detected" else "On-device risk score",
                        fontSize = 11.sp,
                        color = ClearColors.muted,
                        modifier = Modifier.weight(1f)
                    )
                    Text("$risk / 100", fontSize = 11.sp, color = ClearColors.muted)
                }
            }
        }
    }
}

/**
 * Official cyber-fraud reporting card (National Cyber Crime Reporting Portal / I4C).
 * Shown when a message scores as a real scam so the user gets the genuine government
 * next step — dial 1930 or open cybercrime.gov.in — instead of a scammer's fake "helpline".
 */
@Composable
private fun CyberHelplineCard() {
    val context = LocalContext.current
    val info = remember { OnDeviceRuleEngine.cyberHelplineInfo() }
    GlassCard {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = ClearColors.blue)
                Spacer(Modifier.width(8.dp))
                Text("If you've been targeted", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(8.dp))
            info.advice.forEach { line ->
                Row(modifier = Modifier.padding(vertical = 3.dp)) {
                    Text("•  ", color = ClearColors.blue, fontSize = 13.sp)
                    Text(line, fontSize = 13.sp, color = ClearColors.text)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                PrimaryButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_DIAL,
                                    android.net.Uri.parse("tel:${info.number}")
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    accent = ClearColors.danger
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Call ${info.number}")
                }
                Spacer(Modifier.width(10.dp))
                PrimaryButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(info.portal)
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                    accent = ClearColors.blue
                ) {
                    Icon(Icons.Default.Language, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Report online")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "1930 is India's official cyber-fraud helpline. Reporting fast can help freeze the money.",
                fontSize = 11.sp,
                color = ClearColors.muted
            )
        }
    }
}

/** A single scam detection with a severity stripe down its left edge. */
@Composable
private fun ScamDetectionRow(det: ScamScreenshotAnalyzer.Detection) {
    val accent = when {
        det.confidence >= 80 -> ClearColors.danger
        det.confidence >= 60 -> ClearColors.warning
        else -> ClearColors.blue
    }
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Severity stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(det.category, fontWeight = FontWeight.Bold, color = accent, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(accent.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("${det.confidence}%", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = accent)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(det.reason, fontSize = 12.sp, color = ClearColors.text)
                if (det.snippet.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "\"${det.snippet.take(140)}${if (det.snippet.length > 140) "..." else ""}\"",
                        fontSize = 11.sp,
                        color = ClearColors.muted,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                    )
                }
            }
        }
    }
}
