package com.clearguard.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.clearguard.app.ui.components.AnimatedFeatureIcon
import com.clearguard.app.ui.components.AnimatedPageIndicator
import com.clearguard.app.ui.components.AnimatedShieldLogo
import com.clearguard.app.ui.components.BubbleGlassBackground
import com.clearguard.app.ui.components.rememberAnimationsEnabled
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector?,
    val title: String,
    val subtitle: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage(
        icon = null,
        title = "Welcome to ShieldDNS",
        subtitle = "Your Shield. Your Rules.",
        description = "Your DNS-level firewall for a cleaner, safer internet. Block ads, trackers, and threats before they ever reach your device."
    ),
    OnboardingPage(
        icon = Icons.Default.Dns,
        title = "DNS-Level Protection",
        subtitle = "How It Works",
        description = "ShieldDNS intercepts DNS queries to block ads, trackers, and threats before they load — making your entire device faster and safer."
    ),
    OnboardingPage(
        icon = Icons.Default.VpnKey,
        title = "One Permission Needed",
        subtitle = "Local VPN · Zero Logging",
        description = "ShieldDNS creates a local VPN on your device to filter DNS traffic. No remote servers, no data leaves your phone — everything stays private."
    ),
    OnboardingPage(
        icon = Icons.Default.CheckCircle,
        title = "You're All Set",
        subtitle = "Protection Awaits",
        description = "Tap below to activate your shield and start browsing a cleaner, faster, and safer internet."
    )
)

@Composable
fun OnboardingScreen(onComplete: (startProtection: Boolean) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()
    val animationsEnabled = rememberAnimationsEnabled()

    BubbleGlassBackground(
        modifier = Modifier.fillMaxSize(),
        active = true,
        animate = animationsEnabled
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top progress
        LinearProgressIndicator(
            progress = { (pagerState.currentPage + 1) / pages.size.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 32.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val item = pages[page]

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (item.icon == null) {
                    // Vector launcher mark — avoids decoding large PNGs on low-RAM devices at first launch.
                    AnimatedShieldLogo(
                        diameter = 96.dp,
                        accent = MaterialTheme.colorScheme.primary,
                        active = true,
                        animate = animationsEnabled,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                } else {
                    AnimatedFeatureIcon(
                        icon = item.icon,
                        modifier = Modifier.padding(bottom = 24.dp),
                        size = 96.dp,
                        animate = animationsEnabled
                    )
                }

                Text(
                    text = item.title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = 20.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(0.9f),
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bottom controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (pagerState.currentPage > 0) {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                        }
                    }
                ) {
                    Text("Back")
                }
            } else {
                Spacer(Modifier.width(64.dp))
            }

            AnimatedPageIndicator(
                pageCount = pages.size,
                currentPage = pagerState.currentPage
            )

            if (pagerState.currentPage < pages.size - 1) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next")
                }
            } else {
                Button(
                    onClick = { onComplete(true) },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Activate Protection")
                }
            }
        }

        // On the final page, let users who want to look around first skip activation.
        if (pagerState.currentPage == pages.size - 1) {
            TextButton(
                onClick = { onComplete(false) },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    "Skip for now — I'll start it myself",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
    }
}