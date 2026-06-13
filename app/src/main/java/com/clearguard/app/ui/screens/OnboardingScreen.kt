package com.clearguard.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.R
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
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val coroutineScope = rememberCoroutineScope()

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
                    // Premium full logo for welcome page (high-quality asset)
                    Image(
                        painter = painterResource(id = R.drawable.shield_dns_logo_full),
                        contentDescription = "ShieldDNS",
                        modifier = Modifier
                            .height(64.dp)
                            .padding(bottom = 24.dp),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(72.dp)
                            .padding(bottom = 32.dp)
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

            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 10.dp else 8.dp)
                            .padding(1.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isSelected)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.fillMaxSize()
                        ) {}
                    }
                }
            }

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
                    onClick = {
                        PreferenceKeys.prefs(context).edit()
                            .putBoolean(PreferenceKeys.KEY_ONBOARDING_SEEN, true)
                            .apply()
                        onComplete()
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Get Started")
                }
            }
        }
    }
}