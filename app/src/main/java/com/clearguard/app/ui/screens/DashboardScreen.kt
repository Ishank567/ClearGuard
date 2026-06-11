package com.clearguard.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                // 3D layered toggle button
                val scale by animateFloatAsState(
                    targetValue = if (isProtected) 1.0f else 0.96f,
                    label = "toggleScale"
                )

                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(
                            if (isProtected)
                                ClearColors.green.copy(alpha = 0.15f)
                            else
                                ClearColors.muted.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    LiquidGlassIconButton(
                        onClick = { onToggleProtection(!isProtected) },
                        accent = if (isProtected) ClearColors.green else ClearColors.muted,
                        contentColor = if (isProtected) ClearColors.green else ClearColors.muted,
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
                    color = if (isProtected) ClearColors.green else ClearColors.muted
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

        GlassCard(
            modifier = Modifier.fillMaxWidth()
        ) {
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
                StatusRow("Traffic Route", "DNS only")
                StatusRow("Telemetry", "None")
                StatusRow("Background Jobs", "None")
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
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = ClearColors.text
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
                    .fillMaxWidth(0.6f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent.copy(alpha = 0.65f))
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
