package com.clearguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.theme.ClearColors
import java.util.Locale

@Composable
fun StatisticsScreen(
    blockedToday: Int,
    totalBlocked: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Blocked",
                value = formatNumber(blockedToday),
                accent = true
            )
            StatCard(
                modifier = Modifier.weight(1f),
                label = "Allowed",
                value = formatNumber(totalBlocked),
                accent = false
            )
        }

        Spacer(Modifier.height(16.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Local Counters", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "ClearGuard keeps aggregate DNS counts only. It does not store browsing history or per-domain logs.",
                    color = ClearColors.muted,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Cache", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Allowed DNS responses are cached in memory to reduce repeated network calls and battery use.",
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

private fun formatNumber(n: Int): String {
    return when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fm", n / 1_000_000f)
        n >= 10_000 -> "${n / 1000}k"
        else -> "%,d".format(n)
    }
}
