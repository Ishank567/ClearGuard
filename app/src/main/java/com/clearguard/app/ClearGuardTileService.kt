package com.clearguard.app

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.clearguard.app.vpn.ClearGuardVpnService

/**
 * Quick Settings tile that toggles DNS protection from the notification shade.
 * If VPN consent has not been granted yet, it opens the app so the system
 * consent dialog can be shown there.
 */
class ClearGuardTileService : TileService() {

    override fun onStartListening() {
        refreshTile(ClearGuardVpnService.isRunning())
    }

    override fun onClick() {
        when {
            ClearGuardVpnService.isRunning() -> {
                ClearGuardVpnService.stop(this)
                refreshTile(false)
            }
            VpnService.prepare(this) == null -> {
                ClearGuardVpnService.start(this)
                refreshTile(true)
            }
            else -> openAppForConsent()
        }
    }

    private fun openAppForConsent() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refreshTile(active: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) "Protected" else "Paused"
        }
        tile.updateTile()
    }
}
