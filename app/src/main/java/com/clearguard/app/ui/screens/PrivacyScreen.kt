package com.clearguard.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.GlassCardCompact
import com.clearguard.app.ui.components.LiquidGlassButton
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign
import com.clearguard.app.vpn.ClearGuardVpnService
import kotlin.math.cos
import kotlin.math.sin

// Screenshot Scanner imports
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.clearguard.app.security.ScamScreenshotAnalyzer
import kotlinx.coroutines.launch

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
    val tabs = listOf("Live Monitor", "Analytics", "App Audit", "Scanner")

    Column(modifier = Modifier.fillMaxSize()) {
        // Frosted Glass sub-tabs switcher
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ClearDesign.screenHPadding, vertical = 8.dp),
            cornerRadius = 16.dp,
            glassAlpha = 0.85f
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                val tabWidth = maxWidth / tabs.size
                val animatedTabOffset by animateDpAsState(
                    targetValue = tabWidth * selectedTab,
                    animationSpec = spring(dampingRatio = 0.76f, stiffness = 380f),
                    label = "tabSlide"
                )

                // Sliding capsule indicator
                Box(
                    modifier = Modifier
                        .offset(x = animatedTabOffset)
                        .width(tabWidth)
                        .height(38.dp)
                        .padding(horizontal = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ClearColors.green.copy(alpha = 0.18f))
                        .border(
                            width = 1.dp,
                            color = ClearColors.green.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(12.dp)
                        )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, title ->
                        val selected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(12.dp))
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
        }

        Spacer(modifier = Modifier.height(6.dp))

        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                (fadeIn(tween(ClearDesign.tabCrossfadeMs)) +
                    slideInHorizontally(tween(ClearDesign.tabCrossfadeMs)) { if (targetState > initialState) it / 4 else -it / 4 }) togetherWith
                    (fadeOut(tween(ClearDesign.tabCrossfadeMs)) +
                        slideOutHorizontally(tween(ClearDesign.tabCrossfadeMs)) { if (targetState > initialState) -it / 4 else it / 4 })
            },
            label = "privacyTabContent"
        ) { tab ->
            when (tab) {
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
                3 -> ScamScreenshotScanner()
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
            val infiniteTransition = rememberInfiniteTransition(label = "emptyPulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 0.92f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "emptyScale"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.12f,
                targetValue = 0.28f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "emptyAlpha"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Pulsing glow behind icon
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .graphicsLayer {
                                scaleX = pulseScale
                                scaleY = pulseScale
                            }
                            .clip(CircleShape)
                            .background(ClearColors.green.copy(alpha = pulseAlpha))
                    )
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = ClearColors.green.copy(alpha = 0.7f),
                        modifier = Modifier.size(42.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Waiting for connections\u2026",
                    fontSize = 15.sp,
                    color = ClearColors.text,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Enable the DNS Shield and open apps to see live traffic here.",
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
                .padding(horizontal = ClearDesign.screenHPadding),
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
            .padding(horizontal = ClearDesign.screenHPadding, vertical = 8.dp)
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
    if (total <= 0) return 95
    // Base cleanliness: fewer trackers relative to queries = higher score
    val ratio = blocked.toDouble() / total.coerceAtLeast(1)
    var score = (100 - (ratio * 78)).toInt()

    // Bonus for very clean apps
    if (blocked < 3 && total < 30) score += 12
    // Penalty for heavy tracker apps
    if (ratio > 0.6) score -= 18

    return score.coerceIn(12, 100)
}

private fun formatTime(millis: Long): String {
    val date = java.util.Date(millis)
    val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.US)
    return sdf.format(date)
}

// =====================================================
// Scam Screenshot Scanner (Indian Scam Shield feature)
// User uploads screenshot of ad/SMS/WhatsApp/website → on-device OCR + heuristic analysis
// =====================================================
@Composable
fun ScamScreenshotScanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detections by remember { mutableStateOf<List<ScamScreenshotAnalyzer.Detection>>(emptyList()) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var suggestedDomains by remember { mutableStateOf<List<String>>(emptyList()) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            errorMessage = null
            detections = emptyList()
            extractedText = ""
            suggestedDomains = emptyList()

            // Load bitmap (compatible path for minSdk 26)
            scope.launch {
                isAnalyzing = true
                try {
                    @Suppress("DEPRECATION")
                    val bmp = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    bitmap = bmp

                    // Run on-device OCR + Indian Scam Shield analysis
                    val dets = ScamScreenshotAnalyzer.analyze(context, bmp)
                    detections = dets

                    // Build display text from snippets + attempt domain extraction
                    extractedText = dets.joinToString("\n") { "• ${it.snippet}" }.ifBlank { "No readable text found." }
                    suggestedDomains = ScamScreenshotAnalyzer.extractSuspiciousDomains(
                        dets.joinToString(" ") { it.snippet } + " " + (selectedUri?.toString() ?: "")
                    )
                } catch (e: Exception) {
                    errorMessage = "OCR or analysis failed: ${e.message}"
                    detections = emptyList()
                } finally {
                    isAnalyzing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Scam Screenshot Scanner", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = ClearColors.green)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Upload a screenshot of a suspicious ad, SMS, WhatsApp message or website. On-device OCR + Indian Scam Shield patterns will check for fake reward, KYC, payment, investment, job, customer support, or APK lures.",
                    fontSize = 13.sp,
                    color = ClearColors.muted
                )
                Spacer(Modifier.height(16.dp))

                LiquidGlassButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    accent = ClearColors.green
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Select Screenshot from Gallery")
                    }
                }

                if (selectedUri != null && bitmap != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("Selected image:", fontSize = 12.sp, color = ClearColors.muted)
                    Spacer(Modifier.height(6.dp))
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Uploaded screenshot",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, ClearColors.border.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        if (isAnalyzing) {
            GlassCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = ClearColors.green)
                    Spacer(Modifier.height(12.dp))
                    Text("Running on-device OCR + scam analysis...", fontSize = 14.sp, color = ClearColors.text)
                    Text("All processing stays on your device.", fontSize = 12.sp, color = ClearColors.muted)
                }
            }
        }

        if (errorMessage != null) {
            GlassCard {
                Text(errorMessage!!, color = ClearColors.danger, modifier = Modifier.padding(16.dp))
            }
        }

        if (detections.isNotEmpty()) {
            GlassCard {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ClearColors.danger)
                        Spacer(Modifier.width(8.dp))
                        Text("Detections — Indian Scam Shield", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("${detections.size} scam pattern(s) found in the image text", fontSize = 13.sp, color = ClearColors.muted)
                    Spacer(Modifier.height(12.dp))

                    detections.forEach { det ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            glassAlpha = 0.6f
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row {
                                    Text(det.category, fontWeight = FontWeight.Bold, color = ClearColors.danger, fontSize = 15.sp)
                                    Spacer(Modifier.weight(1f))
                                    Text("${det.confidence}%", fontSize = 12.sp, color = ClearColors.muted)
                                }
                                Text(det.reason, fontSize = 12.sp, color = ClearColors.text)
                                if (det.snippet.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "\"${det.snippet.take(140)}${if (det.snippet.length > 140) "..." else ""}\"",
                                        fontSize = 11.sp,
                                        color = ClearColors.muted,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (bitmap != null && !isAnalyzing && detections.isEmpty() && errorMessage == null) {
            GlassCard {
                Text(
                    "No obvious scam patterns detected in the text. Still exercise caution — some sophisticated scams use images with little text.",
                    modifier = Modifier.padding(16.dp),
                    color = ClearColors.text
                )
            }
        }

        if (extractedText.isNotBlank()) {
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Extracted text (on-device OCR)", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        extractedText.take(600) + if (extractedText.length > 600) "..." else "",
                        fontSize = 12.sp,
                        color = ClearColors.muted
                    )
                }
            }
        }

        if (suggestedDomains.isNotEmpty()) {
            GlassCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Domains / links mentioned in image", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    suggestedDomains.forEach { domain ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Add to custom block list + reload protection
                                    val normalized = com.clearguard.app.blocking.HostBlocker.normalizeDomain(domain) ?: domain
                                    PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, normalized)
                                    com.clearguard.app.blocking.HostBlocker.get(context).reload()
                                    ClearGuardVpnService.reloadIfRunning(context)
                                    // Simple toast-like feedback
                                    // (In real app could use Snackbar)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(domain, modifier = Modifier.weight(1f), color = ClearColors.text)
                            Text("Block", color = ClearColors.green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Tap any domain to add it to your block list.", fontSize = 11.sp, color = ClearColors.muted)
                }
            }
        }

        // Quick tip
        GlassCard(glassAlpha = 0.5f) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Tip", fontWeight = FontWeight.Medium)
                Text(
                    "This scanner uses the same on-device Indian Scam Shield patterns as the DNS blocker. Best results on clear text screenshots (ads, SMS, WhatsApp forwards).",
                    fontSize = 12.sp, color = ClearColors.muted
                )
            }
        }
    }
}


