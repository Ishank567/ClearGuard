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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.GlassCardHero
import com.clearguard.app.ui.components.LiquidGlassIconButton
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign

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

    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ClearDesign.screenHPadding)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ClearDesign.cardSpacing)
    ) {
        GlassCardHero(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleProtection(!isProtected)
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Accent crossfades between active green and paused gray.
                val accent by animateColorAsState(
                    targetValue = if (isProtected) ClearColors.green else ClearColors.muted,
                    animationSpec = tween(350),
                    label = "heroAccent"
                )

                // 3D layered toggle button
                val scale by animateFloatAsState(
                    targetValue = if (isProtected) 1.0f else 0.96f,
                    label = "toggleScale"
                )

                // Multi-layered breathing halos while protection is active.
                val haloPulse = rememberInfiniteTransition(label = "protectedPulse")
                val haloScale1 by haloPulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.14f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1600, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "haloScale1"
                )
                val haloScale2 by haloPulse.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.28f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "haloScale2"
                )

                Box(contentAlignment = Alignment.Center) {
                    // Outer pulsing glow halo (only when protected)
                    if (isProtected) {
                        Box(
                            modifier = Modifier
                                .size(112.dp)
                                .graphicsLayer {
                                    scaleX = haloScale2
                                    scaleY = haloScale2
                                }
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.08f))
                        )
                    }
                    // Inner pulsing halo
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .graphicsLayer {
                                val s = if (isProtected) haloScale1 else 1f
                                scaleX = s
                                scaleY = s
                            }
                            .clip(CircleShape)
                            .background(accent.copy(alpha = if (isProtected) 0.16f else 0.10f))
                    )
                    if (isProtected) {
                        FloatingParticles(
                            isProtected = true,
                            modifier = Modifier.size(140.dp)
                        )
                    }
                    LiquidGlassIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggleProtection(!isProtected)
                        },
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                        accent = accent,
                        contentColor = accent,
                        size = 94.dp
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Protection",
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = if (isProtected) "DNS SHIELD ACTIVE" else "DNS SHIELD PAUSED",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Text(
                    text = if (isProtected) "Tap to pause blocking" else "Tap to resume DNS blocking",
                    fontSize = 12.sp,
                    color = ClearColors.muted
                )
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
        
        val context = LocalContext.current
        val prefs = remember { PreferenceKeys.prefs(context) }
        var currentMode by remember {
            mutableStateOf(PreferenceKeys.getCurrentMode(context))
        }
        
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
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, top = 2.dp)
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
                    StatusRow("Indian Scam Shield", "Active — 9 India-specific scam types protected")
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
private fun ModePill(
    modeId: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onSelect: () -> Unit
) {
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
