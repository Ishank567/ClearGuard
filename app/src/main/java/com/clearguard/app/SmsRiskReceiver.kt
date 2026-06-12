package com.clearguard.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.clearguard.app.security.OnDeviceRuleEngine
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.blocking.HostBlocker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BroadcastReceiver for incoming SMS to enable true "incoming" tagging with Mobile Number Risk Scoring (FRI).
 * On high risk, adds to local FRI DB, logs, and can trigger notification or other actions.
 * Requires the RECEIVE_SMS permission (declared in the manifest).
 * This provides the "tag high-risk senders before transaction" for real SMS (vs just scanner screenshots).
 *
 * Realtime usecase method: Uses goAsync() + coroutine to support the full suspend MobileRiskScoringApi.queryRisk
 * (local + optional remote FRI/operator signals) in a BroadcastReceiver, which has strict time limits (~10s).
 * This is the recommended pattern for realtime async work in receivers without blocking the main thread.
 */
class SmsRiskReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsRiskReceiver"
        // Use a simple scope; in prod use a longer-lived one or inject.
        private val scope = CoroutineScope(Dispatchers.Default)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val pendingResult = goAsync()  // Critical for realtime async: extends the 10s limit.

        scope.launch {
            try {
                val bundle = intent.extras ?: return@launch
                val pdus = bundle.get("pdus") as? Array<*> ?: return@launch
                val format = bundle.getString("format")

                for (pdu in pdus) {
                    val sms = SmsMessage.createFromPdu(pdu as ByteArray, format)
                    val sender = sms.originatingAddress ?: "unknown"
                    val body = sms.messageBody ?: ""

                    Log.d(TAG, "Incoming SMS from $sender: $body")

                    // Use the FULL Mobile Risk Scoring API for realtime (supports remote if enabled)
                    try {
                        // ensureFRIDBLoaded is fast/local; queryRisk will handle remote async if toggled.
                        OnDeviceRuleEngine.ensureFRIDBLoaded(context)

                        // Call the suspend API - this can do local heuristic + remote FRI/operator query.
                        val result = OnDeviceRuleEngine.MobileRiskScoringApi.queryRisk(
                            context = context,
                            phone = sender,
                            contextText = body
                        )

                        Log.d(TAG, "SMS risk for $sender: score=${result.score}, highRisk=${result.isHighRisk}, action=${result.recommendedAction}, signals=${result.signals}")

                        if (result.isHighRisk) {
                            Log.w(TAG, "HIGH RISK SMS sender detected via realtime API: $sender (score=${result.score}). Body: $body. ${result.explanation}")

                            // Auto-seed to local FRI DB (for future offline scans)
                            OnDeviceRuleEngine.addToLocalRiskDB(sender)

                            // Realtime actions:
                            // 1. Show high-priority notification using common helper.
                            OnDeviceRuleEngine.showHighRiskPhoneNotification(context, sender, result, "Message: $body")

                            // 2. Auto-add to security blocks for downstream protection (scanner/browser).
                            val marker = "phone:$sender"
                            if (PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_SECURITY_BLOCKS, marker)) {
                                // Trigger reload so HostBlocker/VPN picks it up immediately for related domains.
                                com.clearguard.app.blocking.HostBlocker.get(context).reload()
                                com.clearguard.app.vpn.ClearGuardVpnService.reloadIfRunning(context)
                            }

                            // 3. Log to VPN activity / recent queries for unified view in Activity/Privacy screens.
                            com.clearguard.app.vpn.ClearGuardVpnService.logHighRiskPhoneEvent(sender, result.score, body, null, null)

                            // 4. Integrate with BankingGateway for UPI in SMS body (realtime payee verification).
                            val upiLink = OnDeviceRuleEngine.parseUpiLink(body)
                            if (upiLink != null) {
                                val upiVerify = OnDeviceRuleEngine.BankingGateway.verifyPayee(upiLink.vpa, upiLink.amount, body)
                                if (upiVerify.riskScore > 50 || upiVerify.recommendedAction == "BLOCK_TRANSACTION") {
                                    Log.w(TAG, "HIGH RISK UPI in SMS from high-risk sender: ${upiVerify}")
                                    // Add UPI VPA to blocks if high risk
                                    PreferenceKeys.addToStringSet(context, PreferenceKeys.KEY_SECURITY_BLOCKS, "upi:${upiLink.vpa}")
                                    com.clearguard.app.blocking.HostBlocker.get(context).reload()
                                }
                            }

                            // 5. Auto-report to federated intel + Edge Threat fabric (stub: add to shared local cache feeding risk API; in prod, anonymized upload).
                            Log.i(TAG, "AUTO-REPORT to federated intel + Edge: high-risk phone $sender score=${result.score} signals=${result.signals}")
                            OnDeviceRuleEngine.addToEdgeThreatSignal(sender) // feeds the risk API as shared cache.

                            // 6. SMS tweak: abort broadcast for high-risk to prevent other apps (note: deprecated/unreliable for security, use for demo only; can prevent delivery in some cases).
                            abortBroadcast()

                            // 7. Tie to VPN firewall: if high risk, could trigger domain blocks, but via security blocks above.
                        } else {
                            Log.d(TAG, "SMS from $sender risk=${result.score} (low per realtime API)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error scoring SMS risk with API: ${e.message}")
                        // Fallback to local only
                        val risk = OnDeviceRuleEngine.phoneRiskScore(sender, body)
                        if (OnDeviceRuleEngine.isHighRiskPhone(sender, body)) {
                            OnDeviceRuleEngine.addToLocalRiskDB(sender)
                            Log.w(TAG, "HIGH RISK (fallback local) SMS: $sender risk=$risk")
                        }
                    }
                }
            } finally {
                // Must call finish() to release the receiver.
                pendingResult.finish()
            }
        }
    }

}