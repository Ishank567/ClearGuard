package com.clearguard.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.R

/**
 * Clean, minimal, modern, classy and visually appealing Dashboard.
 * 
 * Design principles:
 * - Generous whitespace and clear visual hierarchy
 * - Premium but restrained use of Material 3 (elevated cards, excellent typography)
 * - The protection status is the calm, confident hero of the screen
 * - Shields presented in an elegant 2-column grid (modern dashboard feel)
 * - No heavy effects, no clutter, no animation for animation's sake
 */
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
    val context = LocalContext.current
    val prefs = remember { PreferenceKeys.prefs(context) }

    var currentMode by remember { mutableStateOf(PreferenceKeys.getCurrentMode(context)) }

    // Live shield states (drive real features)
    var adsEnabled by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_AI_AD_PATTERN_DETECTOR_ENABLED, PreferenceKeys.DEFAULT_AI_AD_PATTERN_DETECTOR_ENABLED))
    }
    var familyEnabled by remember { mutableStateOf(PreferenceKeys.getCurrentMode(context) == "kids") }
    var trackersEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(PreferenceKeys.KEY_BROWSER_ANTI_FINGERPRINT, PreferenceKeys.DEFAULT_BROWSER_ANTI_FINGERPRINT) &&
            prefs.getBoolean(PreferenceKeys.KEY_BROWSER_COOKIE_REMOVER, PreferenceKeys.DEFAULT_BROWSER_COOKIE_REMOVER)
        )
    }
    var gamingEnabled by remember { mutableStateOf(PreferenceKeys.getCurrentMode(context) == "battery") }
    var malwareEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(PreferenceKeys.KEY_MOBILE_RISK_SCORING_ENABLED, PreferenceKeys.DEFAULT_MOBILE_RISK_SCORING_ENABLED) &&
            prefs.getBoolean(PreferenceKeys.KEY_RASP_ENABLED, PreferenceKeys.DEFAULT_RASP_ENABLED)
        )
    }
    var cryptoEnabled by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_RASP_ENABLED, PreferenceKeys.DEFAULT_RASP_ENABLED))
    }

    val shields = listOf(
        ShieldItem("Ads & Patterns", Icons.Default.AdUnits, adsEnabled) { enabled ->
            adsEnabled = enabled
            prefs.edit().putBoolean(PreferenceKeys.KEY_AI_AD_PATTERN_DETECTOR_ENABLED, enabled).apply()
            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
        },
        ShieldItem("Family / Kids", Icons.Default.FamilyRestroom, familyEnabled) { enabled ->
            familyEnabled = enabled
            val mode = if (enabled) "kids" else "default"
            prefs.edit().putString(PreferenceKeys.KEY_PROTECTION_MODE, mode).apply()
            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
            currentMode = mode
        },
        ShieldItem("Trackers", Icons.Default.TrackChanges, trackersEnabled) { enabled ->
            trackersEnabled = enabled
            prefs.edit()
                .putBoolean(PreferenceKeys.KEY_BROWSER_ANTI_FINGERPRINT, enabled)
                .putBoolean(PreferenceKeys.KEY_BROWSER_COOKIE_REMOVER, enabled)
                .apply()
        },
        ShieldItem("Battery Saver", Icons.Default.BatterySaver, gamingEnabled) { enabled ->
            gamingEnabled = enabled
            val mode = if (enabled) "battery" else "default"
            prefs.edit().putString(PreferenceKeys.KEY_PROTECTION_MODE, mode).apply()
            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
            currentMode = mode
        },
        ShieldItem("Malware & RASP", Icons.Default.Security, malwareEnabled) { enabled ->
            malwareEnabled = enabled
            prefs.edit()
                .putBoolean(PreferenceKeys.KEY_MOBILE_RISK_SCORING_ENABLED, enabled)
                .putBoolean(PreferenceKeys.KEY_RASP_ENABLED, enabled)
                .apply()
            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
        },
        ShieldItem("Crypto Mining", Icons.Default.CurrencyBitcoin, cryptoEnabled) { enabled ->
            cryptoEnabled = enabled
            prefs.edit().putBoolean(PreferenceKeys.KEY_RASP_ENABLED, enabled).apply()
            if (enabled) {
                listOf("xmrpool.net", "supportxmr.com", "minemonero.com", "pool.minexmr.com", "cryptonight.net", "xmr.nanopool.org")
                    .forEach { PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_SECURITY_BLOCKS, it) }
            }
            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // ========== HERO: Status + Primary Action ==========
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Premium branded logo treatment - using high-quality modern logo asset + wordmark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.shield_dns_logo),
                    contentDescription = "ShieldDNS",
                    modifier = Modifier.height(48.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "ShieldDNS",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Big, calm status
            Text(
                text = if (isProtected) "Protected" else "Protection Paused",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (isProtected) {
                    "$blockedToday threats & ads blocked today"
                } else {
                    "Your device is currently unprotected"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(24.dp))

            // Classy, prominent action button (the heart of the experience)
            LiquidGlassButton(
                onClick = { onToggleProtection(!isProtected) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                accent = if (isProtected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                contentColor = if (isProtected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = if (isProtected) "Pause Protection" else "Start Protection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // ========== STATISTICS ==========
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSectionHeader("Statistics")

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatItem(
                    modifier = Modifier.weight(1f),
                    label = "Blocked Today",
                    value = blockedToday.toString(),
                    icon = Icons.Default.Block
                )
                StatItem(
                    modifier = Modifier.weight(1f),
                    label = "Active Rules",
                    value = formatLargeNumber(totalBlocked),
                    icon = Icons.Default.ListAlt
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatItem(
                    modifier = Modifier.weight(1f),
                    label = "Scam Blocks",
                    value = if (scamShieldEnabled) formatCompact(scamBlockedToday) else "Off",
                    icon = Icons.Default.GppGood
                )
                StatItem(
                    modifier = Modifier.weight(1f),
                    label = "Cache Efficiency",
                    value = cacheEfficiency(cacheHits, upstreamQueries),
                    icon = Icons.Default.Speed
                )
            }
        }

        // ========== ACTIVE SHIELDS (beautiful 2-col grid) ==========
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSectionHeader("Active Shields")

            // Clean 2-column grid using simple rows (perfect for small fixed set)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shields.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { shield ->
                            ShieldCard(
                                item = shield,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Fill empty slot if odd number
                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ========== PROTECTION MODES ==========
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSectionHeader("Protection Mode")

            val visionModes = listOf(
                "default" to Icons.Default.Shield,
                "study" to Icons.Default.MenuBook,
                "work" to Icons.Default.Work,
                "kids" to Icons.Default.ChildCare,
                "elder" to Icons.Default.Elderly,
                "shopping" to Icons.Default.ShoppingCart,
                "spiritual" to Icons.Default.SelfImprovement,
                "battery" to Icons.Default.BatterySaver
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visionModes.forEach { (modeId, icon) ->
                    val selected = currentMode == modeId
                    FilterChip(
                        selected = selected,
                        onClick = {
                            currentMode = modeId
                            prefs.edit().putString(PreferenceKeys.KEY_PROTECTION_MODE, modeId).apply()
                            com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
                        },
                        label = { Text(PreferenceKeys.modeDisplayName(modeId)) },
                        leadingIcon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Text(
                text = PreferenceKeys.modeDescription(currentMode),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }

        // ========== SYSTEM STATUS ==========
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppSectionHeader("System")

            GlassCard {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusRow("DNS Filtering", if (isProtected) "Active" else "Paused")
                    StatusRow("Threat Shield", if (scamShieldEnabled) "On (scam + DGA)" else "Basic")
                    if (PreferenceKeys.isIndianScamShieldEnabled(context)) {
                        StatusRow("Indian Scam Shield", "Active — 11 categories")
                    }
                    StatusRow("Secure DNS", if (dohEnabled) "DoH (encrypted)" else "Classic UDP")
                    StatusRow("Telemetry", "None — fully on-device")
                }
            }
        }
    }
}

// Elegant stat card
@Composable
private fun StatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector
) {
    GlassCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Beautiful minimal shield toggle card for the 2-col grid
@Composable
private fun ShieldCard(item: ShieldItem, modifier: Modifier = Modifier) {
    GlassCardInteractive(
        onClick = { item.onToggle(!item.enabled) },
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (item.enabled) {
                    Text(
                        "Active • Real-time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
            Switch(
                checked = item.enabled,
                onCheckedChange = item.onToggle
            )
        }
    }
}

private data class ShieldItem(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean,
    val onToggle: (Boolean) -> Unit
)

@Composable
private fun StatusRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable


private fun formatLargeNumber(num: Int): String {
    return when {
        num >= 1_000_000 -> String.format("%.1fM", num / 1_000_000f)
        num >= 10_000 -> "${num / 1000}k"
        else -> num.toString()
    }
}

private fun cacheEfficiency(cacheHits: Long, upstreamQueries: Long): String {
    val total = cacheHits + upstreamQueries
    if (total <= 0L) return "Learning"
    return String.format("%.0f%%", (cacheHits * 100f) / total)
}

private fun formatCompact(value: Long): String {
    return when {
        value >= 1_000_000 -> String.format("%.1fM", value / 1_000_000f)
        value >= 10_000 -> "${value / 1000}k"
        else -> value.toString()
    }
}