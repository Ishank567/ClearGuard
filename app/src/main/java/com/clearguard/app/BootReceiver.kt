package com.clearguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.clearguard.app.vpn.ClearGuardVpnService

/**
 * Restores protection after a reboot when the user had it on and has not revoked
 * VPN consent. The consent dialog cannot be shown from a receiver, so if consent
 * is missing the user simply starts protection from the app as before.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }
        val prefs = PreferenceKeys.prefs(context)
        val wantsProtection = prefs.getBoolean(PreferenceKeys.KEY_PROTECTION_DESIRED, false)
        val resumeOnBoot = prefs.getBoolean(
            PreferenceKeys.KEY_RESUME_ON_BOOT,
            PreferenceKeys.DEFAULT_RESUME_ON_BOOT
        )
        if (!wantsProtection || !resumeOnBoot) {
            return
        }
        if (VpnService.prepare(context) != null) {
            return
        }
        try {
            ClearGuardVpnService.start(context)
        } catch (ignored: RuntimeException) {
            // Some OEM builds restrict service starts from boot; the user can start manually.
        }
    }
}
