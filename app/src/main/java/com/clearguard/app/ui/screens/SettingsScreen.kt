package com.clearguard.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.LiquidGlassButton
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.vpn.ClearGuardVpnService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isProtected: Boolean,
    onProtectionChange: (Boolean) -> Unit,
    scamShieldEnabled: Boolean,
    onScamShieldChange: (Boolean) -> Unit,
    dohEnabled: Boolean,
    onDohEnabledChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceKeys.prefs(context) }
    var resolver by remember {
        mutableStateOf(
            prefs.getString(PreferenceKeys.KEY_UPSTREAM_DNS, PreferenceKeys.DEFAULT_UPSTREAM_DNS)
                ?: PreferenceKeys.DEFAULT_UPSTREAM_DNS
        )
    }
    var resolverMessage by remember { mutableStateOf("") }
    var cacheTtl by remember {
        mutableStateOf(
            prefs.getInt(PreferenceKeys.KEY_CACHE_TTL_SECONDS, PreferenceKeys.DEFAULT_CACHE_TTL_SECONDS)
                .toString()
        )
    }
    var cacheMessage by remember { mutableStateOf("") }

    var dohUrl by remember {
        mutableStateOf(
            prefs.getString(PreferenceKeys.KEY_DOH_URL, PreferenceKeys.DEFAULT_DOH_URL)
                ?: PreferenceKeys.DEFAULT_DOH_URL
        )
    }
    var dohProvider by remember {
        mutableStateOf(
            prefs.getString(PreferenceKeys.KEY_DOH_PROVIDER, PreferenceKeys.DEFAULT_DOH_PROVIDER)
                ?: PreferenceKeys.DEFAULT_DOH_PROVIDER
        )
    }
    var dohMessage by remember { mutableStateOf("") }
    var dohMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Protection", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("System DNS filtering", fontSize = 12.sp, color = ClearColors.muted)
                }
                Switch(
                    checked = isProtected,
                    onCheckedChange = onProtectionChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ClearColors.green,
                        checkedTrackColor = ClearColors.green.copy(alpha = 0.35f)
                    )
                )
            }
        }

        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Threat Shield", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "On-device scam, brand impersonation + DGA (malware C2) detection",
                        fontSize = 12.sp,
                        color = ClearColors.muted
                    )
                }
                Switch(
                    checked = scamShieldEnabled,
                    onCheckedChange = { enabled ->
                        onScamShieldChange(enabled)
                        ClearGuardVpnService.reloadIfRunning(context)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ClearColors.green,
                        checkedTrackColor = ClearColors.green.copy(alpha = 0.35f)
                    )
                )
            }
        }

        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Secure DNS (DoH)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Encrypts DNS queries to a trusted provider. Protects against snooping and hijacking on Wi-Fi and by ISPs.",
                        fontSize = 12.sp,
                        color = ClearColors.muted
                    )
                }
                Switch(
                    checked = dohEnabled,
                    onCheckedChange = { enabled ->
                        onDohEnabledChange(enabled)
                        ClearGuardVpnService.reloadIfRunning(context)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ClearColors.green,
                        checkedTrackColor = ClearColors.green.copy(alpha = 0.35f)
                    )
                )
            }
        }

        // DoH quick-select provider (replaces free-text by default)
        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("DoH Provider", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Quick-select a trusted provider. Changes take effect immediately when protection is active (with cache clear).",
                    fontSize = 12.sp,
                    color = ClearColors.muted
                )
                Spacer(Modifier.height(12.dp))

                // Provider data
                val providers = listOf(
                    Provider("quad9", "Quad9 (Recommended)", "https://dns.quad9.net/dns-query", "Security-focused with malware & phishing blocking"),
                    Provider("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", "Fast, privacy-respecting public resolver"),
                    Provider("mullvad", "Mullvad", "https://dns.mullvad.net/dns-query", "No-logs, strong privacy provider"),
                    Provider("adguard", "AdGuard", "https://dns.adguard-dns.com/dns-query", "Built-in ad & tracker blocking at DNS level"),
                    Provider("custom", "Custom", "", "Use your own DoH endpoint URL")
                )

                val currentProvider = providers.find { it.id == dohProvider } ?: providers.last()

                ExposedDropdownMenuBox(
                    expanded = dohMenuExpanded,
                    onExpandedChange = { dohMenuExpanded = !it }
                ) {
                    OutlinedTextField(
                        value = currentProvider.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select provider") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dohMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )

                    ExposedDropdownMenu(
                        expanded = dohMenuExpanded,
                        onDismissRequest = { dohMenuExpanded = false }
                    ) {
                        providers.forEach { prov ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(prov.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        if (prov.desc.isNotBlank()) {
                                            Text(prov.desc, fontSize = 11.sp, color = ClearColors.muted)
                                        }
                                    }
                                },
                                onClick = {
                                    dohMenuExpanded = false
                                    dohProvider = prov.id

                                    if (prov.id != "custom") {
                                        // Apply preset immediately
                                        val newUrl = prov.url
                                        dohUrl = newUrl
                                        prefs.edit()
                                            .putString(PreferenceKeys.KEY_DOH_PROVIDER, prov.id)
                                            .putString(PreferenceKeys.KEY_DOH_URL, newUrl)
                                            .apply()
                                        dohMessage = "Switched to ${prov.label}"
                                        ClearGuardVpnService.reloadIfRunning(context)
                                    } else {
                                        // Switch to custom mode - keep current URL editable
                                        prefs.edit()
                                            .putString(PreferenceKeys.KEY_DOH_PROVIDER, "custom")
                                            .apply()
                                        dohMessage = "Custom mode enabled — edit the URL below"
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    "Active endpoint: $dohUrl",
                    fontSize = 12.sp,
                    color = ClearColors.muted
                )

                // Only show free-text editor + save when Custom is selected
                if (dohProvider == "custom") {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = dohUrl,
                        onValueChange = {
                            dohUrl = it
                            dohMessage = ""
                        },
                        singleLine = true,
                        label = { Text("Custom DoH URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LiquidGlassButton(
                        onClick = {
                            val value = dohUrl.trim()
                            if (value.startsWith("https://", ignoreCase = true) && value.length > 12) {
                                prefs.edit()
                                    .putString(PreferenceKeys.KEY_DOH_URL, value)
                                    .putString(PreferenceKeys.KEY_DOH_PROVIDER, "custom")
                                    .apply()
                                dohUrl = value
                                dohMessage = "Custom endpoint saved"
                                ClearGuardVpnService.reloadIfRunning(context)
                            } else {
                                dohMessage = "Must start with https:// and be a valid DoH URL"
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text("Save Custom URL", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (dohMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(dohMessage, fontSize = 12.sp, color = ClearColors.muted)
                }
            }
        }

        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Resolver (classic fallback)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = resolver,
                    onValueChange = {
                        resolver = it
                        resolverMessage = ""
                    },
                    singleLine = true,
                    label = { Text("IPv4 DNS server") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                LiquidGlassButton(
                    onClick = {
                        val value = resolver.trim()
                        if (isIpv4Address(value)) {
                            prefs.edit().putString(PreferenceKeys.KEY_UPSTREAM_DNS, value).apply()
                            resolver = value
                            resolverMessage = "Saved"
                            ClearGuardVpnService.reloadIfRunning(context)
                        } else {
                            resolverMessage = "Enter a valid IPv4 resolver"
                        }
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text("Save Resolver", fontWeight = FontWeight.SemiBold)
                }
                if (resolverMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(resolverMessage, fontSize = 12.sp, color = ClearColors.muted)
                }
            }
        }

        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("DNS cache", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "How long allowed DNS answers stay cached in memory. Higher saves more battery and data; lower picks up DNS changes faster.",
                    fontSize = 12.sp,
                    color = ClearColors.muted
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = cacheTtl,
                    onValueChange = {
                        cacheTtl = it.filter { ch -> ch.isDigit() }
                        cacheMessage = ""
                    },
                    singleLine = true,
                    label = { Text("Cache time (seconds)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                LiquidGlassButton(
                    onClick = {
                        val parsed = cacheTtl.trim().toIntOrNull()
                        if (parsed != null && parsed in 30..900) {
                            prefs.edit().putInt(PreferenceKeys.KEY_CACHE_TTL_SECONDS, parsed).apply()
                            cacheTtl = parsed.toString()
                            cacheMessage = "Saved"
                            ClearGuardVpnService.reloadIfRunning(context)
                        } else {
                            cacheMessage = "Enter a value between 30 and 900 seconds"
                        }
                    }
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Text("Save Cache Time", fontWeight = FontWeight.SemiBold)
                }
                if (cacheMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(cacheMessage, fontSize = 12.sp, color = ClearColors.muted)
                }
            }
        }

        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Privacy", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text("Analytics SDKs: none", fontSize = 13.sp, color = ClearColors.muted)
                Text("Accounts: none", fontSize = 13.sp, color = ClearColors.muted)
                Text("Remote app logs: none", fontSize = 13.sp, color = ClearColors.muted)
                Text("Local storage: counters and custom lists", fontSize = 13.sp, color = ClearColors.muted)
                Text("Scam scoring: on-device only", fontSize = 13.sp, color = ClearColors.muted)
                Text("Recent blocked list: in memory only", fontSize = 13.sp, color = ClearColors.muted)
            }
        }

        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Battery", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                Text("Routes DNS only, not all app traffic", fontSize = 13.sp, color = ClearColors.muted)
                Text("Uses an in-memory DNS cache", fontSize = 13.sp, color = ClearColors.muted)
                Text("Blocklist updates run only when tapped", fontSize = 13.sp, color = ClearColors.muted)
                Text("No wakelocks or polling workers", fontSize = 13.sp, color = ClearColors.muted)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

private fun isIpv4Address(value: String): Boolean {
    val parts = value.split(".")
    if (parts.size != 4) return false
    return parts.all { part ->
        part.toIntOrNull()?.let { it in 0..255 } == true
    }
}

/** Simple model for DoH quick-select options. */
private data class Provider(
    val id: String,
    val label: String,
    val url: String,
    val desc: String = ""
)
