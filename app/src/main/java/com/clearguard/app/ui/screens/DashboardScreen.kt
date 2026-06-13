package com.clearguard.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.clearguard.app.R
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.GlassCardHero
import com.clearguard.app.ui.components.LiquidGlassButton
import com.clearguard.app.ui.components.LiquidGlassIconButton
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import kotlin.math.cos
import kotlin.math.sin

private data class ShieldProfile(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val accent: Color
)

@Composable
fun DashboardScreen(
    isProtected: Boolean,
    onToggleProtection: (Boolean) -> Unit,
    blockedToday: Int,
    totalBlocked: Int,
    cacheHits: Long,
    upstreamQueries: Long,
    scamShieldEnabled: Boolean,
    scamBlockedToday: Long,
    dohEnabled: Boolean
) {
    val animatedBlockedToday by animateIntAsState(
        targetValue = blockedToday,
        label = "blockedToday"
    )

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { PreferenceKeys.prefs(context) }
    var currentMode by remember { mutableStateOf(PreferenceKeys.getCurrentMode(context)) }

    val adaptiveThreatScore = remember(blockedToday, scamBlockedToday, cacheHits, upstreamQueries, isProtected, currentMode, scamShieldEnabled) {
        val base = ((blockedToday / 6) + (scamBlockedToday.toInt() * 3) + (cacheHits / 120L).toInt() + (upstreamQueries / 150L).toInt())
        val modeBoost = when (currentMode) {
            "elder" -> 10
            "kids" -> 8
            "shopping" -> 6
            "study", "work" -> 4
            "battery" -> 0
            else -> 2
        }
        val shieldBoost = if (isProtected) 8 else 0
        val scamBoost = if (scamShieldEnabled) 6 else 0
        (base + modeBoost + shieldBoost + scamBoost).coerceIn(0, 100)
    }

    val threatPulseLabel = when {
        adaptiveThreatScore >= 75 -> "Critical pulse"
        adaptiveThreatScore >= 50 -> "Elevated pulse"
        else -> "Calm pulse"
    }
    val threatPulseTone = when {
        adaptiveThreatScore >= 75 -> ClearColors.danger
        adaptiveThreatScore >= 50 -> ClearColors.warning
        else -> ClearColors.green
    }
    val threatPulseAdvice = when {
        adaptiveThreatScore >= 75 -> "Your session is under heavy scam pressure. Boost Elder or Shopping mode for extra guardrails."
        adaptiveThreatScore >= 50 -> "Ad traffic is active and scam signals are rising. Keep the shield on and use the smart mode coach."
        else -> "Protection looks steady. You can keep current mode for a lighter, faster browsing flow."
    }
    val smartModeSuggestion = when {
        currentMode == "elder" || adaptiveThreatScore >= 75 -> "Elder mode is ideal right now — it adds the strongest scam and fake-bank protection."
        currentMode == "shopping" || adaptiveThreatScore >= 55 -> "Shopping mode will help cut fake discounts, redirect chains, and tracker-heavy pages."
        currentMode == "kids" -> "Kids mode is tuned for safer, cleaner browsing with adult-content and scam filters."
        currentMode == "battery" -> "Battery mode keeps the stack light while still protecting your core DNS path."
        else -> "Default mode is balanced for everyday privacy protection."
    }

    val recommendedMode = when {
        adaptiveThreatScore >= 75 -> "elder"
        adaptiveThreatScore >= 55 -> "shopping"
        currentMode == "kids" -> "kids"
        else -> currentMode
    }

    val scoreReasons = listOfNotNull(
        if (blockedToday >= 120) "Live block volume is unusually high right now." else null,
        if (scamBlockedToday >= 4L) "Recent scam detections are pushing the threat meter upward." else null,
        if (!isProtected) "Protection is paused, which lowers the active shield level." else null,
        if (!scamShieldEnabled) "The scam shield toggle is currently off." else null,
        if (cacheHits > upstreamQueries) "Cache efficiency is helping keep the protection path smooth." else null,
        if (adaptiveThreatScore >= 55) "The current session looks more exposed to deceptive content and redirects." else null
    ).takeIf { it.isNotEmpty() } ?: listOf("Your setup is balanced, and the current mode is holding steady.")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ClearDesign.screenHPadding)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ClearDesign.cardSpacing)
    ) {
        // === HERO SECTION — central protection ring (ref: Vanda VPN glassmorphism) ===
        ProtectionRingHero(
            isProtected = isProtected,
            blockedToday = animatedBlockedToday,
            onToggle = { onToggleProtection(!isProtected) },
            onCreateProfile = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                Toast.makeText(
                    context,
                    "Profile presets are on the roadmap. Use the shield controls and settings to tailor protection today.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Adaptive Threat Pulse", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = ClearColors.text)
                Text(
                    text = "On-device threat intelligence that adapts to current session pressure and protection mode.",
                    fontSize = 11.sp,
                    color = ClearColors.muted,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Session risk", fontSize = 12.sp, color = ClearColors.muted)
                        Text(
                            text = adaptiveThreatScore.toString(),
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = threatPulseTone
                        )
                        Text(text = threatPulseLabel, fontSize = 11.sp, color = ClearColors.text)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(threatPulseTone.copy(alpha = 0.10f))
                            .border(1.dp, threatPulseTone.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(text = "Local AI pulse", fontSize = 11.sp, color = threatPulseTone, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ClearColors.border.copy(alpha = 0.25f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(adaptiveThreatScore / 100f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(Brush.horizontalGradient(listOf(threatPulseTone, ClearColors.blue)))
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(text = threatPulseAdvice, fontSize = 11.sp, color = ClearColors.text)
                Spacer(Modifier.height(8.dp))
                Text("Why this score?", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ClearColors.text)
                scoreReasons.forEach { reason ->
                    Text("• $reason", fontSize = 10.5.sp, color = ClearColors.muted, modifier = Modifier.padding(top = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("Smart mode coach", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ClearColors.text)
                Text(text = smartModeSuggestion, fontSize = 11.sp, color = ClearColors.muted)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LiquidGlassButton(
                        onClick = {
                            currentMode = recommendedMode
                            prefs.edit().putString(PreferenceKeys.KEY_PROTECTION_MODE, recommendedMode).apply()
                            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        accent = ClearColors.green,
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Use ${PreferenceKeys.modeDisplayName(recommendedMode)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    LiquidGlassButton(
                        onClick = {
                            currentMode = if (recommendedMode == "elder") "shopping" else "elder"
                            prefs.edit().putString(PreferenceKeys.KEY_PROTECTION_MODE, currentMode).apply()
                            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        accent = ClearColors.blue,
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (recommendedMode == "elder") "Try Shopping" else "Boost Elder", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ===== FLOATING PROTECTION PROFILES — directly inspired by the reference image =====
        // These now control **real** features in the app!
        Text(
            "Active Shields",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = ClearColors.text,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )

        // Ensure we have context/prefs for the cards (declared early for scope)
        val ctx = LocalContext.current
        val prfs = remember { PreferenceKeys.prefs(ctx) }

        // Live states read from real PreferenceKeys
        var adsEnabled by remember {
            mutableStateOf(
                prfs.getBoolean(
                    PreferenceKeys.KEY_AI_AD_PATTERN_DETECTOR_ENABLED,
                    PreferenceKeys.DEFAULT_AI_AD_PATTERN_DETECTOR_ENABLED
                )
            )
        }
        var familyEnabled by remember {
            mutableStateOf(PreferenceKeys.getCurrentMode(ctx) == "kids")
        }
        var trackersEnabled by remember {
            mutableStateOf(
                prfs.getBoolean(PreferenceKeys.KEY_BROWSER_ANTI_FINGERPRINT, PreferenceKeys.DEFAULT_BROWSER_ANTI_FINGERPRINT) &&
                prfs.getBoolean(PreferenceKeys.KEY_BROWSER_COOKIE_REMOVER, PreferenceKeys.DEFAULT_BROWSER_COOKIE_REMOVER)
            )
        }
        var gamingEnabled by remember {
            mutableStateOf(PreferenceKeys.getCurrentMode(ctx) == "battery")
        }
        var malwareEnabled by remember {
            mutableStateOf(
                prfs.getBoolean(PreferenceKeys.KEY_MOBILE_RISK_SCORING_ENABLED, PreferenceKeys.DEFAULT_MOBILE_RISK_SCORING_ENABLED) &&
                prfs.getBoolean(PreferenceKeys.KEY_RASP_ENABLED, PreferenceKeys.DEFAULT_RASP_ENABLED)
            )
        }
        var cryptoEnabled by remember {
            mutableStateOf(prfs.getBoolean(PreferenceKeys.KEY_RASP_ENABLED, PreferenceKeys.DEFAULT_RASP_ENABLED))
        }

        val profiles = listOf(
            ShieldProfile("Ads", Icons.Default.AdUnits, adsEnabled, ClearColors.green),
            ShieldProfile("Family", Icons.Default.FamilyRestroom, familyEnabled, ClearColors.blue),
            ShieldProfile("Trackers", Icons.Default.TrackChanges, trackersEnabled, ClearColors.green),
            ShieldProfile("Gaming", Icons.Default.SportsEsports, gamingEnabled, ClearColors.blue),
            ShieldProfile("Malware", Icons.Default.Security, malwareEnabled, Color(0xFF34D399)),
            ShieldProfile("Crypto-mining", Icons.Default.CurrencyBitcoin, cryptoEnabled, ClearColors.warning),
        )

        // Container with glowing connection lines (Canvas) + tilted floating cards
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) {
            // Glowing connection lines (futuristic network like the reference)
            Canvas(modifier = Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height

                // Approximate card centers (tuned for the staggered layout below)
                val positions = listOf(
                    Offset(w * 0.18f, h * 0.32f),
                    Offset(w * 0.42f, h * 0.22f),
                    Offset(w * 0.68f, h * 0.35f),
                    Offset(w * 0.22f, h * 0.72f),
                    Offset(w * 0.52f, h * 0.78f),
                    Offset(w * 0.78f, h * 0.65f),
                )

                for (i in positions.indices) {
                    for (j in i + 1 until positions.size) {
                        if ((i + j) % 2 == 0) { // sparse elegant connections
                            val p1 = positions[i]
                            val p2 = positions[j]
                            drawLine(
                                color = ClearColors.blue.copy(alpha = 0.18f),
                                start = p1,
                                end = p2,
                                strokeWidth = 1.5f
                            )
                            // subtle glow
                            drawLine(
                                color = ClearColors.green.copy(alpha = 0.08f),
                                start = p1,
                                end = p2,
                                strokeWidth = 3.5f
                            )
                        }
                    }
                }
            }

            // Staggered floating 3D-tilted cards
            val cardModifierBase = Modifier
                .width(108.dp)
                .height(78.dp)

            // Row 1 (top row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                profiles.take(3).forEachIndexed { index, profile ->
                    FloatingShieldCard(
                        profile = profile,
                        modifier = cardModifierBase.graphicsLayer {
                            rotationX = if (index == 1) -9f else 6f
                            rotationY = if (index == 0) 11f else -8f
                            translationY = if (index == 1) -6f else 4f
                            cameraDistance = 6f
                        },
                        onToggle = { enabled ->
                            when (index) {
                                0 -> { // Ads -> AI Ad Pattern Detector
                                    adsEnabled = enabled
                                    prfs.edit()
                                        .putBoolean(PreferenceKeys.KEY_AI_AD_PATTERN_DETECTOR_ENABLED, enabled)
                                        .apply()
                                    com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(ctx)
                                }
                                1 -> { // Family → Kids mode (real protection mode)
                                    familyEnabled = enabled
                                    val newMode = if (enabled) "kids" else "default"
                                    prfs.edit()
                                        .putString(PreferenceKeys.KEY_PROTECTION_MODE, newMode)
                                        .apply()
                                    com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(ctx)
                                }
                                2 -> { // Trackers → real browser privacy features
                                    trackersEnabled = enabled
                                    prfs.edit()
                                        .putBoolean(PreferenceKeys.KEY_BROWSER_ANTI_FINGERPRINT, enabled)
                                        .putBoolean(PreferenceKeys.KEY_BROWSER_COOKIE_REMOVER, enabled)
                                        .apply()
                                    // Browser features take effect on next WebView load
                                }
                            }
                        }
                    )
                }
            }

            // Row 2 (bottom row, offset)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 102.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                profiles.drop(3).forEachIndexed { index, profile ->
                    FloatingShieldCard(
                        profile = profile,
                        modifier = cardModifierBase.graphicsLayer {
                            rotationX = if (index == 1) 8f else -7f
                            rotationY = if (index == 0) -12f else 9f
                            translationY = if (index == 2) -8f else 2f
                            cameraDistance = 6f
                        },
                        onToggle = { enabled ->
                            when (index) {
                                0 -> { // Gaming → real Battery Saver mode
                                    gamingEnabled = enabled
                                    val newMode = if (enabled) "battery" else "default"
                                    prfs.edit()
                                        .putString(PreferenceKeys.KEY_PROTECTION_MODE, newMode)
                                        .apply()
                                    com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(ctx)
                                }
                                1 -> { // Malware → real Mobile Risk + RASP
                                    malwareEnabled = enabled
                                    prfs.edit()
                                        .putBoolean(PreferenceKeys.KEY_MOBILE_RISK_SCORING_ENABLED, enabled)
                                        .putBoolean(PreferenceKeys.KEY_RASP_ENABLED, enabled)
                                        .apply()
                                    com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(ctx)
                                }
                                2 -> { // Crypto-mining → RASP + real block rules for miners
                                    cryptoEnabled = enabled
                                    prfs.edit()
                                        .putBoolean(PreferenceKeys.KEY_RASP_ENABLED, enabled)
                                        .apply()
                                    if (enabled) {
                                        // Add known crypto-mining domains to security blocks (real effect)
                                        listOf(
                                            "xmrpool.net", "supportxmr.com", "minemonero.com",
                                            "pool.minexmr.com", "cryptonight.net", "xmr.nanopool.org"
                                        ).forEach { domain ->
                                            PreferenceKeys.addToStringSet(ctx, PreferenceKeys.KEY_SECURITY_BLOCKS, domain)
                                        }
                                    }
                                    com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(ctx)
                                }
                            }
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatGlassCard(
                modifier = Modifier.weight(1f),
                label = "Blocked Today",
                value = animatedBlockedToday.toString(),
                accent = ClearColors.green
            )
            StatGlassCard(
                modifier = Modifier.weight(1f),
                label = "Active Rules",
                value = formatLargeNumber(totalBlocked),
                accent = ClearColors.blue
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatGlassCard(
                modifier = Modifier.weight(1f),
                label = "Threat Blocks",
                value = if (scamShieldEnabled) formatCompact(scamBlockedToday) else "Off",
                accent = ClearColors.green
            )
            StatGlassCard(
                modifier = Modifier.weight(1f),
                label = "Cache Shield",
                value = cacheEfficiency(cacheHits, upstreamQueries),
                accent = ClearColors.blue
            )
        }

        // ===== Intent-Based Blocking: Full Protection Modes (user vision) =====
        Text("Protection Mode", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ClearColors.text)
        
        // Exact modes from the product vision (Study / Work / Kids / Elder / Shopping / Spiritual / Battery Saver)
        val visionModes = listOf(
            "default" to Icons.Default.Shield,
            "study" to Icons.Default.MenuBook,
            "work" to Icons.Default.Work,
            "kids" to Icons.Default.ChildCare,
            "elder" to Icons.Default.Elderly,
            "shopping" to Icons.Default.ShoppingCart,
            "spiritual" to Icons.Default.SelfImprovement,
            "battery" to Icons.Default.BatterySaver
        )
        
        Column {
            // First row of 4
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visionModes.take(4).forEach { (modeId, icon) ->
                    ModePill(
                        modeId = modeId,
                        label = PreferenceKeys.modeDisplayName(modeId),
                        icon = icon,
                        selected = currentMode == modeId,
                        onSelect = {
                            currentMode = modeId
                            prefs.edit().putString(PreferenceKeys.KEY_PROTECTION_MODE, modeId).apply()
                            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
                        }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            // Second row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visionModes.drop(4).forEach { (modeId, icon) ->
                    ModePill(
                        modeId = modeId,
                        label = PreferenceKeys.modeDisplayName(modeId),
                        icon = icon,
                        selected = currentMode == modeId,
                        onSelect = {
                            currentMode = modeId
                            prefs.edit().putString(PreferenceKeys.KEY_PROTECTION_MODE, modeId).apply()
                            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
                        }
                    )
                }
            }
        }
        
        // Dynamic description from PreferenceKeys (localized to vision)
        Text(
            text = PreferenceKeys.modeDescription(currentMode),
            fontSize = 11.sp,
            color = ClearColors.muted,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp)
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            val context = LocalContext.current
            val prefs = remember { PreferenceKeys.prefs(context) }
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "System Status",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = ClearColors.text
                )
                Spacer(Modifier.height(12.dp))
                val threatShieldStatus = when (currentMode) {
                    "kids", "elder" -> "Maximum (Kids / Elder Safe Search)"
                    "spiritual" -> "Satvik / Dharma Clean + Scam Shield"
                    "study", "work" -> "Focus (Distraction + Scam)"
                    "shopping" -> "Deal Protection + Trackers"
                    "battery" -> "Battery Optimized (Minimal DoH)"
                    else -> if (scamShieldEnabled) {
                        "On-device (scam + DGA)"
                    } else {
                        "Basic (Ads + Trackers)"
                    }
                }
                val secureDnsStatus = when (currentMode) {
                    "gaming" -> "Cloudflare DoH (Enforced)"
                    "battery" -> "Classic (plaintext) (Enforced)"
                    else -> if (dohEnabled) "DoH encrypted" else "Classic (plaintext)"
                }
                val cacheTtlSeconds = when (currentMode) {
                    "gaming" -> 1800
                    "battery" -> 3600
                    else -> prefs.getInt(PreferenceKeys.KEY_CACHE_TTL_SECONDS, PreferenceKeys.DEFAULT_CACHE_TTL_SECONDS)
                }
                val cacheTtlText = if (cacheTtlSeconds >= 3600) {
                    "${cacheTtlSeconds / 3600} hr cache"
                } else {
                    "${cacheTtlSeconds / 60} min cache"
                }

                StatusRow("DNS Filtering", if (isProtected) "Active" else "Paused")
                StatusRow("Threat Shield", threatShieldStatus)
                if (PreferenceKeys.isIndianScamShieldEnabled(context)) {
                    StatusRow("Indian Scam Shield", "Active — 11 India-specific scam types protected")
                }
                StatusRow("Secure DNS", secureDnsStatus)
                StatusRow("Local Cache", cacheTtlText)
                StatusRow(
                    "Bypass Guard",
                    if (prefs.getBoolean(
                            PreferenceKeys.KEY_BYPASS_GUARD_ENABLED,
                            PreferenceKeys.DEFAULT_BYPASS_GUARD_ENABLED
                        )
                    ) "Blocking DNS sidesteps" else "Off"
                )
                StatusRow(
                    "List Updates",
                    if (prefs.getBoolean(
                            PreferenceKeys.KEY_AUTO_UPDATE_ENABLED,
                            PreferenceKeys.DEFAULT_AUTO_UPDATE_ENABLED
                        )
                    ) "Automatic (daily)" else "Manual only"
                )
                StatusRow("Telemetry", "None")

                // Gamification hint (Privacy Challenge)
                val blockedTodayVal = blockedToday
                val gamifyIcon = when {
                    blockedTodayVal > 5000 -> "🔥"
                    blockedTodayVal > 1500 -> "🥇"
                    blockedTodayVal > 400 -> "🥈"
                    else -> "🛡️"
                }
                val gamifyLabel = when {
                    blockedTodayVal > 5000 -> "Legendary streak — top tier privacy this session"
                    blockedTodayVal > 1500 -> "Gold level — excellent clean browsing"
                    blockedTodayVal > 400 -> "Silver — good momentum, keep the shield on"
                    else -> "Every block counts — data & attention protected"
                }
                val gamifyColor = when {
                    blockedTodayVal > 5000 -> Color(0xFFFF6B35)
                    blockedTodayVal > 1500 -> Color(0xFFFFD700)
                    blockedTodayVal > 400 -> Color(0xFFC0C0C0)
                    else -> ClearColors.green
                }
                Spacer(Modifier.height(10.dp))
                // Badge chip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(gamifyColor.copy(alpha = 0.10f))
                        .border(
                            width = 1.dp,
                            color = gamifyColor.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "$gamifyIcon  $gamifyLabel",
                        fontSize = 11.sp,
                        color = gamifyColor,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Quick access hint for Screenshot Scanner (part of Indian Scam Shield)
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(ClearColors.blue.copy(alpha = 0.08f))
                        .border(
                            width = 1.dp,
                            color = ClearColors.blue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        "📸  Privacy tab → Scanner to check suspicious screenshots",
                        fontSize = 11.sp,
                        color = ClearColors.blue.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureChip(label: String, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = accent,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun RowScope.ModePill(
    modeId: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // Animated scale bounce on selection
    val pillScale by animateFloatAsState(
        targetValue = if (selected) ClearDesign.pillSelectedScale else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "pillScale"
    )
    val pillBg by animateColorAsState(
        targetValue = if (selected) ClearColors.green.copy(alpha = 0.18f) else ClearColors.bg.copy(alpha = 0.2f),
        animationSpec = tween(220),
        label = "pillBg"
    )
    val pillBorder by animateColorAsState(
        targetValue = if (selected) ClearColors.green.copy(alpha = 0.38f) else ClearColors.border.copy(alpha = 0.12f),
        animationSpec = tween(220),
        label = "pillBorder"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(58.dp)
            .graphicsLayer {
                scaleX = pillScale
                scaleY = pillScale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(pillBg)
            .border(
                width = 1.dp,
                color = pillBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onSelect()
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = if (selected) ClearColors.green else ClearColors.muted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(1.dp))
            Text(
                text = label.replace(" Mode", "").replace(" (Safe Search)", ""),
                fontSize = 9.sp,
                color = if (selected) ClearColors.green else ClearColors.text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatGlassCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Color
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(ClearColors.text, accent)
                    )
                )
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = ClearColors.muted
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .fillMaxWidth(0.5f)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            listOf(accent, accent.copy(alpha = 0.15f))
                        )
                    )
            )
        }
    }
}

/**
 * Hero surface: a central "protection ring" framing the ShieldDNS logo, with the live
 * status, feature chips, and the primary toggle below. Inspired by the Vanda VPN
 * glassmorphism reference; built entirely from existing design tokens/components.
 *
 * The ring animates its sweep on/off with protection state. Height is content-driven so
 * it adapts to font scaling (the old hero was a cramped fixed 260dp with three overlapping
 * alignments).
 */
@Composable
private fun ProtectionRingHero(
    isProtected: Boolean,
    blockedToday: Int,
    onToggle: () -> Unit,
    onCreateProfile: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val sweep by animateFloatAsState(
        targetValue = if (isProtected) 1f else 0.12f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ringSweep"
    )
    val ringStart = if (isProtected) ClearColors.green else ClearColors.muted
    val ringEnd = if (isProtected) ClearColors.blue else ClearColors.muted

    GlassCardHero(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.size(168.dp), contentAlignment = Alignment.Center) {
                val track = ClearColors.glassBorder.copy(alpha = 0.25f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 12.dp.toPx()
                    val arc = Size(size.width - stroke, size.height - stroke)
                    val tl = Offset(stroke / 2f, stroke / 2f)
                    drawArc(
                        color = track,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = tl,
                        size = arc,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = Brush.sweepGradient(listOf(ringStart, ringEnd, ringStart)),
                        startAngle = -90f,
                        sweepAngle = 360f * sweep,
                        useCenter = false,
                        topLeft = tl,
                        size = arc,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
                if (isProtected) {
                    Box(
                        modifier = Modifier
                            .size(128.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    listOf(ClearColors.blue.copy(alpha = 0.22f), Color.Transparent)
                                )
                            )
                    )
                }
                FloatingParticles(
                    isProtected = isProtected,
                    modifier = Modifier.matchParentSize()
                )
                Image(
                    painter = painterResource(id = R.drawable.shield_dns_logo),
                    contentDescription = "ShieldDNS",
                    modifier = Modifier.size(84.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = if (isProtected) "Protected" else "Paused",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isProtected) ClearColors.green else ClearColors.muted
            )
            Text(
                text = if (isProtected) "$blockedToday threats & ads blocked today"
                    else "Tap below to resume protection",
                fontSize = 13.sp,
                color = ClearColors.muted
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FeatureChip("Zero cloud", ClearColors.blue)
                FeatureChip("Local AI", ClearColors.green)
                FeatureChip("Fast DNS", ClearColors.warning)
            }

            Spacer(Modifier.height(20.dp))
            LiquidGlassButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                accent = if (isProtected) ClearColors.green else ClearColors.blue,
                contentColor = Color.White
            ) {
                Text(
                    text = if (isProtected) "Pause Protection" else "Get Started",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Create Profile",
                fontSize = 13.sp,
                color = ClearColors.muted,
                modifier = Modifier.clickable { onCreateProfile() }
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Status indicator dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            value.startsWith("Active") || value.startsWith("On") || value.startsWith("DoH") || value.startsWith("Auto") || value.startsWith("Block") || value.startsWith("Maximum") || value.startsWith("Satvik") || value.startsWith("Focus") || value.startsWith("Deal") -> ClearColors.success
                            value == "None" || value == "Paused" || value == "Off" || value.startsWith("Manual") || value.startsWith("Classic") -> ClearColors.muted
                            else -> ClearColors.blue
                        }
                    )
            )
            Text(label, color = ClearColors.muted, fontSize = 13.sp)
        }
        Text(value, color = ClearColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

private fun formatLargeNumber(num: Int): String {
    return when {
        num >= 1_000_000 -> String.format("%.1fM", num / 1_000_000f)
        num >= 10_000 -> "${num / 1000}k"
        else -> num.toString()
    }
}

private fun cacheEfficiency(cacheHits: Long, upstreamQueries: Long): String {
    val total = cacheHits + upstreamQueries
    if (total <= 0L) {
        return "Learning"
    }
    return String.format("%.0f%%", (cacheHits * 100f) / total)
}

private fun formatCompact(value: Long): String {
    return when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f)
        value >= 10_000 -> "${value / 1000}k"
        else -> value.toString()
    }
}

@Composable
private fun FloatingParticles(isProtected: Boolean, modifier: Modifier = Modifier) {
    if (!isProtected) return

    val infiniteTransition = rememberInfiniteTransition(label = "particles")

    val p1Progress by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "p1"
    )
    val p2Progress by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3400, delayMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "p2"
    )
    val p3Progress by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "p3"
    )
    val p4Progress by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, delayMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "p4"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val activeColor = ClearColors.green

        // Particle 1 (left side)
        drawCircle(
            color = activeColor.copy(alpha = p1Progress * 0.25f),
            radius = 5.dp.toPx(),
            center = Offset(w * 0.24f, h * 0.15f + h * 0.7f * p1Progress)
        )
        // Particle 2 (right-middle)
        drawCircle(
            color = activeColor.copy(alpha = p2Progress * 0.20f),
            radius = 4.dp.toPx(),
            center = Offset(w * 0.76f, h * 0.15f + h * 0.7f * p2Progress)
        )
        // Particle 3 (middle-left)
        drawCircle(
            color = activeColor.copy(alpha = p3Progress * 0.28f),
            radius = 7.dp.toPx(),
            center = Offset(w * 0.36f, h * 0.15f + h * 0.7f * p3Progress)
        )
        // Particle 4 (middle-right)
        drawCircle(
            color = activeColor.copy(alpha = p4Progress * 0.22f),
            radius = 5.dp.toPx(),
            center = Offset(w * 0.64f, h * 0.15f + h * 0.7f * p4Progress)
        )
    }
}

/**
 * Floating 3D-tilted glass card matching the reference aesthetic:
 * dark frosted glass, subtle glow, icon + label, optional switch.
 * Used for the "Active Shields" / profile cards with network connections.
 */
@Composable
private fun FloatingShieldCard(
    profile: ShieldProfile,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    GlassCard(
        modifier = modifier
            .clickable { onToggle(!profile.enabled) },
        cornerRadius = 18.dp,
        glassAlpha = 0.82f,
        elevation = 14.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = profile.icon,
                    contentDescription = null,
                    tint = profile.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = profile.label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ClearColors.text,
                    maxLines = 1
                )
            }

            // Small toggle switch (reference style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (profile.enabled) "On" else "Off",
                    fontSize = 9.sp,
                    color = if (profile.enabled) profile.accent else ClearColors.muted
                )

                // Mini switch using the existing ClearSwitch style but compact
                Box(
                    modifier = Modifier
                        .size(width = 28.dp, height = 15.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (profile.enabled) profile.accent.copy(alpha = 0.25f)
                            else ClearColors.muted.copy(alpha = 0.2f)
                        )
                        .clickable { onToggle(!profile.enabled) },
                    contentAlignment = if (profile.enabled) Alignment.CenterEnd else Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .offset(x = if (profile.enabled) (-2).dp else 2.dp)
                            .clip(CircleShape)
                            .background(if (profile.enabled) profile.accent else ClearColors.muted)
                    )
                }
            }
        }
    }
}
