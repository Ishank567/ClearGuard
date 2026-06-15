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
 * It is heuristic and fragile by nature: Instagram can change its UI or wording at any time, and the
 * label match is English ("Sponsored"). This is intentionally conservative — when unsure, it does
 * nothing rather than scrolling the user past real posts.
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
        if (!isSponsoredOnScreen(root)) return

        val scrollable = findScrollable(root) ?: return
        if (scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
            lastActionAt = now
        }
    }

    override fun onInterrupt() { /* no-op */ }

    /**
     * True when a "Sponsored" ad label is visible in the upper part of the screen — i.e. the post
     * currently being viewed is an ad, not one merely peeking in from below. The viewport guard
     * keeps the skipper from scrolling the user past content they were actually reading.
     */
    private fun isSponsoredOnScreen(root: AccessibilityNodeInfo): Boolean {
        val screenHeight = resources.displayMetrics.heightPixels
        val cutoff = (screenHeight * VIEWPORT_FRACTION).toInt()
        for (label in AD_LABELS) {
            val matches = root.findAccessibilityNodeInfosByText(label) ?: continue
            for (node in matches) {
                if (node == null || !node.isVisibleToUser) continue
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.top in 0..cutoff) return true
            }
        }
        return false
    }

    /** Depth-first search for the first scrollable container (the feed list or the Reels pager). */
    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollable(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    companion object {
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val ACTION_COOLDOWN_MS = 1500L
        // Only act when the ad label sits within the top 70% of the screen (the focused post).
        private const val VIEWPORT_FRACTION = 0.70f
        // The exact label Instagram puts under the author on a sponsored post / Reel.
        private val AD_LABELS = listOf("Sponsored")
    }
}
