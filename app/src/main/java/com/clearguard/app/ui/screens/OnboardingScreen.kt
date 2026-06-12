package com.clearguard.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clearguard.app.ui.components.GlassCardHero
import com.clearguard.app.ui.components.LiquidGlassButton
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign
import com.clearguard.app.ui.theme.ClearMeshBackground
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

// ────────────────────────────────────────────────────────────────────────────
// Data
// ────────────────────────────────────────────────────────────────────────────

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val description: String,
    val accentColor: @Composable () -> Color
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Default.Shield,
        title = "Welcome to ShieldDNS",
        subtitle = "Your Shield. Your Rules.",
        description = "Your DNS-level firewall for a cleaner, safer internet. Block ads, trackers, and threats before they ever reach your device.",
        accentColor = { ClearColors.green }
    ),
    OnboardingPage(
        icon = Icons.Default.Dns,
        title = "DNS-Level Protection",
        subtitle = "How It Works",
        description = "ShieldDNS intercepts DNS queries to block ads, trackers, and threats before they load — making your entire device faster and safer.",
        accentColor = { ClearColors.blue }
    ),
    OnboardingPage(
        icon = Icons.Default.VpnKey,
        title = "One Permission Needed",
        subtitle = "Local VPN · Zero Logging",
        description = "ShieldDNS creates a local VPN on your device to filter DNS traffic. No remote servers, no data leaves your phone — everything stays private.",
        accentColor = { ClearColors.green }
    ),
    OnboardingPage(
        icon = Icons.Default.CheckCircle,
        title = "You're All Set",
        subtitle = "Protection Awaits",
        description = "Tap below to activate your shield and start browsing a cleaner, faster, and safer internet.",
        accentColor = { ClearColors.green }
    )
)

// ────────────────────────────────────────────────────────────────────────────
// Main Screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Haptic feedback on page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
    }

    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(modifier = Modifier.fillMaxSize()) {
        // Animated mesh gradient background
        ClearMeshBackground(darkTheme = isDark)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // ── Top bar: page counter + Skip ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClearDesign.screenHPadding, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${pages.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ClearColors.muted
                )

                // Skip button (hidden on last page)
                AnimatedVisibility(
                    visible = !isLastPage,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    LiquidGlassButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pages.lastIndex,
                                    animationSpec = tween(500, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Text(
                            text = "Skip",
                            style = MaterialTheme.typography.labelLarge,
                            color = ClearColors.muted
                        )
                    }
                }
            }

            // ── Pager ──
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                beyondViewportPageCount = 1
            ) { pageIndex ->
                val page = pages[pageIndex]

                // Parallax / scale based on offset from settled position
                val pageOffset = ((pagerState.currentPage - pageIndex)
                        + pagerState.currentPageOffsetFraction).absoluteValue

                val scale by animateFloatAsState(
                    targetValue = 1f - (pageOffset * 0.08f).coerceIn(0f, 0.08f),
                    animationSpec = tween(300),
                    label = "pageScale"
                )
                val alpha by animateFloatAsState(
                    targetValue = 1f - (pageOffset * 0.5f).coerceIn(0f, 0.5f),
                    animationSpec = tween(300),
                    label = "pageAlpha"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    contentAlignment = Alignment.Center
                ) {
                    OnboardingPageContent(
                        page = page,
                        isLastPage = pageIndex == pages.lastIndex,
                        onActivate = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onComplete()
                        }
                    )
                }
            }

            // ── Dot Indicator ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    val selected = pagerState.currentPage == index
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 28.dp else 8.dp,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
                        label = "dotWidth"
                    )
                    val dotAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0.35f,
                        animationSpec = tween(300),
                        label = "dotAlpha"
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                ClearColors.green.copy(alpha = dotAlpha)
                            )
                    )
                }
            }

            // ── Bottom: Next / Get Started button ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = ClearDesign.screenHPadding,
                        vertical = 16.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isLastPage) {
                    LiquidGlassButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onComplete()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accent = ClearColors.green,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 32.dp,
                            vertical = 16.dp
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Activate Shield",
                            style = MaterialTheme.typography.titleMedium,
                            color = ClearColors.green
                        )
                    }
                } else {
                    LiquidGlassButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    pagerState.currentPage + 1,
                                    animationSpec = tween(400, easing = FastOutSlowInEasing)
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 32.dp,
                            vertical = 16.dp
                        )
                    ) {
                        Text(
                            text = "Continue",
                            style = MaterialTheme.typography.titleMedium,
                            color = ClearColors.green
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Individual page content
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    isLastPage: Boolean,
    onActivate: () -> Unit
) {
    val accent = page.accentColor()
    val infiniteTransition = rememberInfiniteTransition(label = "iconPulse")

    // Gentle floating animation for the icon
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // Glow pulse behind the icon
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = ClearDesign.screenHPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ── Hero Glass Card ──
        GlassCardHero(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Icon with glow backdrop
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.graphicsLayer {
                        translationY = floatOffset
                    }
                ) {
                    // Glow circle behind icon
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        accent.copy(alpha = glowAlpha),
                                        accent.copy(alpha = glowAlpha * 0.3f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    // Icon
                    Icon(
                        imageVector = page.icon,
                        contentDescription = page.title,
                        modifier = Modifier.size(56.dp),
                        tint = accent
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Subtitle label
                Text(
                    text = page.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Title
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = ClearColors.text,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Description
                Text(
                    text = page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = ClearColors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Extra accent line for the last page
                if (isLastPage) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        ClearColors.green.copy(alpha = 0.6f),
                                        ClearColors.blue.copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}
