package com.clearguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.blocking.HostBlocker
import com.clearguard.app.ui.components.GlassCard
// LiquidGlassButton replaced by clean PrimaryButton / SecondaryButton in fresh UI
// Using fresh MaterialTheme + standard components (old Clear* tokens removed)
import com.clearguard.app.vpn.ClearGuardVpnService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ActivityScreen(isProtected: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var recent by remember { mutableStateOf(ClearGuardVpnService.recentBlocked()) }
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var message by remember { mutableStateOf("") }
    var selectedQuery by remember { mutableStateOf<ClearGuardVpnService.BlockedQuery?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("All") }

    // Poll the in-memory ring buffer. Faster while protected, since that is when it changes.
    LaunchedEffect(isProtected) {
        while (isActive) {
            recent = ClearGuardVpnService.recentBlocked()
            nowMillis = System.currentTimeMillis()
            delay(if (isProtected) 2000L else 5000L)
        }
    }

    fun allow(domain: String) {
        val normalized = HostBlocker.normalizeDomain(domain) ?: domain
        PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_ALLOWLIST, normalized)
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, normalized)
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_SECURITY_BLOCKS, normalized)
        message = "Allowed $normalized"
        scope.launch {
            withContext(Dispatchers.IO) {
                HostBlocker.get(context).reload()
                ClearGuardVpnService.reloadIfRunning(context)
            }
            recent = ClearGuardVpnService.recentBlocked()
        }
    }

    fun block(domain: String) {
        val normalized = HostBlocker.normalizeDomain(domain) ?: domain
        PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_CUSTOM_BLOCKS, normalized)
        PreferenceKeys.removeFromStringSet(context, PreferenceKeys.KEY_ALLOWLIST, normalized)
        message = "Blocked $normalized"
        scope.launch {
            withContext(Dispatchers.IO) {
                HostBlocker.get(context).reload()
                ClearGuardVpnService.reloadIfRunning(context)
            }
            recent = ClearGuardVpnService.recentBlocked()
        }
    }

    val filteredRecent = remember(recent, searchQuery, filterStatus) {
        recent.filter { entry ->
            val matchesSearch = entry.domain.contains(searchQuery.trim(), ignoreCase = true)
            val matchesFilter = when (filterStatus) {
                "All" -> true
                "Blocked" -> entry.blocked && entry.status == "blocked"
                "Allowed" -> !entry.blocked
                "Threats" -> entry.blocked && (entry.status == "threat" || entry.status == "bypass")
                else -> true
            }
            matchesSearch && matchesFilter
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent Activity", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                if (recent.isNotEmpty()) {
                    SecondaryButton(
                        onClick = {
                            ClearGuardVpnService.clearRecentBlocked()
                            recent = ClearGuardVpnService.recentBlocked()
                            message = ""
                        },
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Clear", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "DNS queries captured this session. Tap a row for details or to block/allow. Lives in memory only.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
            )

            if (message.isNotBlank()) {
                Text(
                    message,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                )
            }

            if (recent.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (true) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
                    placeholder = { Text("Search domain...", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { searchQuery = "" }
                            )
                        }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chips = listOf("All", "Allowed", "Blocked", "Threats")
                    chips.forEach { chip ->
                        val isSelected = filterStatus == chip
                        val chipColor = when (chip) {
                            "Allowed" -> MaterialTheme.colorScheme.primary
                            "Blocked" -> MaterialTheme.colorScheme.secondary
                            "Threats" -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        val bgAlpha = if (isSelected) 0.18f else 0.05f
                        val borderAlpha = if (isSelected) 0.5f else 0.15f
                        val textColor = if (isSelected) chipColor else MaterialTheme.colorScheme.onSurfaceVariant
                        
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) chipColor.copy(alpha = bgAlpha) else (if (true) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface))
                                .border(1.dp, if (isSelected) chipColor.copy(alpha = borderAlpha) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = borderAlpha), RoundedCornerShape(12.dp))
                                .clickable { filterStatus = chip }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chip,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
            }

            if (recent.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isProtected) Icons.Default.CheckCircle else Icons.Default.Pause,
                            contentDescription = null,
                            tint = if (isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (isProtected) "Active Protection" else "Protection paused",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isProtected) {
                                "No queries captured yet. They will appear here in real-time as DNS requests occur."
                            } else {
                                "Start protection from Home to start capturing DNS query logs."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (filteredRecent.isEmpty()) {
                Spacer(Modifier.height(24.dp))
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "No queries match",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "No results found for search \"$searchQuery\" or filter \"$filterStatus\".",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredRecent) { entry ->
                        QueryRow(
                            query = entry,
                            whenLabel = relativeTime(nowMillis - entry.timeMillis),
                            onClick = { selectedQuery = entry },
                            onAllow = { allow(entry.domain) },
                            onBlock = { block(entry.domain) }
                        )
                    }
                }
            }
        }

        // Overlay Scrim
        if (selectedQuery != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { selectedQuery = null }
            )
        }

        // Sliding Details Panel
        androidx.compose.animation.AnimatedVisibility(
            visible = selectedQuery != null,
            enter = androidx.compose.animation.slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 300f)
            ) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220)
            ) + androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            selectedQuery?.let { query ->
                DomainDetailPanel(
                    query = query,
                    onDismiss = { selectedQuery = null },
                    onAllow = {
                        allow(query.domain)
                        selectedQuery = null
                    },
                    onBlock = {
                        block(query.domain)
                        selectedQuery = null
                    }
                )
            }
        }
    }
}

@Composable
private fun QueryRow(
    query: com.clearguard.app.vpn.ClearGuardVpnService.BlockedQuery,
    whenLabel: String,
    onClick: () -> Unit,
    onAllow: () -> Unit,
    onBlock: () -> Unit
) {
    val statusColor = when (query.status) {
        "allowed" -> MaterialTheme.colorScheme.primary
        "blocked" -> MaterialTheme.colorScheme.secondary
        "threat" -> MaterialTheme.colorScheme.error
        "bypass" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val statusText = when (query.status) {
        "allowed" -> if (query.latencyMs == 0) "Cache hit" else "${query.latencyMs} ms"
        "blocked" -> "Blocked"
        "threat" -> "Threat Block"
        "bypass" -> "Bypass sidestep"
        else -> "Logged"
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        cornerRadius = 18.dp,
        glassAlpha = 0.82f,
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    query.domain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$statusText • $whenLabel",
                    fontSize = 11.sp,
                    color = if (query.status == "threat") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Spacer(Modifier.width(8.dp))

            if (!query.blocked) {
                SecondaryButton(
                    onClick = onBlock,
                    modifier = Modifier.height(32.dp),
                    accent = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.error,
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("Block", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            } else {
                SecondaryButton(
                    onClick = onAllow,
                    modifier = Modifier.height(32.dp),
                    accent = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.primary,
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                ) {
                    Text("Allow", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun relativeTime(deltaMillis: Long): String {
    if (deltaMillis < 0) return "just now"
    val seconds = deltaMillis / 1000
    return when {
        seconds < 5 -> "just now"
        seconds < 60 -> "${seconds}s ago"
        seconds < 3600 -> "${seconds / 60}m ago"
        else -> "${seconds / 3600}h ago"
    }
}

@Composable
private fun DomainDetailPanel(
    query: com.clearguard.app.vpn.ClearGuardVpnService.BlockedQuery,
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
    onBlock: () -> Unit
) {
    val category = when {
        !query.blocked -> "Allowed DNS Traffic"
        query.threatScore > 50 -> "Malware C2 / Phishing"
        query.threatScore > 0 -> "Suspicious / Threat"
        query.reason.lowercase().contains("ad") -> "Advertising / Tracker"
        query.reason.lowercase().contains("custom") -> "Custom Block List"
        else -> "Analytics / Telemetry"
    }

    val categoryColor = when {
        !query.blocked -> MaterialTheme.colorScheme.primary
        query.threatScore > 50 -> MaterialTheme.colorScheme.error
        query.threatScore > 0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }

    val ipHash = Math.abs(query.domain.hashCode())
    val resolvedIp = "172.217.${(ipHash % 255)}.${(ipHash / 256) % 255}"
    val resolvedIpv6 = "2607:f8b0:4005:80${(ipHash % 9)}::200e"

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        cornerRadius = 24.dp,
        glassAlpha = 0.94f,
        elevation = 28.dp
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Shield Query Details",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor.copy(alpha = 0.15f))
                        .border(1.dp, categoryColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = category,
                        fontSize = 11.sp,
                        color = categoryColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = query.domain,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailMetricBox(
                    label = "Resolution Status",
                    value = if (query.blocked) "Blocked" else "Allowed",
                    accentColor = categoryColor,
                    modifier = Modifier.weight(1f)
                )
                DetailMetricBox(
                    label = "Reason / Trigger",
                    value = query.reason,
                    accentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1.5f)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = if (query.blocked) "DNS Sinkhole Simulation" else "DNS Resolution Simulation",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (query.blocked) {
                    RecordRow("A Record", "0.0.0.0 (Sinkhole)")
                    RecordRow("AAAA Record", ":: (Sinkhole)")
                    RecordRow("TTL", "0 seconds (Immediate cache expiry)")
                    RecordRow("Security Check", if (query.threatScore > 0) "Blocked via Heuristics" else "Blocked via Hostfile")
                } else {
                    RecordRow("A Record", resolvedIp)
                    RecordRow("AAAA Record", resolvedIpv6)
                    RecordRow("TTL", "300 seconds (Cached)")
                    RecordRow("Security Check", "Clean / Allowed")
                }
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SecondaryButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    accent = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text("Close", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                if (query.blocked) {
                    SecondaryButton(
                        onClick = onAllow,
                        modifier = Modifier.weight(1.5f),
                        accent = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Text("Allow Domain", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                } else {
                    SecondaryButton(
                        onClick = onBlock,
                        modifier = Modifier.weight(1.5f),
                        accent = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.error
                    ) {
                        Text("Block Domain", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailMetricBox(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(12.dp)
    ) {
        Column {
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}
