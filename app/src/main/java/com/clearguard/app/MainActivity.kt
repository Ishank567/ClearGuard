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
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.GlassCardHero
import com.clearguard.app.ui.screens.BlocklistsScreen
import com.clearguard.app.ui.screens.BrowserScreen
import com.clearguard.app.ui.screens.DashboardScreen
import com.clearguard.app.ui.screens.PrivacyScreen
import com.clearguard.app.ui.screens.OnboardingScreen
import com.clearguard.app.ui.screens.SettingsScreen
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign
import com.clearguard.app.ui.theme.ClearGuardTheme
import com.clearguard.app.ui.theme.ThemeMode
import com.clearguard.app.ui.theme.ClearMeshBackground
import com.clearguard.app.vpn.ClearGuardVpnService
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
    // Skip the splash when the user shared text to scan — get them to the verdict fast
    var show3DSplash by remember { mutableStateOf(sharedScamText == null) }

    if (showOnboarding) {
        OnboardingScreen(onComplete = {
            PreferenceKeys.prefs(context).edit()
                .putBoolean(PreferenceKeys.KEY_ONBOARDING_SEEN, true)
                .apply()
            showOnboarding = false
        })
        return
    }

    // 3D Splash: beautiful modern launch animation (runs briefly on every open for brand impact)
    if (show3DSplash) {
        ThreeDSplash(onFinished = { show3DSplash = false })
        return
    }

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

    val isDark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ClearMeshBackground(darkTheme = isDark)
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = ClearColors.text,
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
            GlassHeader(currentScreen.title, isProtected, blockedTodayState.value.toInt())

            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    (fadeIn(tween(ClearDesign.screenFadeInMs)) +
                        slideInVertically(tween(ClearDesign.screenFadeInMs)) { it / 16 }) togetherWith
                        (fadeOut(tween(ClearDesign.screenFadeOutMs)) +
                            slideOutVertically(tween(ClearDesign.screenFadeOutMs)) { -it / 16 })
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
                            // Reload so the dedicated patterns take effect immediately
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

@Composable
fun GlassHeader(title: String, isProtected: Boolean, blockedToday: Int = 0) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ClearDesign.screenHPadding, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            // Use the official logo (small version) for strong brand presence in the modern header
            // The provided logo already includes the wordmark — this gives a clean, professional look
            Image(
                painter = painterResource(id = R.drawable.shield_dns_logo),
                contentDescription = "ShieldDNS",
                modifier = Modifier
                    .height(22.dp)
                    .padding(bottom = 2.dp),
                contentScale = ContentScale.Fit
            )
            Text(
                text = title,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClearColors.text
            )
        }

        // Status pill (glass)
        val infiniteTransition = rememberInfiniteTransition(label = "statusPill")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isProtected) ClearColors.green else ClearColors.danger)
                    )
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .border(
                                width = 1.5.dp,
                                color = (if (isProtected) ClearColors.green else ClearColors.danger)
                                    .copy(alpha = pulseAlpha),
                                shape = CircleShape
                            )
                    )
                }
                Text(
                    text = if (isProtected) "Protected" else "Paused",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = ClearColors.text
                )
                // Live blocked counter
                if (isProtected && blockedToday > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ClearColors.green.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "🛡️ $blockedToday",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ClearColors.green
                        )
                    }
                }
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
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        cornerRadius = ClearDesign.navCorner,
        glassAlpha = 0.92f,
        elevation = ClearDesign.navElevation
    ) {
        val selectedIndex = AppScreen.entries.indexOf(currentScreen)
        val haptic = LocalHapticFeedback.current

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp)
        ) {
            val tabWidth = maxWidth / AppScreen.entries.size
            val animatedOffset by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(dampingRatio = 0.76f, stiffness = 380f),
                label = "navSlide"
            )

            // Dynamic sliding glass capsule background behind icons
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(tabWidth)
                    .fillMaxHeight(0.85f)
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                ClearColors.green.copy(alpha = 0.15f),
                                ClearColors.green.copy(alpha = 0.05f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = ClearColors.green.copy(alpha = 0.28f),
                        shape = RoundedCornerShape(16.dp)
                    )
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppScreen.entries.forEach { screen ->
                    val selected = currentScreen == screen
                    val scale by animateFloatAsState(
                        targetValue = if (selected) ClearDesign.navSelectedScale else 1f,
                        animationSpec = tween(ClearDesign.pressFeedbackMs), label = "navScale"
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onScreenSelected(screen)
                            },
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
                        Box {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) ClearColors.green else ClearColors.muted
                            )
                            // Live activity dot on Privacy tab when VPN active
                            if (screen == AppScreen.Privacy && com.clearguard.app.vpn.ClearGuardVpnService.isRunning()) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-1).dp)
                                        .clip(CircleShape)
                                        .background(ClearColors.green)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern 3D Splash Effect - premium, visually striking launch experience.
 * Uses the official ShieldDNS logo you provided (with full 3D perspective transforms).
 *
 * === HOW TO ADD THE LOGO ASSET (IMPORTANT) ===
 * 1. Save/export the attached logo image.
 * 2. For best results on the 3D splash + onboarding:
 *    - Create a high-resolution PNG version of the full logo (including the "ShieldDNS" wordmark).
 *    - Place it in:
 *      app/src/main/res/drawable/shield_dns_logo.png   (or .webp for smaller size)
 *    - For different densities you can provide mdpi/hdpi/xhdpi/xxhdpi/xxxhdpi versions in the corresponding drawable-* folders.
 * 3. (Optional but recommended for launcher icon):
 *    - Crop/export just the shield graphic (no bottom text) as a square-ish foreground.
 *    - Replace or update res/drawable/ic_launcher_foreground.xml (or switch the adaptive icon to use PNGs in mipmap-*).
 * 4. After adding the file, rebuild. The splash, header, and onboarding will automatically pick it up with beautiful 3D rotation/perspective.
 *
 * The 3D transforms (rotationY, rotationX, cameraDistance) applied to the logo will give exactly the modern 3D splash effect you asked for.
 */
@Composable
fun ThreeDSplash(onFinished: () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "splash3d")
    
    // Continuous 3D rotation for the shield (primary "wow" effect)
    val rotY by infinite.animateFloat(
        initialValue = -22f, targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(4200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotY"
    )
    val rotX by infinite.animateFloat(
        initialValue = -8f, targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(5100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotX"
    )
    
    // Subtle breathing scale + extra spin layer for 3D volume
    val breath by infinite.animateFloat(
        initialValue = 0.96f, targetValue = 1.065f,
        animationSpec = infiniteRepeatable(
            animation = tween(1850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    
    // Orbiting accent particles around the 3D object (depth layers)
    val orbit by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )
    
    // Entrance animation
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
        delay(1850)
        onFinished()
    }
    
    val entranceScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.65f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 180f),
        label = "splashEntranceScale"
    )
    val entranceAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(620),
        label = "splashEntranceAlpha"
    )
    val entranceRot by animateFloatAsState(
        targetValue = if (entered) 0f else -38f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 140f),
        label = "splashRot"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ClearColors.bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFinished() },
        contentAlignment = Alignment.Center
    ) {
        // Reuse and enhance the beautiful animated mesh as the deep 3D environment
        ClearMeshBackground(darkTheme = isSystemInDarkTheme())

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer {
                    alpha = entranceAlpha
                    scaleX = entranceScale
                    scaleY = entranceScale
                    rotationZ = entranceRot * 0.3f
                }
        ) {
            // === THE STAR: True 3D Rotating Logo with depth (using your provided ShieldDNS logo) ===
            // Place the attached logo image in res/drawable as shield_dns_logo.png (or .xml vector)
            // Recommended: export the shield+text at 2-3x for crispness, or convert the shield graphic to vector.
            Box(
                modifier = Modifier
                    .size(200.dp)   // a bit larger to show the full logo including wordmark nicely under 3D transform
                    .graphicsLayer {
                        // Core 3D perspective transform — this gives the premium "3D splash effect"
                        rotationY = rotY
                        rotationX = rotX + (sin(orbit / 57f) * 5f)
                        cameraDistance = 10.5f   // pronounced 3D pop
                        scaleX = breath * 1.02f
                        scaleY = breath * 1.02f
                        // subtle perspective shear for extra depth
                        shadowElevation = 12f
                    },
                contentAlignment = Alignment.Center
            ) {
                // The official logo with full 3D treatment
                Image(
                    painter = painterResource(id = R.drawable.shield_dns_logo),
                    contentDescription = "ShieldDNS Logo",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // extra micro-tilt on the image itself for volume
                            rotationZ = sin(rotY / 90f) * 1.5f
                        },
                    contentScale = ContentScale.Fit
                )

                // Optional subtle glow / rim overlay to enhance the 3D glass feel on top of your logo art
                // (remove or tweak alpha if it conflicts with the provided artwork)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent,
                                    ClearColors.blue.copy(alpha = 0.06f)
                                ),
                                radius = 280f
                            )
                        )
                )
            }

            Spacer(Modifier.height(18.dp))

            // Tagline only — the official logo image above already includes the beautiful "ShieldDNS" wordmark
            Text(
                text = "PROTECTED • PRIVATE • POWERFUL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = ClearColors.muted,
                letterSpacing = 2.8.sp,
                modifier = Modifier
                    .graphicsLayer { alpha = 0.85f }
            )
        }

        // Orbiting 3D accent particles (adds motion and depth around the central 3D object)
        Canvas(
            modifier = Modifier
                .size(260.dp)
                .graphicsLayer { alpha = 0.7f }
        ) {
            val cx = size.width / 2
            val cy = size.height / 2 - 28f
            val r = 94f

            for (i in 0 until 5) {
                val angle = orbit + (i * 72f)
                val rad = Math.toRadians(angle.toDouble())
                val px = cx + (r * cos(rad)).toFloat()
                val py = cy + (r * sin(rad).toFloat() * 0.45f)   // squash for 3D ellipse orbit

                drawCircle(
                    color = if (i % 2 == 0) ClearColors.green.copy(alpha = 0.55f) else ClearColors.blue.copy(alpha = 0.4f),
                    radius = if (i == 0) 4.5f else 2.8f,
                    center = Offset(px, py)
                )
            }
        }

        // Bottom subtle tagline
        Text(
            text = "India-first privacy & scam shield",
            fontSize = 11.sp,
            color = ClearColors.muted.copy(alpha = 0.65f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52.dp)
        )
    }
}
