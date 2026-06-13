package com.clearguard.app.ui.screens

import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.theme.ClearColors

private data class FeatureTransparencyItem(
    val name: String,
    val enabled: Boolean,
    val scope: String,
    val dataUsed: String,
    val visibleIn: String,
    val lastAdded: String
)

@Composable
fun FeatureTransparencyPanel(
    context: Context,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val items = buildFeatureTransparencyItems(context)
    val visibleItems = if (compact) {
        items.filter { it.enabled }.take(6)
    } else {
        items
    }
    val enabledCount = items.count { it.enabled }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        glassAlpha = if (compact) 0.82f else 0.86f,
        elevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (compact) "Active Engines" else "Feature Transparency",
                        fontSize = if (compact) 15.sp else 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ClearColors.text
                    )
                    Text(
                        text = "$enabledCount of ${items.size} visible protections are enabled",
                        fontSize = 11.sp,
                        color = ClearColors.muted
                    )
                }
                TransparencyChip(
                    label = if (compact) "LIVE MAP" else "AUDIT",
                    color = ClearColors.blue
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                visibleItems.forEach { item ->
                    FeatureTransparencyRow(item = item, compact = compact)
                }
            }
        }
    }
}

@Composable
private fun FeatureTransparencyRow(
    item: FeatureTransparencyItem,
    compact: Boolean
) {
    val statusColor = if (item.enabled) ClearColors.green else ClearColors.muted
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = if (compact) 0.22f else 0.18f))
            .border(1.dp, statusColor.copy(alpha = if (item.enabled) 0.24f else 0.12f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = ClearColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TransparencyChip(
                label = if (item.enabled) "ON" else "OFF",
                color = statusColor
            )
        }

        Spacer(Modifier.height(6.dp))
        TransparencyLine("Scope", item.scope)
        if (!compact) {
            TransparencyLine("Uses", item.dataUsed)
            TransparencyLine("Visible", item.visibleIn)
            TransparencyLine("Added", item.lastAdded)
        } else {
            TransparencyLine("Visible", item.visibleIn)
        }
    }
}

@Composable
private fun TransparencyLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            color = ClearColors.muted,
            modifier = Modifier.weight(0.36f)
        )
        Text(
            text = value,
            fontSize = 10.5.sp,
            color = ClearColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TransparencyChip(label: String, color: Color) {
    Text(
        text = label,
        fontSize = 9.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// Add every user-visible protection or detector here when introducing it.
private fun buildFeatureTransparencyItems(context: Context): List<FeatureTransparencyItem> {
    val prefs = PreferenceKeys.prefs(context)
    val dohEnabled = prefs.getBoolean(PreferenceKeys.KEY_DOH_ENABLED, PreferenceKeys.DEFAULT_DOH_ENABLED)
    val callScreeningAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    return listOf(
        FeatureTransparencyItem(
            name = "AI Ad Pattern Detector",
            enabled = prefs.getBoolean(
                PreferenceKeys.KEY_AI_AD_PATTERN_DETECTOR_ENABLED,
                PreferenceKeys.DEFAULT_AI_AD_PATTERN_DETECTOR_ENABLED
            ),
            scope = "DNS sinkhole for ad-tech and tracker-like hosts",
            dataUsed = "Domain labels only; no page content leaves the device",
            visibleIn = "Pro Console logs as AI ad pattern and Analytics total",
            lastAdded = "0.4.x"
        ),
        FeatureTransparencyItem(
            name = "Scam Impersonation Shield",
            enabled = prefs.getBoolean(PreferenceKeys.KEY_SCAM_SHIELD_ENABLED, PreferenceKeys.DEFAULT_SCAM_SHIELD_ENABLED),
            scope = "DNS phishing, fake job, loan, payment, and brand lure checks",
            dataUsed = "Domain name, local heuristics, optional local TFLite model",
            visibleIn = "Pro Console threat rows, Domain Inspector, Analytics",
            lastAdded = "0.4.x"
        ),
        FeatureTransparencyItem(
            name = "Indian Scam Shield",
            enabled = prefs.getBoolean(
                PreferenceKeys.KEY_INDIAN_SCAM_SHIELD_ENABLED,
                PreferenceKeys.DEFAULT_INDIAN_SCAM_SHIELD_ENABLED
            ),
            scope = "UPI KYC, courier, electricity bill, job fee, APK, customer-care, digital-arrest / fake-authority, and festival / seasonal offer scams",
            dataUsed = "Domain and scanner text snippets processed on device",
            visibleIn = "Settings, Pro Console, Screenshot/Text Scanner verdicts",
            lastAdded = "0.4.x"
        ),
        FeatureTransparencyItem(
            name = "Safe Payment Checks",
            enabled = prefs.getBoolean(
                PreferenceKeys.KEY_SAFE_PAYMENT_CHECKS_ENABLED,
                PreferenceKeys.DEFAULT_SAFE_PAYMENT_CHECKS_ENABLED
            ),
            scope = "Alerts before risky UPI payments or suspicious pay links",
            dataUsed = "UPI VPA, payee name, amount, and surrounding message text; processed on device",
            visibleIn = "Privacy > Scanner and Browser UPI payment alert",
            lastAdded = "0.4.x"
        ),
        FeatureTransparencyItem(
            name = "Secure DNS over HTTPS",
            enabled = dohEnabled,
            scope = "Encrypts upstream DNS lookups when local cache/blocking does not answer",
            dataUsed = "DNS query payload sent to selected DoH provider",
            visibleIn = "DNS Monitor route, DoH query counter, Settings provider",
            lastAdded = "0.4.x"
        ),
        FeatureTransparencyItem(
            name = "Bypass Guard",
            enabled = prefs.getBoolean(
                PreferenceKeys.KEY_BYPASS_GUARD_ENABLED,
                PreferenceKeys.DEFAULT_BYPASS_GUARD_ENABLED
            ),
            scope = "Blocks known private-DNS sidestep domains",
            dataUsed = "DNS hostnames only",
            visibleIn = "Pro Console logs as BYPASS",
            lastAdded = "0.4.x"
        ),
        FeatureTransparencyItem(
            name = "On-device Rule Engine",
            enabled = prefs.getBoolean(
                PreferenceKeys.KEY_ON_DEVICE_RULE_ENGINE_ENABLED,
                PreferenceKeys.DEFAULT_ON_DEVICE_RULE_ENGINE_ENABLED
            ),
            scope = "Local regex and heuristic scoring before ML/classifier paths",
            dataUsed = "Text, URL, or domain supplied to the local scanner",
            visibleIn = "Scanner verdict details and threat log reasons",
            lastAdded = "0.4.x"
        ),
        FeatureTransparencyItem(
            name = "Screenshot/Text Scam Scanner",
            enabled = true,
            scope = "Manual scan of shared text or selected screenshots",
            dataUsed = "User-selected image/text; OCR and analysis stay local",
            visibleIn = "Privacy > Scanner",
            lastAdded = "0.4.x"
        ),
        FeatureTransparencyItem(
            name = "Spam Call Screening",
            enabled = callScreeningAvailable && prefs.getBoolean(
                PreferenceKeys.KEY_CALL_SCREENING_ENABLED,
                PreferenceKeys.DEFAULT_CALL_SCREENING_ENABLED
            ),
            scope = "Android call-screening role for high-risk numbers",
            dataUsed = "Incoming phone number and local FRI-style risk signals",
            visibleIn = "Settings counter and system call-screening behavior",
            lastAdded = "0.4.x"
        )
    )
}
