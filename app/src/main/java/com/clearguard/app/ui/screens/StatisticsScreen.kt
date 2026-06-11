package com.clearguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.theme.ClearColors
import java.util.Locale

@Composable
fun StatisticsScreen(
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
    val cacheEfficiency = cacheEfficiencyRatio(cacheHits, upstreamQueries)
    val avoidedDnsTrips = blockedTotal + cacheHits
    val securityScore = securityScore(
        scamShieldEnabled = scamShieldEnabled,
        blocked = blockedTotal,
        allowed = allowedTotal,
        cacheEfficiency = cacheEfficiency,
        latencyMs = upstreamAverageLatencyMs,
        dohEnabled = dohEnabled
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Blocked",
                value = formatNumber(blockedTotal),
                accent = true
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Allowed",
                value = formatNumber(allowedTotal),
                accent = false
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Blocked Today",
                value = formatNumber(blockedToday),
                accent = true
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Block Rate",
                value = blockRate(blockedTotal, allowedTotal),
                accent = false
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Cache Hits",
                value = formatNumber(cacheHits),
                accent = false
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Resolver RTT",
                value = latencyLabel(upstreamAverageLatencyMs),
                accent = true
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Encrypted (DoH)",
                value = formatNumber(dohQueries),
                accent = dohEnabled
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Secure DNS",
                value = if (dohEnabled) "On" else "Off",
                accent = dohEnabled
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Threat Blocks",
                value = formatNumber(scamBlocked),
                accent = true
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Security Score",
                value = "$securityScore",
                accent = scamShieldEnabled
            )
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Protection Intelligence", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                InsightRow("Cache efficiency", percentLabel(cacheEfficiency))
                MeterBar(progress = cacheEfficiency, color = ClearColors.blue)
                Spacer(Modifier.height(12.dp))
                InsightRow("DNS trips avoided", formatNumber(avoidedDnsTrips))
                InsightRow("Upstream lookups", formatNumber(upstreamQueries))
                InsightRow("Average resolver time", latencyLabel(upstreamAverageLatencyMs))
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Security Posture", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                InsightRow("Threat Shield (Scam + DGA)", if (scamShieldEnabled) "Active" else "Off")
                InsightRow("Encrypted DNS (DoH)", if (dohEnabled) "Active" else "Off")
                MeterBar(progress = securityScore / 100f, color = ClearColors.green)
                Spacer(Modifier.height(12.dp))
                InsightRow("On-device heuristics", if (scamShieldEnabled) "Phishing + DGA entropy" else "Disabled")
                InsightRow("Query encryption", if (dohEnabled) "HTTPS to trusted provider" else "Plaintext (classic)")
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Local Counters", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "ClearGuard keeps aggregate DNS counts only. Recent blocked domains remain in memory and are cleared when protection stops.",
                    color = ClearColors.muted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Updated from on-device state",
            fontSize = 10.sp,
            color = ClearColors.muted,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun InsightRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = ClearColors.muted, fontSize = 13.sp)
        Text(value, color = ClearColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MeterBar(progress: Float, color: Color) {
    val boundedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(ClearColors.border.copy(alpha = 0.55f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(boundedProgress)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color.copy(alpha = 0.78f))
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    accent: Boolean
) {
    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, color = ClearColors.muted, fontSize = 12.sp)
            Text(
                value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (accent) ClearColors.green else ClearColors.text
            )
        }
    }
}

private fun formatNumber(n: Long): String {
    return when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fm", n / 1_000_000f)
        n >= 10_000 -> "${n / 1000}k"
        else -> String.format(Locale.US, "%,d", n)
    }
}

private fun blockRate(blocked: Long, allowed: Long): String {
    val total = blocked + allowed
    if (total <= 0L) {
        return "—"
    }
    val percent = (blocked * 100f) / total
    return String.format(Locale.US, "%.0f%%", percent)
}

private fun cacheEfficiencyRatio(cacheHits: Long, upstreamQueries: Long): Float {
    val total = cacheHits + upstreamQueries
    if (total <= 0L) {
        return 0f
    }
    return cacheHits.toFloat() / total
}

private fun percentLabel(ratio: Float): String {
    if (ratio <= 0f) {
        return "Learning"
    }
    return String.format(Locale.US, "%.0f%%", ratio * 100f)
}

private fun latencyLabel(latencyMs: Float): String {
    if (latencyMs <= 0f) {
        return "—"
    }
    if (latencyMs >= 1000f) {
        return String.format(Locale.US, "%.1f s", latencyMs / 1000f)
    }
    return String.format(Locale.US, "%.0f ms", latencyMs)
}

private fun securityScore(
    scamShieldEnabled: Boolean,
    blocked: Long,
    allowed: Long,
    cacheEfficiency: Float,
    latencyMs: Float,
    dohEnabled: Boolean
): Int {
    var score = 40
    if (scamShieldEnabled) {
        score += 25
    }
    if (dohEnabled) {
        score += 20
    }
    if (blocked + allowed > 0L) {
        score += 10
    }
    score += (cacheEfficiency.coerceIn(0f, 1f) * 8f).toInt()
    if (latencyMs in 1f..350f) {
        score += 7
    }
    return score.coerceIn(0, 100)
}
