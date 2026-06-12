package com.clearguard.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.GlassCardHero
import com.clearguard.app.ui.components.LiquidGlassIconButton
import com.clearguard.app.ui.theme.ClearColors

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        GlassCardHero(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clickable { onToggleProtection(!isProtected) }
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
                        onClick = { onToggleProtection(!isProtected) },
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

        // One-tap security modes selector
        Text("Security Profiles", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = ClearColors.text)
        
        val context = LocalContext.current
        val prefs = remember { PreferenceKeys.prefs(context) }
        var currentMode by remember {
            mutableStateOf(prefs.getString(PreferenceKeys.KEY_SECURITY_MODE, PreferenceKeys.DEFAULT_SECURITY_MODE) ?: "strict")
        }
        
        val modes = listOf(
            Triple("basic", "Basic", Icons.Default.Verified),
            Triple("strict", "Strict", Icons.Default.Security),
            Triple("family", "Family", Icons.Default.ChildCare),
            Triple("gaming", "Gaming", Icons.Default.Gamepad),
            Triple("battery", "Saver", Icons.Default.BatteryChargingFull)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            modes.forEach { (modeId, label, icon) ->
                val selected = currentMode == modeId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) ClearColors.green.copy(alpha = 0.18f) else ClearColors.bg.copy(alpha = 0.25f))
                        .border(
                            width = 1.dp,
                            color = if (selected) ClearColors.green.copy(alpha = 0.35f) else ClearColors.border.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            currentMode = modeId
                            prefs.edit().putString(PreferenceKeys.KEY_SECURITY_MODE, modeId).apply()
                            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, contentDescription = null, tint = if (selected) ClearColors.green else ClearColors.muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(2.dp))
                        Text(label, fontSize = 10.sp, color = if (selected) ClearColors.green else ClearColors.text, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        
        // Mode description card
        val modeDesc = when (currentMode) {
            "basic" -> "Blocks standard advertisements and telemetry trackers."
            "strict" -> "Strict protection. Blocks social widgets, fingerprinters and scam domains."
            "family" -> "Safe for kids. Blocks adult sites, gambling, and short-video apps (TikTok)."
            "gaming" -> "Optimized for latency. Uses Cloudflare DoH and caches DNS for 30 minutes."
            "battery" -> "Optimized for battery. Disables DoH encryption, caches DNS queries for 1 hour."
            else -> ""
        }
        Text(
            text = modeDesc,
            fontSize = 11.sp,
            color = ClearColors.muted,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
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
                StatusRow("DNS Filtering", if (isProtected) "Active" else "Paused")
                StatusRow("Threat Shield", if (scamShieldEnabled) "On-device (scam + DGA)" else "Off")
                StatusRow("Secure DNS", if (dohEnabled) "DoH encrypted" else "Classic (plaintext)")
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
                StatusRow("Traffic Route", "DNS only")
                StatusRow("Telemetry", "None")
            }
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
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = ClearColors.muted, fontSize = 13.sp)
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
