package com.clearguard.app.telephony

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.security.OnDeviceRuleEngine

/**
 * Spam Call Filter — screens incoming calls fully on-device.
 *
 * Bound by the system when the user grants ShieldDNS the Call Screening role
 * (RoleManager.ROLE_CALL_SCREENING, Android 10+; requested from Settings).
 * Scores the caller with the local FRI risk DB + phone heuristics from
 * OnDeviceRuleEngine. High-risk callers are silenced (default) or rejected,
 * depending on the user's setting. No number ever leaves the device.
 */
class SpamCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // Only screen incoming calls (callDirection is available from API 29,
        // same level as the Call Screening role itself).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            callDetails.callDirection != Call.Details.DIRECTION_INCOMING
        ) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val prefs = PreferenceKeys.prefs(this)
        val enabled = prefs.getBoolean(
            PreferenceKeys.KEY_CALL_SCREENING_ENABLED,
            PreferenceKeys.DEFAULT_CALL_SCREENING_ENABLED
        )
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        if (!enabled || number.isBlank()) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        OnDeviceRuleEngine.ensureFRIDBLoaded(this)
        val risk = OnDeviceRuleEngine.phoneRiskScore(number)
        val listedInDb = OnDeviceRuleEngine.isInLocalRiskDB(number)
        val highRisk = listedInDb || risk >= BLOCK_THRESHOLD

        // Anti "digital arrest": optionally screen calls bearing a foreign country code.
        val warnIntl = prefs.getBoolean(
            PreferenceKeys.KEY_WARN_INTERNATIONAL_CALLS,
            PreferenceKeys.DEFAULT_WARN_INTERNATIONAL_CALLS
        )
        val foreign = warnIntl && OnDeviceRuleEngine.isForeignNumber(number)

        if (!highRisk && !foreign) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        // Only honour the user's "reject" preference for genuinely high-risk numbers; a call flagged
        // solely because it is international is silenced (never hard-rejected) to limit collateral.
        val reject = highRisk && prefs.getBoolean(
            PreferenceKeys.KEY_CALL_SCREENING_REJECT,
            PreferenceKeys.DEFAULT_CALL_SCREENING_REJECT
        )
        val response = CallResponse.Builder().apply {
            if (reject) {
                setDisallowCall(true)
                setRejectCall(true)
                setSkipNotification(true)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setSilenceCall(true)
            }
        }.build()
        respondToCall(callDetails, response)

        val counterKey = if (highRisk) {
            PreferenceKeys.KEY_SPAM_CALLS_BLOCKED
        } else {
            PreferenceKeys.KEY_INTL_CALLS_SCREENED
        }
        prefs.edit()
            .putLong(counterKey, prefs.getLong(counterKey, 0L) + 1)
            .apply()
        Log.i(
            TAG,
            "Screened call (risk=$risk, listed=$listedInDb, foreign=$foreign, mode=${if (reject) "reject" else "silence"})"
        )
    }

    companion object {
        private const val TAG = "SpamCallScreening"
        private const val BLOCK_THRESHOLD = 60
    }
}
