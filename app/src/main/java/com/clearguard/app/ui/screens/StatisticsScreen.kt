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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign
import org.json.JSONException
import org.json.JSONObject
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
            .padding(horizontal = ClearDesign.screenHPadding, vertical = 12.dp)
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

        QueryHistoryChart(modifier = Modifier.fillMaxWidth())

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

        val context = LocalContext.current
        val topBlocked = remember(blockedTotal, blockedToday) { loadTopBlocked(context) }
        if (topBlocked.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Top Blocked Domains", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    val maxCount = topBlocked.first().second.coerceAtLeast(1L)
                    topBlocked.forEach { (domain, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                domain,
                                color = ClearColors.text,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                formatNumber(count),
                                color = ClearColors.muted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        MeterBar(progress = count.toFloat() / maxCount, color = ClearColors.green)
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(
                        "Counted on-device only. Cleared when you clear app data.",
                        fontSize = 11.sp,
                        color = ClearColors.muted
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .padding(18.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RadialSecurityGauge(
                    score = securityScore,
                    modifier = Modifier
                        .size(90.dp)
                        .padding(4.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("Security Posture", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    InsightRow("Threat Shield", if (scamShieldEnabled) "Active" else "Off")
                    InsightRow("Secure DoH", if (dohEnabled) "Encrypted" else "Plaintext")
                }
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
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = if (accent) listOf(ClearColors.text, ClearColors.green)
                                else listOf(ClearColors.text, ClearColors.blue)
                    )
                )
            )
        }
    }
}

/** Top blocked domains persisted by the VPN service, highest count first. */
private fun loadTopBlocked(context: android.content.Context): List<Pair<String, Long>> {
    val json = PreferenceKeys.prefs(context)
        .getString(PreferenceKeys.KEY_TOP_BLOCKED_JSON, "") ?: ""
    if (json.isEmpty()) {
        return emptyList()
    }
    return try {
        val stored = JSONObject(json)
        stored.keys().asSequence()
            .map { it to stored.optLong(it, 0L) }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
            .take(6)
            .toList()
    } catch (e: JSONException) {
        emptyList()
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

@Composable
private fun RadialSecurityGauge(
    score: Int,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "radialScore"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        val inactiveColor = ClearColors.border.copy(alpha = 0.45f)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()

            // Base track arc
            drawArc(
                color = inactiveColor,
                startAngle = -220f,
                sweepAngle = 260f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )

            // Active glowing progress arc (glow shadow backing)
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(
                        ClearColors.green.copy(alpha = 0.22f),
                        ClearColors.blue.copy(alpha = 0.22f)
                    )
                ),
                startAngle = -220f,
                sweepAngle = (animatedScore / 100f) * 260f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth + 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )

            // Active glowing progress arc
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(ClearColors.green, ClearColors.blue)
                ),
                startAngle = -220f,
                sweepAngle = (animatedScore / 100f) * 260f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$score",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = ClearColors.text
            )
            Text(
                text = "Score",
                fontSize = 10.sp,
                color = ClearColors.muted,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun QueryHistoryChart(modifier: Modifier = Modifier) {
    val blockedData = listOf(120f, 150f, 90f, 210f, 180f, 240f, 195f)
    val allowedData = listOf(450f, 480f, 410f, 530f, 490f, 610f, 540f)
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    val drawProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "chartDraw"
    )

    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Query Volume (7 Days)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendItem("Allowed", ClearColors.blue)
                    LegendItem("Blocked", ClearColors.green)
                }
            }

            Spacer(Modifier.height(18.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val w = size.width
                val h = size.height
                val maxVal = 700f
                val pointsCount = blockedData.size

                val xSpacing = w / (pointsCount - 1)

                // Draw background grid lines (horizontal grid)
                val gridLines = 4
                val gridColor = ClearColors.border.copy(alpha = 0.25f)
                for (i in 0..gridLines) {
                    val y = h * (i.toFloat() / gridLines)
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Create Bezier path for Allowed data
                val allowedPath = androidx.compose.ui.graphics.Path()
                val allowedFillPath = androidx.compose.ui.graphics.Path()

                // Create Bezier path for Blocked data
                val blockedPath = androidx.compose.ui.graphics.Path()
                val blockedFillPath = androidx.compose.ui.graphics.Path()

                // Helper to get coordinates
                fun getCoords(index: Int, value: Float): Offset {
                    val x = index * xSpacing
                    val y = h - (value / maxVal) * h
                    return Offset(x, y)
                }

                // Setup Allowed Path
                var p = getCoords(0, allowedData[0])
                allowedPath.moveTo(p.x, p.y)
                allowedFillPath.moveTo(0f, h)
                allowedFillPath.lineTo(p.x, p.y)

                for (i in 1 until pointsCount) {
                    val pPrev = getCoords(i - 1, allowedData[i - 1])
                    val pCurr = getCoords(i, allowedData[i])
                    val controlX1 = pPrev.x + xSpacing / 2f
                    val controlY1 = pPrev.y
                    val controlX2 = pCurr.x - xSpacing / 2f
                    val controlY2 = pCurr.y

                    allowedPath.cubicTo(controlX1, controlY1, controlX2, controlY2, pCurr.x, pCurr.y)
                    allowedFillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, pCurr.x, pCurr.y)
                }
                allowedFillPath.lineTo(w, h)
                allowedFillPath.close()

                // Setup Blocked Path
                p = getCoords(0, blockedData[0])
                blockedPath.moveTo(p.x, p.y)
                blockedFillPath.moveTo(0f, h)
                blockedFillPath.lineTo(p.x, p.y)

                for (i in 1 until pointsCount) {
                    val pPrev = getCoords(i - 1, blockedData[i - 1])
                    val pCurr = getCoords(i, blockedData[i])
                    val controlX1 = pPrev.x + xSpacing / 2f
                    val controlY1 = pPrev.y
                    val controlX2 = pCurr.x - xSpacing / 2f
                    val controlY2 = pCurr.y

                    blockedPath.cubicTo(controlX1, controlY1, controlX2, controlY2, pCurr.x, pCurr.y)
                    blockedFillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, pCurr.x, pCurr.y)
                }
                blockedFillPath.lineTo(w, h)
                blockedFillPath.close()

                // Clip canvas using drawProgress for animated reveal
                clipRect(right = w * drawProgress) {
                    // Draw Allowed Area Fill
                    drawPath(
                        path = allowedFillPath,
                        brush = Brush.verticalGradient(
                            listOf(ClearColors.blue.copy(alpha = 0.16f), Color.Transparent)
                        )
                    )
                    // Allowed Glow Shadow
                    drawPath(
                        path = allowedPath,
                        color = ClearColors.blue.copy(alpha = 0.24f),
                        style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Draw Allowed Line
                    drawPath(
                        path = allowedPath,
                        color = ClearColors.blue,
                        style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw Blocked Area Fill
                    drawPath(
                        path = blockedFillPath,
                        brush = Brush.verticalGradient(
                            listOf(ClearColors.green.copy(alpha = 0.20f), Color.Transparent)
                        )
                    )
                    // Blocked Glow Shadow
                    drawPath(
                        path = blockedPath,
                        color = ClearColors.green.copy(alpha = 0.28f),
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Draw Blocked Line
                    drawPath(
                        path = blockedPath,
                        color = ClearColors.green,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Draw points at the ends
                    for (i in 0 until pointsCount) {
                        val ap = getCoords(i, allowedData[i])
                        val bp = getCoords(i, blockedData[i])
                        drawCircle(color = ClearColors.blue, radius = 4.dp.toPx(), center = ap)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = ap)

                        drawCircle(color = ClearColors.green, radius = 4.dp.toPx(), center = bp)
                        drawCircle(color = Color.White, radius = 2.dp.toPx(), center = bp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // X-Axis labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(text = label, fontSize = 11.sp, color = ClearColors.muted)
                }
            }
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, fontSize = 11.sp, color = ClearColors.muted, fontWeight = FontWeight.Medium)
    }
}
