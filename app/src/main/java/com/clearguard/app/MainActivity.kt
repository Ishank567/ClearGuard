package com.clearguard.app

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.blocking.HostBlocker
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.GlassCardHero
import com.clearguard.app.ui.screens.ActivityScreen
import com.clearguard.app.ui.screens.BlocklistsScreen
import com.clearguard.app.ui.screens.DashboardScreen
import com.clearguard.app.ui.screens.SettingsScreen
import com.clearguard.app.ui.screens.StatisticsScreen
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearGuardTheme
import com.clearguard.app.vpn.ClearGuardVpnService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

enum class AppScreen(val title: String, val icon: ImageVector) {
    Dashboard("Home", Icons.Default.Shield),
    Activity("Activity", Icons.Default.History),
    Statistics("Stats", Icons.Default.BarChart),
    Blocklists("Lists", Icons.Default.List),
    Settings("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceKeys.ensureDefaults(this)
        HostBlocker.get(this).reload()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
        setContent {
            ClearGuardTheme {
                ClearGuardApp()
            }
        }
    }
}

@Composable
fun ClearGuardApp() {
    val context = LocalContext.current

    var currentScreen by remember { mutableStateOf(AppScreen.Dashboard) }

    // Real state synced with the VPN service + live updates via broadcast
    var isProtected by remember { mutableStateOf(ClearGuardVpnService.isRunning()) }
    val blockedTodayState = remember { mutableStateOf(loadBlockedToday(context)) }
    val blockedTotalState = remember { mutableStateOf(loadBlockedTotal(context)) }
    val allowedTotalState = remember { mutableStateOf(loadAllowedTotal(context)) }
    val activeRulesState = remember { mutableStateOf(loadActiveRules(context)) }
    val cacheHitState = remember { mutableStateOf(loadCacheHits(context)) }
    val scamBlockedState = remember { mutableStateOf(loadScamBlocked(context)) }
    val scamBlockedTodayState = remember { mutableStateOf(loadScamBlockedToday(context)) }
    val scamShieldEnabledState = remember { mutableStateOf(loadScamShieldEnabled(context)) }
    val upstreamQueryState = remember { mutableStateOf(loadUpstreamQueries(context)) }
    val upstreamLatencyState = remember { mutableStateOf(loadUpstreamAverageLatency(context)) }
    val dohEnabledState = remember { mutableStateOf(loadDohEnabled(context)) }
    val dohQueryState = remember { mutableStateOf(loadDohQueries(context)) }

    // Listen for live stats updates from the VPN service (makes increments very visible)
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                if (intent?.action == ClearGuardVpnService.ACTION_STATS_CHANGED) {
                    blockedTodayState.value = loadBlockedToday(context)
                    blockedTotalState.value = loadBlockedTotal(context)
                    allowedTotalState.value = loadAllowedTotal(context)
                    activeRulesState.value = loadActiveRules(context)
                    cacheHitState.value = loadCacheHits(context)
                    scamBlockedState.value = loadScamBlocked(context)
                    scamBlockedTodayState.value = loadScamBlockedToday(context)
                    scamShieldEnabledState.value = loadScamShieldEnabled(context)
                    upstreamQueryState.value = loadUpstreamQueries(context)
                    upstreamLatencyState.value = loadUpstreamAverageLatency(context)
                    dohEnabledState.value = loadDohEnabled(context)
                    dohQueryState.value = loadDohQueries(context)
                }
            }
        }
        val filter = android.content.IntentFilter(ClearGuardVpnService.ACTION_STATS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) { /* ignore */ }
        }
    }

    // VPN permission launcher
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ClearGuardVpnService.start(context)
            isProtected = true
        } else {
            isProtected = false
        }
    }

    // Real protection toggle that actually starts/stops the VPN
    val toggleProtection: (Boolean) -> Unit = { enable ->
        if (enable) {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnPermissionLauncher.launch(prepareIntent)
            } else {
                ClearGuardVpnService.start(context)
                isProtected = true
            }
        } else {
            ClearGuardVpnService.stop(context)
            isProtected = false
        }
    }

    // Periodic sync + service state (backup to broadcast)
    LaunchedEffect(Unit) {
        while (isActive) {
            isProtected = ClearGuardVpnService.isRunning()
            blockedTodayState.value = loadBlockedToday(context)
            blockedTotalState.value = loadBlockedTotal(context)
            allowedTotalState.value = loadAllowedTotal(context)
            activeRulesState.value = loadActiveRules(context)
            cacheHitState.value = loadCacheHits(context)
            scamBlockedState.value = loadScamBlocked(context)
            scamBlockedTodayState.value = loadScamBlockedToday(context)
            scamShieldEnabledState.value = loadScamShieldEnabled(context)
            upstreamQueryState.value = loadUpstreamQueries(context)
            upstreamLatencyState.value = loadUpstreamAverageLatency(context)
            dohEnabledState.value = loadDohEnabled(context)
            dohQueryState.value = loadDohQueries(context)
            delay(if (isProtected) 3000L else 8000L)
        }
    }

    Scaffold(
        containerColor = ClearColors.bg,
        bottomBar = {
            GlassBottomNavigation(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Glassy top header
            GlassHeader(currentScreen.title, isProtected)

            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                },
                modifier = Modifier.weight(1f)
            ) { screen ->
                when (screen) {
                    AppScreen.Dashboard -> DashboardScreen(
                        isProtected = isProtected,
                        onToggleProtection = toggleProtection,
                        blockedToday = blockedTodayState.value.toInt(),
                        totalBlocked = activeRulesState.value,
                        cacheHits = cacheHitState.value,
                        upstreamQueries = upstreamQueryState.value,
                        scamShieldEnabled = scamShieldEnabledState.value,
                        scamBlockedToday = scamBlockedTodayState.value,
                        dohEnabled = dohEnabledState.value
                    )
                    AppScreen.Activity -> ActivityScreen(isProtected = isProtected)
                    AppScreen.Statistics -> StatisticsScreen(
                        blockedTotal = blockedTotalState.value,
                        allowedTotal = allowedTotalState.value,
                        blockedToday = blockedTodayState.value,
                        cacheHits = cacheHitState.value,
                        upstreamQueries = upstreamQueryState.value,
                        upstreamAverageLatencyMs = upstreamLatencyState.value,
                        scamBlocked = scamBlockedState.value,
                        scamShieldEnabled = scamShieldEnabledState.value,
                        dohEnabled = dohEnabledState.value,
                        dohQueries = dohQueryState.value
                    )
                    AppScreen.Blocklists -> BlocklistsScreen()
                    AppScreen.Settings -> SettingsScreen(
                        isProtected = isProtected,
                        onProtectionChange = toggleProtection,
                        scamShieldEnabled = scamShieldEnabledState.value,
                        onScamShieldChange = { enabled ->
                            PreferenceKeys.prefs(context).edit()
                                .putBoolean(PreferenceKeys.KEY_SCAM_SHIELD_ENABLED, enabled)
                                .apply()
                            scamShieldEnabledState.value = enabled
                        },
                        dohEnabled = dohEnabledState.value,
                        onDohEnabledChange = { enabled ->
                            PreferenceKeys.prefs(context).edit()
                                .putBoolean(PreferenceKeys.KEY_DOH_ENABLED, enabled)
                                .apply()
                            dohEnabledState.value = enabled
                        }
                    )
                }
            }
        }
    }
}

// === Real aggregate stats helpers ===

private fun loadBlockedToday(context: android.content.Context): Long {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getLong(PreferenceKeys.KEY_BLOCKED_TODAY, 0L)
}

private fun loadBlockedTotal(context: android.content.Context): Long {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getLong(PreferenceKeys.KEY_BLOCKED_COUNT, 0L)
}

private fun loadAllowedTotal(context: android.content.Context): Long {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getLong(PreferenceKeys.KEY_ALLOWED_COUNT, 0L)
}

private fun loadCacheHits(context: android.content.Context): Long {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getLong(PreferenceKeys.KEY_CACHE_HIT_COUNT, 0L)
}

private fun loadScamBlocked(context: android.content.Context): Long {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getLong(PreferenceKeys.KEY_SCAM_BLOCKED_COUNT, 0L)
}

private fun loadScamBlockedToday(context: android.content.Context): Long {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getLong(PreferenceKeys.KEY_SCAM_BLOCKED_TODAY, 0L)
}

private fun loadScamShieldEnabled(context: android.content.Context): Boolean {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getBoolean(
        PreferenceKeys.KEY_SCAM_SHIELD_ENABLED,
        PreferenceKeys.DEFAULT_SCAM_SHIELD_ENABLED
    )
}

private fun loadUpstreamQueries(context: android.content.Context): Long {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getLong(PreferenceKeys.KEY_UPSTREAM_QUERY_COUNT, 0L)
}

private fun loadUpstreamAverageLatency(context: android.content.Context): Float {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getFloat(PreferenceKeys.KEY_UPSTREAM_AVERAGE_LATENCY_MS, 0f)
}

private fun loadDohEnabled(context: android.content.Context): Boolean {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getBoolean(
        PreferenceKeys.KEY_DOH_ENABLED,
        PreferenceKeys.DEFAULT_DOH_ENABLED
    )
}

private fun loadDohQueries(context: android.content.Context): Long {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getLong(PreferenceKeys.KEY_DOH_QUERY_COUNT, 0L)
}

private fun loadActiveRules(context: android.content.Context): Int {
    return try {
        HostBlocker.get(context).snapshot().blockedHostCount
    } catch (e: Exception) {
        0
    }
}

@Composable
fun GlassHeader(title: String, isProtected: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "ClearGuard",
                fontSize = 13.sp,
                color = ClearColors.muted,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClearColors.text
            )
        }

        // Status pill (glass)
        GlassCard(
            modifier = Modifier.height(36.dp),
            cornerRadius = 18.dp,
            glassAlpha = 0.75f,
            elevation = 6.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxHeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(if (isProtected) ClearColors.green else ClearColors.danger)
                )
                Text(
                    text = if (isProtected) "Protected" else "Paused",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClearColors.text
                )
            }
        }
    }
}

@Composable
fun GlassBottomNavigation(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        cornerRadius = 28.dp,
        glassAlpha = 0.92f,
        elevation = 14.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppScreen.entries.forEach { screen ->
                val selected = currentScreen == screen
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    animationSpec = tween(180), label = "navScale"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onScreenSelected(screen) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = screen.icon,
                        contentDescription = screen.title,
                        tint = if (selected) ClearColors.green else ClearColors.muted,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = screen.title,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) ClearColors.green else ClearColors.muted
                    )
                }
            }
        }
    }
}
