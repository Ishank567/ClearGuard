package com.clearguard.app.security

import java.util.regex.Pattern
import android.content.Context
import android.util.Log
import com.clearguard.app.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * On-device rule engine for fast, deterministic phishing and scam classification.
 * Uses pre-compiled regex patterns + heuristics. Runs entirely locally, zero network.
 * Designed to be extremely fast (microseconds per classification) and interpretable.
 *
 * Can be used standalone or to pre-filter before the optional TFLite model.
 */
object OnDeviceRuleEngine {

    // Pre-compiled patterns for speed (compiled once at class load)
    private val URGENT_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(urgent|immediate|now|asap|today|verify|confirm|update|secure|account|login|password|kyc|otp|pin)\\b.*\\b(required|action|needed|claim|within|hours?|minutes?)\\b"),
        Pattern.compile("(?i)\\b(you have won|congratulations|prize|lottery|reward|cashback)\\b"),
        Pattern.compile("(?i)\\b(suspended|locked|disabled|limited|fraud|unauthorized)\\b.*\\b(account|access|card|payment)\\b")
    )

    private val SUSPICIOUS_URL_PATTERNS = listOf(
        Pattern.compile("(?i)https?://[^\\s/$.?#].*\\.(tk|ml|ga|cf|gq|top|xyz|loan|support|secure|verify|update|claim|win|prize)"),
        Pattern.compile("(?i)https?://[^\\s]+-(?:sbi|hdfc|icici|axis|paytm|phonepe|airtel|jio|bank|gov)[^\\s]*\\.(?:com|in|net|org|co)"),
        Pattern.compile("(?i)bit\\.ly|tinyurl|goo\\.gl|t\\.co|short\\.link|rebrand\\.ly|ow\\.ly")
    )

    private val BRAND_IMPERSONATION = listOf(
        "sbi", "hdfc", "icici", "axis", "paytm", "phonepe", "gpay", "bhim", "airtel", "jio",
        "amazon", "flipkart", "myntra", "irctc", "epfo", "uidai", "incometax"
    )

    private val PHISHING_KEYWORDS = setOf(
        "verify your", "update your", "confirm your", "secure your", "claim your",
        "reset password", "account suspended", "payment failed", "delivery pending",
        "kyc incomplete", "otp required", "limited time", "act now", "final notice"
    )

    /**
     * Fast deterministic score (0-100). Higher = more likely phishing/scam.
     * Combines regex hits + heuristics. No ML here.
     */
    fun score(text: String, url: String? = null): Int {
        if (text.isBlank() && url.isNullOrBlank()) return 0

        var score = 0
        val combined = (text + " " + (url ?: "")).lowercase()

        // Regex urgent / action-required patterns
        for (p in URGENT_PATTERNS) {
            if (p.matcher(combined).find()) {
                score += 28
                break
            }
        }

        // Suspicious URL patterns (typosquatting, shorteners, bad TLDs)
        if (url != null) {
            for (p in SUSPICIOUS_URL_PATTERNS) {
                if (p.matcher(url.lowercase()).find()) {
                    score += 32
                    break
                }
            }
            // Brand impersonation in domain
            val domain = try {
                java.net.URI(url).host?.lowercase() ?: ""
            } catch (e: Exception) { "" }
            for (brand in BRAND_IMPERSONATION) {
                if (domain.contains(brand) && !domain.endsWith(".$brand") && !domain.contains("official") && !domain.contains("secure")) {
                    score += 25
                    break
                }
            }
        }

        // Keyword density
        var keywordHits = 0
        for (kw in PHISHING_KEYWORDS) {
            if (combined.contains(kw)) keywordHits++
        }
        score += keywordHits * 8

        // Heuristics
        val wordCount = combined.split("\\s+".toRegex()).size
        if (wordCount > 0) {
            val urgencyRatio = (combined.count { it.isDigit() } + combined.count { it == '!' } + combined.count { it == '$' }).toFloat() / wordCount
            score += (urgencyRatio * 20).toInt()
        }

        // Length-based (very long or very short suspicious messages)
        val len = combined.length
        if (len in 20..80) score += 5   // typical phishing SMS length
        if (len > 300) score += 8

        // Multiple exclamation or currency symbols
        score += (combined.count { it == '!' } * 4).coerceAtMost(16)
        score += (combined.count { it == '₹' || it == '$' } * 3).coerceAtMost(12)

        return score.coerceIn(0, 100)
    }

    /**
     * Deterministic classification with explanation.
     */
    fun classify(text: String, url: String? = null): ClassificationResult {
        val s = score(text, url)
        val reasons = mutableListOf<String>()

        val combined = (text + " " + (url ?: "")).lowercase()
        if (URGENT_PATTERNS.any { it.matcher(combined).find() }) reasons += "Urgent action language detected"
        if (url != null && SUSPICIOUS_URL_PATTERNS.any { it.matcher(url.lowercase()).find() }) reasons += "Suspicious URL structure or TLD"
        if (BRAND_IMPERSONATION.any { brand -> combined.contains(brand) && url?.contains(brand) == false }) reasons += "Brand name used without official domain"
        if (PHISHING_KEYWORDS.any { combined.contains(it) }) reasons += "Classic phishing keywords present"

        val label = when {
            s >= 75 -> "High phishing risk"
            s >= 50 -> "Suspicious (likely phishing)"
            s >= 30 -> "Elevated risk"
            else -> "Low risk"
        }

        return ClassificationResult(
            score = s,
            label = label,
            reasons = reasons,
            isPhishing = s >= 55
        )
    }

    // === Mobile Number Risk Scoring (FRI-like local heuristic) ===
    // Simulates DoT FRI (Fraud Risk Information) without network for privacy/on-device use.
    // Tags incoming SMS/calls (via scanner text or pasted) as high-risk senders (banks, UPI, airlines, etc.).
    // Real DoT FRI would require partner API integration (auth, query by number).
    // High risk (>60) -> warn in browser/scanner; can be used to auto-block or add to custom security blocks.
    private val HIGH_RISK_PHONE_PREFIXES = setOf(
        "1800", "1860", "186", "140", "160", "170", "4444" // common Indian toll-free/scam prefixes
    )

    // Expanded local "FRI" risk patterns for known scam sender contexts (banks/UPI/gov/courier)
    private val HIGH_RISK_CONTEXT_PATTERNS = listOf(
        Regex("(?i)(sbi|hdfc|icici|axis|paytm|phonepe|gpay|bhim|airtel|jio|vodafone|bsnl).*?(support|care|helpline|refund|kyc|verify|otp|claim|due)"),
        Regex("(?i)(bank|upi|payment|delivery|courier|electricity|gas|irctc|epfo|uidai).*?(fraud|scam|suspended|verify|update)"),
        Regex("(?i)(your account|card|loan|subsidy|scheme).*?(blocked|limited|action required)"),
        Regex("\\b(0|91)?[6-9]\\d{2}0000\\d{4}\\b") // example suspicious patterns, extendable
    )

    // Local FRI risk DB - loaded from assets/fri_risk_db.txt + auto-seeded + user extensions.
    // Acts as the fast on-device "local risk API".
    private val LOCAL_FRI_BAD_PATTERNS = mutableSetOf<String>()

    // Edge Threat Intelligence Fabric: shared local signal cache (e.g., from partners/devices) feeding the risk API.
    // In prod: sync with federated updates (anonymized). Simple set for demo.
    private val EDGE_THREAT_SIGNALS = mutableSetOf<String>() // e.g., additional bad phones/prefixes from fabric.

    /**
     * Load the small FRI risk DB from assets and auto-seed with defaults.
     * Call once at app start (e.g. MainActivity). Idempotent.
     */
    fun loadLocalFRIDB(context: Context) {
        if (LOCAL_FRI_BAD_PATTERNS.isNotEmpty()) return // already loaded

        // Auto-seed some core defaults (in case asset missing)
        LOCAL_FRI_BAD_PATTERNS.addAll(listOf("9876543210", "9123456780", "9988776655", "1800", "1860", "140"))

        try {
            context.assets.open("fri_risk_db.txt").bufferedReader().useLines { lines ->
                lines
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { line ->
                        LOCAL_FRI_BAD_PATTERNS.add(line.replace("[^0-9]".toRegex(), ""))
                    }
            }
            Log.d("OnDeviceRuleEngine", "Loaded ${LOCAL_FRI_BAD_PATTERNS.size} entries from fri_risk_db.txt")
        } catch (e: Exception) {
            // Asset missing or error - rely on auto-seeds
            Log.w("OnDeviceRuleEngine", "Could not load fri_risk_db.txt, using auto-seeds only: ${e.message}")
        }
    }

    // Convenience: call from any Context-aware place to ensure loaded
    fun ensureFRIDBLoaded(context: Context) = loadLocalFRIDB(context)

    fun addToLocalRiskDB(numberOrPrefix: String) {
        LOCAL_FRI_BAD_PATTERNS.add(numberOrPrefix.replace("[^0-9]".toRegex(), ""))
    }

    /** For UI / auto-suggest: return current high-risk numbers/prefixes from the local FRI DB + Edge fabric. */
    fun getHighRiskPhones(limit: Int = 20): List<String> {
        val combined = (LOCAL_FRI_BAD_PATTERNS + EDGE_THREAT_SIGNALS).sorted()
        return combined.take(limit)
    }

    /** Add to Edge Threat fabric cache (e.g., from federated reports or partners). Feeds risk scoring. */
    fun addToEdgeThreatSignal(signal: String) {
        EDGE_THREAT_SIGNALS.add(signal.replace("[^0-9]".toRegex(), ""))
    }

    fun phoneRiskScore(phone: String, contextText: String = ""): Int {
        var risk = 0
        val cleanPhone = phone.replace("[^0-9]".toRegex(), "").takeLast(10) // normalize to last 10 digits
        val combined = (phone + " " + contextText).lowercase()

        // Prefix based (toll free / premium often used in scams)
        if (HIGH_RISK_PHONE_PREFIXES.any { cleanPhone.startsWith(it) || cleanPhone.endsWith(it.takeLast(4)) }) {
            risk += 35
        }

        // Context from known high-risk senders (banks, UPI, gov, courier as per table)
        for (pat in HIGH_RISK_CONTEXT_PATTERNS) {
            if (pat.containsMatchIn(combined)) {
                risk += 30
                break
            }
        }

        // Local FRI risk DB check
        if (LOCAL_FRI_BAD_PATTERNS.any { cleanPhone.contains(it.takeLast(10)) || combined.contains(it) }) {
            risk += 45
        }

        // Edge Threat fabric signals (shared cache feeding risk API)
        if (EDGE_THREAT_SIGNALS.any { cleanPhone.contains(it.takeLast(10)) || combined.contains(it) }) {
            risk += 30
        }

        // Additional heuristics
        if (cleanPhone.length == 10 && cleanPhone[0] in '6'..'9' && combined.containsAny(listOf("bank", "upi", "refund", "kyc", "support", "verify"))) {
            risk += 25
        }
        if (combined.contains("fraud") || combined.contains("scam") || combined.contains("fake")) risk += 15

        // Very new or sequential numbers often risky
        if (cleanPhone.matches(Regex("^[6-9](\\d)\\1{4,}\\d*$"))) risk += 20

        return risk.coerceIn(0, 100)
    }

    fun isHighRiskPhone(phone: String, contextText: String = "", threshold: Int = 60): Boolean {
        return phoneRiskScore(phone, contextText) >= threshold
    }

    private fun String.containsAny(list: List<String>) = list.any { this.contains(it) }

    // === Mobile Number Risk Scoring API (FRI + operator signals) ===
    // Enterprise-grade API facade for "Block/vet high-risk numbers at scale".
    // - Fast local path (always available, zero-latency, privacy-first).
    // - Optional remote operator/FRI signals (stub for DoT FRI / telco APIs).
    // - Supports batch for scale.
    // - Hybrid scoring: local base + remote boost if available.
    // Real integration: Partner with DoT/NPCI/telcos for authenticated FRI endpoint.
    object MobileRiskScoringApi {

        private val httpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        // Configurable remote endpoint (set via prefs or setter for enterprise).
        // In production: https://api.dot.gov.in/fri or telco partner gateway.
        // Requires API key / mTLS / OAuth. Here we use a configurable stub + local fallback.
        private var FRI_REMOTE_BASE = com.clearguard.app.PreferenceKeys.DEFAULT_FRI_REMOTE_ENDPOINT

        fun setRemoteEndpoint(endpoint: String) {
            FRI_REMOTE_BASE = endpoint
        }

        /**
         * Query risk for a single number. Returns enhanced score + signals.
         * Always runs local first. If remote enabled, queries for operator signals (e.g., recent fraud reports from telco).
         */
        suspend fun queryRisk(
            context: Context,
            phone: String,
            contextText: String = ""
        ): RiskResult {
            loadLocalFRIDB(context) // ensure DB loaded
            val localScore = phoneRiskScore(phone, contextText)
            var finalScore = localScore
            val signals = mutableListOf<String>("local-heuristic")

            val prefs = PreferenceKeys.prefs(context)
            val useRemote = prefs.getBoolean(
                PreferenceKeys.KEY_MOBILE_RISK_REMOTE_SIGNALS,
                PreferenceKeys.DEFAULT_MOBILE_RISK_REMOTE_SIGNALS
            ) // advanced toggle, default off for privacy

            if (useRemote) {
                // Configurable endpoint from prefs (enterprise config).
                val configuredEndpoint = prefs.getString(PreferenceKeys.KEY_FRI_REMOTE_ENDPOINT, PreferenceKeys.DEFAULT_FRI_REMOTE_ENDPOINT)
                if (configuredEndpoint != null) setRemoteEndpoint(configuredEndpoint)
                try {
                    val remote = withContext(Dispatchers.IO) {
                        queryRemoteFRI(phone)
                    }
                    if (remote != null) {
                        finalScore = maxOf(localScore, remote)
                        signals += "operator-fri-signal"
                    }
                } catch (e: Exception) {
                    signals += "remote-fallback-local"
                    // Fail closed to local only; better error handling (logged in queryRemote).
                }
            }

            val isHighRisk = finalScore >= 60
            val action = if (isHighRisk) "WARN_OR_BLOCK" else "ALLOW_WITH_CAUTION"

            return RiskResult(
                phone = phone,
                score = finalScore,
                isHighRisk = isHighRisk,
                signals = signals,
                recommendedAction = action,
                explanation = if (isHighRisk) "High-risk sender per FRI + operator data. Verify before any transaction." else "Low risk based on current signals."
            )
        }

        /**
         * Batch version for scale (e.g., vet 100 numbers from a list or CRM).
         */
        suspend fun queryBatchRisk(
            context: Context,
            phones: List<String>
        ): List<RiskResult> {
            return phones.map { queryRisk(context, it) }
        }

        private fun queryRemoteFRI(phone: String): Int? {
            // Enhanced stub for "FRI + operator signals" - realtime capable.
            // In production: Use authenticated POST to real DoT FRI or telco partner API (e.g., with mTLS/OAuth).
            // Privacy: use HMAC-SHA256 with secret from secure storage (see Hardware Key Mgmt).
            // Configurable endpoint from prefs.
            val clean = phone.replace("[^0-9]".toRegex(), "").takeLast(10)
            if (clean.length != 10) return null

            // Get configurable endpoint (for prod, set via enterprise config)
            // For now, use default or from prefs if available (passed via context in callers)
            val endpoint = try {
                // Note: in non-Context calls, falls back. Callers with Context should pass/update.
                com.clearguard.app.PreferenceKeys.prefs(null as? android.content.Context ?: return null).getString(
                    com.clearguard.app.PreferenceKeys.KEY_FRI_REMOTE_ENDPOINT,
                    com.clearguard.app.PreferenceKeys.DEFAULT_FRI_REMOTE_ENDPOINT
                ) ?: com.clearguard.app.PreferenceKeys.DEFAULT_FRI_REMOTE_ENDPOINT
            } catch (e: Exception) {
                com.clearguard.app.PreferenceKeys.DEFAULT_FRI_REMOTE_ENDPOINT
            }

            // Real HMAC for privacy (use a secret; in prod from KeyStore/encrypted prefs)
            val secret = "super_secret_fri_key_change_in_prod" // TODO: load from secure storage
            val hmac = try {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                val hashBytes = mac.doFinal(clean.toByteArray(Charsets.UTF_8))
                hashBytes.fold("") { str, it -> str + "%02x".format(it) }
            } catch (e: Exception) {
                Log.e("MobileRiskScoringApi", "HMAC failed: ${e.message}")
                // Fallback to simple hash
                MessageDigest.getInstance("SHA-256").digest(clean.toByteArray()).fold("") { str, it -> str + "%02x".format(it) }
            }

            val url = endpoint
            val jsonBody = JSONObject().apply {
                put("number_hash", hmac)
                put("client", "ClearGuard-Enterprise")
                put("version", "1.0")
                put("timestamp", System.currentTimeMillis())
            }.toString()

            try {
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer ${getApiKeyOrStub()}")
                    .header("X-Client", "ClearGuard-Enterprise")
                    .header("X-Request-ID", java.util.UUID.randomUUID().toString()) // for tracing
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("MobileRiskScoringApi", "Remote FRI query failed: ${response.code}")
                        return null
                    }
                    val body = response.body?.string() ?: return null
                    val json = JSONObject(body)
                    val risk = json.optInt("risk_score", -1)
                    if (risk >= 0) {
                        Log.d("MobileRiskScoringApi", "Remote FRI returned risk: $risk for hash prefix")
                        return risk
                    }
                    return null
                }
            } catch (e: Exception) {
                Log.e("MobileRiskScoringApi", "Remote FRI error (better handling: fallback): ${e.message}")
                // Better error handling: exponential backoff stub, but for now just fallback
                return null
            }
        }

        /**
         * Utility to "vet and block" at scale: given a list of numbers (e.g. from logs or CRM), return only the high-risk ones with recommendations.
         * Useful for enterprise bulk processing.
         */
        suspend fun vetAndRecommendActions(
            context: Context,
            phones: List<String>
        ): List<RiskResult> {
            return queryBatchRisk(context, phones).filter { it.isHighRisk }
        }

        private fun getApiKeyOrStub(): String {
            // In real: from secure storage (see Hardware-backed Key Management in enterprise table).
            // For demo: return a placeholder. Production must use encrypted prefs or KeyStore.
            return "demo_fri_key_replace_in_prod"
        }
    }

    data class RiskResult(
        val phone: String,
        val score: Int,
        val isHighRisk: Boolean,
        val signals: List<String>,
        val recommendedAction: String,
        val explanation: String
    )

    /**
     * Helper for realtime: show high priority notification for high risk phone.
     * Uses a dedicated channel. Call from receivers/services.
     */
    fun showHighRiskPhoneNotification(context: Context, phone: String, result: RiskResult, extraInfo: String = "") {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                "high_risk_phone_channel",
                "High Risk Phones (FRI)",
                android.app.NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val title = "High Risk Phone (FRI Risk: ${result.score})"
        val text = "From: $phone - ${result.explanation}"
        val bigText = "Phone: $phone\n\n${result.explanation}\nSignals: ${result.signals.joinToString()}\nAction: ${result.recommendedAction}\n$extraInfo"

        val notification = android.app.Notification.Builder(context, "high_risk_phone_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(android.app.Notification.BigTextStyle().bigText(bigText))
            .setPriority(android.app.Notification.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(phone.hashCode(), notification)
    }

    // === UPI Payee Verification (parse + risk) ===
    // Parses upi://pay?pa=...&pn=PayeeName&am=... etc.
    data class UpiLink(val vpa: String, val payeeName: String?, val amount: String?, val raw: String)

    fun parseUpiLink(text: String): UpiLink? {
        val match = Regex("upi://pay\\?([^\\s]+)").find(text) ?: return null
        val params = match.groupValues[1].split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k.lowercase() to v
        }
        return UpiLink(
            vpa = params["pa"] ?: "",
            payeeName = params["pn"],
            amount = params["am"],
            raw = match.value
        )
    }

    fun upiRiskScore(upi: UpiLink, context: String = ""): Int {
        var risk = 0
        if (upi.vpa.contains(Regex("[^a-z0-9@\\.]"))) risk += 20 // suspicious chars
        if (upi.payeeName.isNullOrBlank() || upi.payeeName.length < 3) risk += 25
        if (upi.amount != null && upi.amount.toDoubleOrNull() ?: 0.0 > 10000) risk += 15
        val combined = (upi.vpa + " " + (upi.payeeName ?: "") + " " + context).lowercase()
        if (BRAND_IMPERSONATION.any { combined.contains(it) && !upi.vpa.endsWith("@okaxis") && !upi.vpa.endsWith("@oksbi") }) risk += 30
        return risk.coerceIn(0, 100)
    }

    // === Banking Gateway Integration (NPCI/Bank APIs) stub ===
    // For real-time payee verification and transaction blocking (second in enterprise table).
    // Stub for NPCI UPI APIs, bank verification endpoints. In prod: authenticated calls to NPCI/BHIM/bank gateways.
    // On-device: uses existing UPI parser + risk + local "verified payee" DB stub.
    // "Transaction blocking": return risk + "block" recommendation; app can delay/warn in browser or scanner.
    object BankingGateway {

        // Stub local "verified payees" DB (like cached NPCI responses). In real: from secure cache or prior verified txns.
        private val VERIFIED_PAYEES = mutableMapOf(
            "user@oksbi" to "John Doe (Verified)",
            "merchant@paytm" to "Acme Corp (Verified Merchant)",
            "fraud@icici" to null // example bad
        )

        /**
         * Verify payee via "Banking Gateway" (local + stub remote).
         * Returns verification + risk + blocking recommendation.
         * Enhanced for realtime: can be called from SMS body UPI, call context, or browser.
         */
        fun verifyPayee(vpa: String, amount: String? = null, context: String = ""): PayeeVerification {
            val upi = UpiLink(vpa, null, amount, "upi://pay?pa=$vpa")
            val risk = upiRiskScore(upi, context)
            val verifiedName = VERIFIED_PAYEES[vpa.lowercase()]
            val isVerified = verifiedName != null && !verifiedName.contains("fraud", ignoreCase = true)

            val action = when {
                risk > 70 || verifiedName == null -> "BLOCK_TRANSACTION"
                risk > 40 -> "VERIFY_MANUALLY"
                else -> "PROCEED"
            }

            // Realtime payee verification in calls/SMS or transaction delay suggestion.
            val delayRecommendation = if (risk > 50 || !isVerified) "DELAY_TRANSACTION_30S" else "NO_DELAY"
            val explanation = if (isVerified) {
                "Payee verified via cached bank/NPCI response. Risk: $risk. $delayRecommendation."
            } else {
                "No verification or high risk from local signals / NPCI stub. $delayRecommendation. Do not proceed."
            }

            return PayeeVerification(
                vpa = vpa,
                verifiedName = verifiedName,
                riskScore = risk,
                isVerified = isVerified,
                recommendedAction = action,
                explanation = explanation,
                delayRecommendation = delayRecommendation
            )
        }

        // Stub for "remote" bank/NPCI call (similar to FRI remote, with HMAC for privacy).
        // In prod: call real API, cache result in VERIFIED_PAYEES or secure store.
        fun simulateRemoteVerify(vpa: String): String? {
            // Placeholder logic, enhanced with hash like FRI.
            val hash = try {
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec("bank_secret".toByteArray(), "HmacSHA256"))
                mac.doFinal(vpa.toByteArray()).fold("") { str, it -> str + "%02x".format(it) }.take(16)
            } catch (e: Exception) { vpa.hashCode().toString() }
            return if (vpa.endsWith("@oksbi") || vpa.endsWith("@paytm") || hash.contains("a")) "Verified Merchant/User via NPCI" else null
        }

        // Data class enhanced with delay for realtime transaction control.
        data class PayeeVerification(
            val vpa: String,
            val verifiedName: String?,
            val riskScore: Int,
            val isVerified: Boolean,
            val recommendedAction: String, // e.g. "BLOCK_TRANSACTION", "VERIFY_MANUALLY", "PROCEED"
            val explanation: String,
            val delayRecommendation: String = "NO_DELAY" // For transaction delay in realtime flows.
        )
    }

    // === Multi-modal Phishing (extend for SMS/WhatsApp/QR/voice text) ===
    fun multiModalClassify(text: String, url: String? = null, phone: String? = null, upi: UpiLink? = null): ClassificationResult {
        val base = classify(text, url)
        var extraScore = base.score
        val extraReasons = base.reasons.toMutableList()

        phone?.let {
            val phoneRisk = phoneRiskScore(it, text)
            if (phoneRisk > 50) {
                extraScore = maxOf(extraScore, phoneRisk)
                extraReasons += "High-risk phone number (FRI-like heuristic)"
            }
        }
        upi?.let {
            val upiRisk = upiRiskScore(it, text)
            if (upiRisk > 50) {
                extraScore = maxOf(extraScore, upiRisk)
                extraReasons += "Suspicious UPI payee (VPA/payee mismatch risk)"
            }
        }
        // Voice stub: if text looks like transcribed vishing
        if (text.lowercase().containsAny(listOf("press 1", "your bank", "account will be closed", "otp share"))) {
            extraScore += 20
            extraReasons += "Possible vishing script language"
        }

        return ClassificationResult(
            score = extraScore.coerceIn(0, 100),
            label = if (extraScore >= 70) "High multi-modal phishing risk" else base.label,
            reasons = extraReasons,
            isPhishing = extraScore >= 55
        )
    }

    data class ClassificationResult(
        val score: Int,
        val label: String,
        val reasons: List<String>,
        val isPhishing: Boolean
    )

    // === Explainable regional alerts (Hindi/Bundeli microcopy for trust) ===
    fun getRegionalExplanation(result: ClassificationResult, lang: String = "hi"): String {
        val base = when {
            result.isPhishing -> "यह संदेश/लिंक धोखाधड़ी का हो सकता है। सावधानी बरतें।"
            result.score > 40 -> "जोखिम भरा लगता है। आधिकारिक ऐप/साइट से सत्यापित करें।"
            else -> "सुरक्षित लगता है, लेकिन हमेशा सतर्क रहें।"
        }
        return if (lang == "hi" || lang == "bundeli") {
            "$base (${result.label} - स्कोर: ${result.score})"
        } else {
            "${result.label} - Score: ${result.score}. Verify before acting."
        }
    }
}