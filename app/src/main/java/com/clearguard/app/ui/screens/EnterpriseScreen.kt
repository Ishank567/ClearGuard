package com.clearguard.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.IntegrationInstructions
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.GlassCardHero
import com.clearguard.app.ui.components.LiquidGlassButton
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign
import androidx.compose.runtime.*

private data class EnterpriseFeature(
    val title: String,
    val description: String,
    val impact: String,
    val icon: ImageVector,
    val accent: Color
)

private val enterpriseFeatures = listOf(
    EnterpriseFeature(
        title = "Mobile Number Risk Scoring API",
        description = "Block or vet high-risk numbers at scale using FRI + operator signals. Realtime tagging via SmsReceiver and CallScreeningService for incoming SMS/calls (warn, auto-seed DB, add to blocks).",
        impact = "Very High",
        icon = Icons.Default.Phone,
        accent = Color(0xFF2DD4BF) // ClearColors.green
    ),
    EnterpriseFeature(
        title = "Banking Gateway Integration",
        description = "Real-time payee verification and transaction blocking using NPCI/Bank APIs.",
        impact = "Very High",
        icon = Icons.Default.AccountBalance,
        accent = Color(0xFF38BDF8) // ClearColors.blue
    ),
    EnterpriseFeature(
        title = "Edge Threat Intelligence Fabric",
        description = "Low-latency, shared indicators across partners and devices globally.",
        impact = "High",
        icon = Icons.Default.Hub,
        accent = Color(0xFF8B5CF6) // Purple
    ),
    EnterpriseFeature(
        title = "RASP and Anti-Tamper SDK",
        description = "Prevents app hooking, reverse engineering, and credential exfiltration.",
        impact = "High",
        icon = Icons.Default.VpnKey,
        accent = Color(0xFFF43F5E) // Rose/Danger
    ),
    EnterpriseFeature(
        title = "Network Enforcement Plane",
        description = "Operator-level blocking and telemetry without requiring device installation.",
        impact = "High",
        icon = Icons.Default.CellTower,
        accent = Color(0xFFF59E0B) // Amber
    ),
    EnterpriseFeature(
        title = "Voice Phishing Detection",
        description = "Detects vishing attacks in real-time for enterprises using ASR+NLP.",
        impact = "High",
        icon = Icons.Default.RecordVoiceOver,
        accent = Color(0xFF2DD4BF)
    ),
    EnterpriseFeature(
        title = "SIEM/SOAR Integration",
        description = "Enterprise incident response and automation with a dedicated SOC dashboard.",
        impact = "High",
        icon = Icons.Default.Dashboard,
        accent = Color(0xFF38BDF8)
    ),
    EnterpriseFeature(
        title = "Federated ML and Model Ops",
        description = "Privacy-preserving machine learning model updates across distributed devices.",
        impact = "High",
        icon = Icons.Default.Memory,
        accent = Color(0xFF8B5CF6)
    ),
    EnterpriseFeature(
        title = "Secure SDK for Partner Apps",
        description = "Extend network-level protections directly into banking and merchant apps.",
        impact = "High",
        icon = Icons.Default.IntegrationInstructions,
        accent = Color(0xFFF59E0B)
    ),
    EnterpriseFeature(
        title = "Hardware-backed Key Management",
        description = "Strong cryptographic protection for keys, secrets, and device attestations.",
        impact = "High",
        icon = Icons.Default.Lock,
        accent = Color(0xFFF43F5E)
    )
)

@Composable
fun EnterpriseScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ClearDesign.screenHPadding)
            .padding(top = 16.dp, bottom = 120.dp), // Extra bottom padding for scroll clearing nav bar
        verticalArrangement = Arrangement.spacedBy(ClearDesign.cardSpacing)
    ) {
        // Hero Section
        GlassCardHero(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(ClearColors.green.copy(alpha = 0.2f), ClearColors.blue.copy(alpha = 0.2f))
                            )
                        )
                        .border(1.dp, ClearColors.green.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Enterprise",
                        tint = ClearColors.green,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ClearGuard Enterprise",
                    style = MaterialTheme.typography.displaySmall,
                    color = ClearColors.text,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Advanced intelligence, zero-trust network enforcement, and ML-driven protection for organizations.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = ClearColors.muted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                LiquidGlassButton(
                    text = "Contact Sales",
                    icon = Icons.Default.Star,
                    onClick = { /* No-op for showcase */ },
                    accent = ClearColors.blue,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enterprise Capabilities",
            style = MaterialTheme.typography.titleLarge,
            color = ClearColors.text,
            modifier = Modifier.padding(start = 8.dp)
        )

        // Features List
        enterpriseFeatures.forEach { feature ->
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    // Feature Icon
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(feature.accent.copy(alpha = 0.15f))
                            .border(1.dp, feature.accent.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = feature.icon,
                            contentDescription = feature.title,
                            tint = feature.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Feature Text Content
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = feature.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = ClearColors.text,
                                modifier = Modifier.weight(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Impact Badge
                            val isVeryHigh = feature.impact == "Very High"
                            val badgeColor = if (isVeryHigh) ClearColors.green else ClearColors.blue
                            
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = feature.impact,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = feature.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = ClearColors.muted,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }
}
