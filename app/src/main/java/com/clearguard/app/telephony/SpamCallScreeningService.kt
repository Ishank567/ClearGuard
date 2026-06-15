package com.clearguard.app.telephony

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.security.OnDeviceRuleEngine
import com.clearguard.app.vpn.ClearGuardVpnService

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
        // Elder Mode hardens call screening: it treats screening as on (the Call Screening role is
        // already granted if the system bound us here), forces international-number screening
        // (anti "digital arrest"), and uses a stricter risk threshold.
        val mode = prefs.getString(
            PreferenceKeys.KEY_PROTECTION_MODE,
            PreferenceKeys.DEFAULT_PROTECTION_MODE
        )
        val elder = "elder".equals(mode, ignoreCase = true)
        val enabled = elder || prefs.getBoolean(
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
        val threshold = if (elder) ELDER_BLOCK_THRESHOLD else BLOCK_THRESHOLD
        val highRisk = listedInDb || risk >= threshold

        // Anti "digital arrest": screen calls bearing a foreign country code (forced on in Elder Mode).
        val warnIntl = elder || prefs.getBoolean(
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

        // Surface the screened call in the unified activity timeline so it appears alongside DNS
        // blocks and gets a plain-language explanation in the Domain Inspector. The number is only
        // logged to the in-memory activity list — it never leaves the device.
        val context = when {
            foreign && !highRisk -> "International caller screened (possible spoofed scam)"
            listedInDb -> "Listed in local fraud-risk database"
            else -> "High on-device fraud-risk score"
        }
        ClearGuardVpnService.logHighRiskPhoneEvent(number, risk, context, "Spam Call Filter", "phone.call")

        Log.i(
            TAG,
            "Screened call (risk=$risk, listed=$listedInDb, foreign=$foreign, elder=$elder, mode=${if (reject) "reject" else "silence"})"
        )
    }

    companion object {
        private const val TAG = "SpamCallScreening"
        private const val BLOCK_THRESHOLD = 60
        // Elders are the prime target of fraud/"digital arrest" calls, so screen more aggressively.
        private const val ELDER_BLOCK_THRESHOLD = 45
    }
}
