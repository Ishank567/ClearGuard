package com.clearguard.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.GlassCardCompact
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign
import com.clearguard.app.vpn.ClearGuardVpnService
import kotlin.math.cos
import kotlin.math.sin

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
    dohQueries: Long
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Live Monitor", "Analytics", "App Audit")

    Column(modifier = Modifier.fillMaxSize()) {
        // Frosted Glass sub-tabs switcher
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            cornerRadius = 16.dp,
            glassAlpha = 0.85f
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tabs.forEachIndexed { index, title ->
                    val selected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) ClearColors.green.copy(alpha = 0.18f) else Color.Transparent
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) ClearColors.green.copy(alpha = 0.35f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) ClearColors.green else ClearColors.text
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (selectedTab) {
                0 -> LiveMonitorScreen()
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
            }
        }
    }
}

// === TAB 1: Real-time Connection Monitor ===
@Composable
fun LiveMonitorScreen() {
    var queries by remember { mutableStateOf(emptyList<ClearGuardVpnService.BlockedQuery>()) }

    // Fetch queries periodically
    LaunchedEffect(Unit) {
        while (true) {
            queries = ClearGuardVpnService.recentBlocked()
            kotlinx.coroutines.delay(1200L)
        }
    }

    if (queries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = ClearColors.muted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No recent connection activity",
                    fontSize = 14.sp,
                    color = ClearColors.text,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Start the VPN and run apps to monitor outgoing connections in real-time.",
                    fontSize = 12.sp,
                    color = ClearColors.muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(queries) { query ->
                GlassCardCompact(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Custom App Logo Icon Card
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ClearColors.border.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            val isSystem = query.appPackage == "unknown" || query.appName.contains("System")
                            Icon(
                                imageVector = if (isSystem) Icons.Default.SettingsSystemDaydream else Icons.Default.Android,
                                contentDescription = null,
                                tint = if (query.blocked) ClearColors.danger else ClearColors.green,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = query.appName,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ClearColors.text
                                )
                                Text(
                                    text = formatTime(query.timeMillis),
                                    fontSize = 11.sp,
                                    color = ClearColors.muted
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = query.domain,
                                fontSize = 12.sp,
                                color = ClearColors.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (query.blocked) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Block,
                                        contentDescription = null,
                                        tint = ClearColors.danger,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = query.reason,
                                        fontSize = 11.sp,
                                        color = ClearColors.danger,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
@Composable
fun AppAuditTab() {
    val context = LocalContext.current
    var stats by remember { mutableStateOf(emptyList<ClearGuardVpnService.AppStats>()) }
    var trackerConnections by remember { mutableStateOf(emptyList<ClearGuardVpnService.TrackerConnection>()) }

    // Fetch Stats
    LaunchedEffect(Unit) {
        while (true) {
            stats = ClearGuardVpnService.getAppPrivacyStats().sortedByDescending { it.blockedQueries }
            trackerConnections = ClearGuardVpnService.getTrackerConnections()
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
        // Toggle: Scores vs Tracker Map
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { selectedAuditTab = 0 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedAuditTab == 0) ClearColors.green.copy(alpha = 0.18f) else Color.Transparent,
                    contentColor = if (selectedAuditTab == 0) ClearColors.green else ClearColors.text
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = if (selectedAuditTab == 0) ClearColors.green.copy(alpha = 0.35f) else ClearColors.border.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Privacy Scores", fontSize = 12.sp)
            }

            Button(
                onClick = { selectedAuditTab = 1 },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedAuditTab == 1) ClearColors.green.copy(alpha = 0.18f) else Color.Transparent,
                    contentColor = if (selectedAuditTab == 1) ClearColors.green else ClearColors.text
                ),
                modifier = Modifier
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = if (selectedAuditTab == 1) ClearColors.green.copy(alpha = 0.35f) else ClearColors.border.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Grain, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tracker Map", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedAuditTab == 0) {
            // App scores scrollable list
            if (stats.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Auditing device apps...",
                            color = ClearColors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Privacy scores and tracker counters will populate here as apps make network queries.",
                            color = ClearColors.muted,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                stats.forEach { appStat ->
                    val score = calculatePrivacyScore(appStat.blockedQueries, appStat.totalQueries)
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appStat.appName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ClearColors.text
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${appStat.blockedQueries} trackers blocked out of ${appStat.totalQueries} queries",
                                    fontSize = 12.sp,
                                    color = ClearColors.muted
                                )
                                if (appStat.trackers.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Top trackers: " + appStat.trackers.keys.take(2).joinToString(", "),
                                        fontSize = 11.sp,
                                        color = ClearColors.danger,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Score gauge circle
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawArc(
                                        color = ClearColors.border.copy(alpha = 0.25f),
                                        startAngle = 0f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = 4.dp.toPx())
                                    )
                                    drawArc(
                                        color = if (score >= 80) ClearColors.green else if (score >= 50) ClearColors.blue else ClearColors.danger,
                                        startAngle = -90f,
                                        sweepAngle = (score.toFloat() / 100f) * 360f,
                                        useCenter = false,
                                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "$score",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ClearColors.text
                                    )
                                    Text(
                                        text = "/100",
                                        fontSize = 8.sp,
                                        color = ClearColors.muted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Visual Tracker Map
            Text(
                text = "Invisible Tracker Map",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClearColors.text
            )
            Text(
                text = "Dynamic on-device graph showing connections from your apps (inner nodes) to tracking companies (outer nodes).",
                fontSize = 12.sp,
                color = ClearColors.muted
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
                        Text(
                            text = "No trackers detected yet",
                            color = ClearColors.muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
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
    if (total <= 0) return 100
    val score = 100 - (blocked * 100 / total)
    return score.toInt().coerceIn(0, 100)
}

private fun formatTime(millis: Long): String {
    val date = java.util.Date(millis)
    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
    return sdf.format(date)
}
