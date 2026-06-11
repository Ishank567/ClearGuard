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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.blocking.HostBlocker
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.LiquidGlassButton
import com.clearguard.app.ui.theme.ClearColors
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
        message = "Allowed $normalized"
        // reload() re-parses host files, so run it off the main thread.
        scope.launch {
            withContext(Dispatchers.IO) {
                HostBlocker.get(context).reload()
                ClearGuardVpnService.reloadIfRunning(context)
            }
            recent = ClearGuardVpnService.recentBlocked()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent Activity", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            if (recent.isNotEmpty()) {
                LiquidGlassButton(
                    onClick = {
                        ClearGuardVpnService.clearRecentBlocked()
                        recent = ClearGuardVpnService.recentBlocked()
                        message = ""
                    },
                    modifier = Modifier.height(36.dp),
                    accent = ClearColors.muted,
                    contentColor = ClearColors.muted,
                    cornerRadius = 18.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Text("Clear", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Domains blocked this session. Tap Allow to stop blocking one. This list lives in memory only and is never saved to disk.",
            fontSize = 12.sp,
            color = ClearColors.muted,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        if (message.isNotBlank()) {
            Text(
                message,
                fontSize = 12.sp,
                color = ClearColors.green,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
        }

        if (recent.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                if (isProtected) {
                    "No blocked queries yet. They will appear here as apps make DNS requests."
                } else {
                    "Protection is paused. Start it from Home to see blocked queries here."
                },
                fontSize = 13.sp,
                color = ClearColors.muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(recent) { entry ->
                    BlockedQueryRow(
                        domain = entry.domain,
                        whenLabel = relativeTime(nowMillis - entry.timeMillis),
                        reason = entry.reason,
                        threatScore = entry.threatScore,
                        onAllow = { allow(entry.domain) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BlockedQueryRow(
    domain: String,
    whenLabel: String,
    reason: String,
    threatScore: Int,
    onAllow: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    domain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (threatScore > 0) "$reason - score $threatScore - $whenLabel" else "$reason - $whenLabel",
                    fontSize = 12.sp,
                    color = if (threatScore > 0) ClearColors.danger else ClearColors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LiquidGlassButton(
                onClick = onAllow,
                modifier = Modifier.height(34.dp),
                cornerRadius = 17.dp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
            ) {
                Text("Allow", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
