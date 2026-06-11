package com.clearguard.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.blocking.BlocklistUpdater
import com.clearguard.app.blocking.HostBlocker
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.theme.ClearColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

data class BlocklistRow(val name: String, val count: String, val source: String)

@Composable
fun BlocklistsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PreferenceKeys.prefs(context) }

    var updating by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf("") }
    var activeHosts by remember { mutableStateOf(HostBlocker.get(context).snapshot().blockedHostCount) }
    var downloadedHosts by remember { mutableStateOf(prefs.getInt(PreferenceKeys.KEY_LAST_UPDATE_COUNT, 0)) }

    fun refreshCounts() {
        HostBlocker.get(context).reload()
        activeHosts = HostBlocker.get(context).snapshot().blockedHostCount
        downloadedHosts = prefs.getInt(PreferenceKeys.KEY_LAST_UPDATE_COUNT, 0)
    }

    val customBlocks = prefs.getStringSet(PreferenceKeys.KEY_CUSTOM_BLOCKS, emptySet<String>())?.size ?: 0
    val allowOverrides = prefs.getStringSet(PreferenceKeys.KEY_ALLOWLIST, emptySet<String>())?.size ?: 0
    val rows = listOf(
        BlocklistRow("Built-in starter list", "12", "Bundled asset"),
        BlocklistRow("Downloaded hosts", formatCount(downloadedHosts), "Manual update"),
        BlocklistRow("Custom blocks", customBlocks.toString(), "On-device"),
        BlocklistRow("Allow overrides", allowOverrides.toString(), "On-device")
    )

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
            Text("Blocklists", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            GlassCard(
                modifier = Modifier
                    .height(40.dp)
                    .width(140.dp)
                    .clickable(enabled = !updating) {
                        scope.launch {
                            updating = true
                            updateMessage = ""
                            val result = withContext(Dispatchers.IO) {
                                BlocklistUpdater.updateNow(context.applicationContext)
                            }
                            refreshCounts()
                            updateMessage = if (result.success) {
                                "Loaded ${formatCount(result.hostCount)} domains"
                            } else {
                                result.message
                            }
                            updating = false
                        }
                    },
                cornerRadius = 20.dp,
                glassAlpha = 0.85f
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ClearColors.green,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (updating) "Updating" else "Update",
                        color = ClearColors.green,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "${formatCount(activeHosts)} active blocked domains. Last update: ${lastUpdateLabel(prefs.getLong(PreferenceKeys.KEY_LAST_UPDATE_MILLIS, 0L))}",
            fontSize = 12.sp,
            color = ClearColors.muted,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )
        if (updateMessage.isNotBlank()) {
            Text(
                updateMessage,
                fontSize = 12.sp,
                color = ClearColors.green,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(rows) { row ->
                GlassListItem(row)
            }
        }
    }
}

@Composable
private fun GlassListItem(row: BlocklistRow) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        glassAlpha = 0.82f,
        elevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text("${row.count} domains", fontSize = 12.sp, color = ClearColors.muted)
            }
            Text(row.source, fontSize = 12.sp, color = ClearColors.muted)
        }
    }
}

private fun lastUpdateLabel(millis: Long): String {
    if (millis <= 0L) return "never"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(millis))
}

private fun formatCount(value: Int): String {
    return when {
        value >= 1_000_000 -> String.format(Locale.US, "%.1fm", value / 1_000_000.0)
        value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
        else -> value.toString()
    }
}
