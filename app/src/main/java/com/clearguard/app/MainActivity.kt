package com.clearguard.app

import android.Manifest
import android.app.Activity
import android.content.Intent
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.blocking.BlocklistUpdateWorker
import com.clearguard.app.blocking.HostBlocker
import com.clearguard.app.ui.screens.BlocklistsScreen
import com.clearguard.app.ui.screens.BrowserScreen
import com.clearguard.app.ui.screens.DashboardScreen
import com.clearguard.app.ui.screens.PrivacyScreen
import com.clearguard.app.ui.screens.OnboardingScreen
import com.clearguard.app.ui.screens.SettingsScreen
import com.clearguard.app.ui.theme.ClearGuardTheme
import com.clearguard.app.ui.theme.ThemeMode
import com.clearguard.app.vpn.ClearGuardVpnService
import androidx.compose.material3.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

enum class AppScreen(val title: String, val icon: ImageVector) {
    Dashboard("Home", Icons.Default.Shield),
    Privacy("Privacy", Icons.Default.VerifiedUser),
    Browser("Browser", Icons.Default.Language),
    Blocklists("Lists", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PreferenceKeys.ensureDefaults(this)

        // Heavy startup work (blocklist parse, WorkManager DB init on first launch, asset/model
        // loads, RASP /proc probes) must NOT run on the main thread: doing it synchronously before
        // the first frame blocks the UI thread and triggers a launch ANR. None of it is needed to
        // render the initial UI, so kick it off the main thread.
        lifecycleScope.launch(Dispatchers.IO) {
            HostBlocker.get(this@MainActivity).reload()
            BlocklistUpdateWorker.sync(this@MainActivity)

            // On-device rule engine + TFLite phishing classifier (no-ops gracefully if absent).
            com.clearguard.app.security.PhishingClassifier.initialize(this@MainActivity)

            // Small FRI risk DB from assets + auto-seed (Mobile Number Risk Scoring).
            com.clearguard.app.security.OnDeviceRuleEngine.loadLocalFRIDB(this@MainActivity)

            // RASP + Anti-tamper check (opt-in). Reads /proc/self/maps, so keep it off the UI thread.
            if (com.clearguard.app.PreferenceKeys.prefs(this@MainActivity).getBoolean(
                    com.clearguard.app.PreferenceKeys.KEY_RASP_ENABLED,
                    com.clearguard.app.PreferenceKeys.DEFAULT_RASP_ENABLED
                )) {
                val raspReport = com.clearguard.app.security.RaspGuard.checkIntegrity(this@MainActivity)
                com.clearguard.app.security.RaspGuard.logReport(raspReport)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11)
        }
        // Text shared from another app (SMS, WhatsApp) or the text-selection toolbar →
        // open the on-device SMS/Text Scam Check with it.
        val sharedScamText = when (intent?.action) {
            Intent.ACTION_SEND ->
                if (intent.type == "text/plain") intent.getStringExtra(Intent.EXTRA_TEXT) else null
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }?.takeIf { it.isNotBlank() }

        setContent {
            var themeMode by remember {
                mutableStateOf(
                    ThemeMode.fromPref(
                        PreferenceKeys.prefs(this).getString(
                            PreferenceKeys.KEY_THEME_MODE,
                            PreferenceKeys.DEFAULT_THEME_MODE
                        )
                    )
                )
            }
            ClearGuardTheme(themeMode = themeMode) {
                ClearGuardApp(
                    themeMode = themeMode,
                    sharedScamText = sharedScamText,
                    onThemeModeChange = { mode ->
                        PreferenceKeys.prefs(this).edit()
                            .putString(PreferenceKeys.KEY_THEME_MODE, mode.prefValue)
                            .apply()
                        themeMode = mode
                    }
                )
            }
        }
    }
}

@Composable
fun ClearGuardApp(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    sharedScamText: String? = null
) {
    val context = LocalContext.current

    var showOnboarding by remember {
        mutableStateOf(
            !PreferenceKeys.prefs(context)
                .getBoolean(PreferenceKeys.KEY_ONBOARDING_SEEN, false)
        )
    }
    if (showOnboarding) {
        OnboardingScreen(onComplete = {
            PreferenceKeys.prefs(context).edit()
                .putBoolean(PreferenceKeys.KEY_ONBOARDING_SEEN, true)
                .apply()
            showOnboarding = false
        })
        return
    }

    // Fresh UI: no splash, no 3D animations — go straight to the clean app.

    var currentScreen by remember {
        mutableStateOf(if (sharedScamText != null) AppScreen.Privacy else AppScreen.Dashboard)
    }

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
    val indianScamShieldEnabledState = remember { mutableStateOf(loadIndianScamShieldEnabled(context)) }
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
                    indianScamShieldEnabledState.value = loadIndianScamShieldEnabled(context)
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
            indianScamShieldEnabledState.value = loadIndianScamShieldEnabled(context)
            upstreamQueryState.value = loadUpstreamQueries(context)
            upstreamLatencyState.value = loadUpstreamAverageLatency(context)
            dohEnabledState.value = loadDohEnabled(context)
            dohQueryState.value = loadDohQueries(context)
            delay(if (isProtected) 3000L else 8000L)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    Scaffold(
        topBar = {
            // Clean, minimal top bar — title only when needed. Dashboard has its own strong hero.
            if (currentScreen != AppScreen.Dashboard) {
                TopAppBar(
                    title = {
                        Text(
                            text = currentScreen.title,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                AppScreen.entries.forEach { screen ->
                    val selected = currentScreen == screen
                    NavigationBarItem(
                        selected = selected,
                        onClick = { currentScreen = screen },
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                AppScreen.Privacy -> PrivacyScreen(
                    blockedTotal = blockedTotalState.value,
                    allowedTotal = allowedTotalState.value,
                    blockedToday = blockedTodayState.value,
                    cacheHits = cacheHitState.value,
                    upstreamQueries = upstreamQueryState.value,
                    upstreamAverageLatencyMs = upstreamLatencyState.value,
                    scamBlocked = scamBlockedState.value,
                    scamShieldEnabled = scamShieldEnabledState.value,
                    dohEnabled = dohEnabledState.value,
                    dohQueries = dohQueryState.value,
                    initialScanText = sharedScamText
                )
                AppScreen.Browser -> BrowserScreen()
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
                    indianScamShieldEnabled = indianScamShieldEnabledState.value,
                    onIndianScamShieldChange = { enabled ->
                        PreferenceKeys.prefs(context).edit()
                            .putBoolean(PreferenceKeys.KEY_INDIAN_SCAM_SHIELD_ENABLED, enabled)
                            .apply()
                        indianScamShieldEnabledState.value = enabled
                        com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
                    },
                    dohEnabled = dohEnabledState.value,
                    onDohEnabledChange = { enabled ->
                        PreferenceKeys.prefs(context).edit()
                            .putBoolean(PreferenceKeys.KEY_DOH_ENABLED, enabled)
                            .apply()
                        dohEnabledState.value = enabled
                    },
                    themeMode = themeMode,
                    onThemeModeChange = onThemeModeChange
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

private fun loadIndianScamShieldEnabled(context: android.content.Context): Boolean {
    val prefs: SharedPreferences = PreferenceKeys.prefs(context)
    return prefs.getBoolean(
        PreferenceKeys.KEY_INDIAN_SCAM_SHIELD_ENABLED,
        PreferenceKeys.DEFAULT_INDIAN_SCAM_SHIELD_ENABLED
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

// Fresh UI: removed GlassHeader, GlassBottomNavigation, and the entire ThreeDSplash (all 3D rotations, particles, infinite mesh, custom glass effects). 
// The new navigation is standard Material 3 NavigationBar + TopAppBar.
