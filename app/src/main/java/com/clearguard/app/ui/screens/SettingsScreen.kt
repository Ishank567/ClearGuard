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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.theme.ClearColors

@Composable
fun SettingsScreen(
    isProtected: Boolean,
    onProtectionChange: (Boolean) -> Unit
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
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Resolver", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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
                Button(
                    onClick = {
                        val value = resolver.trim()
                        if (isIpv4Address(value)) {
                            prefs.edit().putString(PreferenceKeys.KEY_UPSTREAM_DNS, value).apply()
                            resolver = value
                            resolverMessage = "Saved"
                        } else {
                            resolverMessage = "Enter a valid IPv4 resolver"
                        }
                    }
                ) {
                    Text("Save Resolver")
                }
                if (resolverMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(resolverMessage, fontSize = 12.sp, color = ClearColors.muted)
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
