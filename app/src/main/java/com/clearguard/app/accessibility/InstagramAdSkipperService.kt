package com.clearguard.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.clearguard.app.PreferenceKeys

/**
 * Best-effort Instagram ad-skipper.
 *
 * ShieldDNS is a no-root DNS filter, so it cannot remove Instagram's first-party video ads at the
 * network layer — they are served from the same domains as real content. This optional
 * AccessibilityService is the only on-device way to reach inside the *native* Instagram app: it
 * watches for the "Sponsored" label on feed and Reels ads and scrolls past them.
 *
 * Privacy & scope:
 *  - Bound only to com.instagram.android (see res/xml/instagram_ad_skipper_config.xml).
 *  - Gated behind an explicit in-app toggle ([PreferenceKeys.KEY_IG_AD_SKIPPER_ENABLED]); even when
 *    the OS keeps the service bound, it does nothing while the toggle is off.
 *  - It never reads, stores, or transmits content. It only checks for the ad label and issues a
 *    scroll on the existing scrollable container.
 *
 * It is heuristic and fragile by nature: Instagram can change its UI or wording at any time. Label
 * matching covers English plus a few common localized variants. This is intentionally conservative —
 * when unsure, it does nothing rather than scrolling the user past real posts.
 */
class InstagramAdSkipperService : AccessibilityService() {

    private var lastActionAt = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName?.toString() != INSTAGRAM_PKG) return

        val prefs = PreferenceKeys.prefs(this)
        if (!prefs.getBoolean(
                PreferenceKeys.KEY_IG_AD_SKIPPER_ENABLED,
                PreferenceKeys.DEFAULT_IG_AD_SKIPPER_ENABLED
            )
        ) return

        val now = System.currentTimeMillis()
        if (now - lastActionAt < ACTION_COOLDOWN_MS) return

        val root = rootInActiveWindow ?: return

        // The "Sponsored" label appears on both feed posts and Reels ads.
        val adLabel = findVisibleAdLabel(root) ?: return

        // Scroll the container that actually holds the ad — its nearest scrollable ancestor. This is
        // the key fix: a plain top-down search grabs the first scrollable it finds, which on the feed
        // is usually the horizontal stories tray, so the old code scrolled sideways / did nothing.
        val scrollable = findScrollableAncestor(adLabel) ?: findBestVerticalScrollable(root) ?: return

        if (!shouldSkip(scrollable, adLabel)) return

        if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            lastActionAt = now
        }
    }

    override fun onInterrupt() { /* no-op */ }

    /** First visible node whose text or content-description carries a "Sponsored" label. */
    private fun findVisibleAdLabel(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Fast path: framework text search (case-insensitive containment).
        for (label in AD_LABELS) {
            val matches = root.findAccessibilityNodeInfosByText(label) ?: continue
            for (node in matches) {
                if (node != null && node.isVisibleToUser && looksLikeAdLabel(node)) return node
            }
        }
        // Fallback: Reels sometimes expose "Sponsored" only as a content-description that the text
        // search misses, so walk the tree once and check both fields ourselves.
        return dfsFindAdLabel(root)
    }

    private fun dfsFindAdLabel(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && looksLikeAdLabel(node)) return node
        for (i in 0 until node.childCount) {
            val found = dfsFindAdLabel(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun looksLikeAdLabel(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        return AD_LABELS.any { label ->
            text?.contains(label, ignoreCase = true) == true ||
                desc?.contains(label, ignoreCase = true) == true
        }
    }

    /** Walk up from the label to the scrollable container that holds the ad (feed list / Reels pager). */
    private fun findScrollableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node?.parent
        var hops = 0
        while (current != null && hops < MAX_ANCESTOR_HOPS) {
            if (current.isScrollable) return current
            current = current.parent
            hops++
        }
        return null
    }

    /**
     * Largest *vertically* scrollable container — a fallback for when the label has no scrollable
     * ancestor. The vertical check skips the horizontal stories tray, which is also scrollable.
     */
    private fun findBestVerticalScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var best: AccessibilityNodeInfo? = null
        var bestArea = 0
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            if (node.isScrollable) {
                val b = Rect()
                node.getBoundsInScreen(b)
                if (b.height() >= b.width()) {
                    val area = b.width() * b.height()
                    if (area > bestArea) {
                        bestArea = area
                        best = node
                    }
                }
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { stack.addLast(it) }
            }
        }
        return best
    }

    /**
     * Guard against scrolling real content away. On a full-bleed vertical pager (Reels) the whole
     * item is the ad, so any visible label is safe to skip. In the feed, only act when the label is
     * in the upper region — i.e. the ad is the post in focus, not one merely peeking in from below.
     */
    private fun shouldSkip(scrollable: AccessibilityNodeInfo, adLabel: AccessibilityNodeInfo): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels
        val scrollBounds = Rect().also { scrollable.getBoundsInScreen(it) }

        val isReelsPager = scrollBounds.top <= screenHeight * REELS_EDGE_FRACTION &&
            scrollBounds.bottom >= screenHeight * (1f - REELS_EDGE_FRACTION) &&
            scrollBounds.height() >= scrollBounds.width()
        if (isReelsPager) return true

        val labelBounds = Rect().also { adLabel.getBoundsInScreen(it) }
        val cutoff = (screenHeight * VIEWPORT_FRACTION).toInt()
        return labelBounds.top in 0..cutoff
    }

    companion object {
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val ACTION_COOLDOWN_MS = 1500L
        private const val MAX_ANCESTOR_HOPS = 25
        // Feed: only act when the ad label sits within the top 70% of the screen (the focused post).
        private const val VIEWPORT_FRACTION = 0.70f
        // Reels: treat a scrollable that reaches within 6% of both top and bottom edges as the pager.
        private const val REELS_EDGE_FRACTION = 0.06f
        // "Sponsored" as Instagram localizes it. English first; a few common locales for wider reach.
        private val AD_LABELS = listOf("Sponsored", "प्रायोजित", "Patrocinado", "Patrocinada", "Commandité")
    }
}
