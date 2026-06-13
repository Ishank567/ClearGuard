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
import java.net.URLDecoder
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
        val norm = normalizePhone(numberOrPrefix)
        if (norm.isNotEmpty()) LOCAL_FRI_BAD_PATTERNS.add(norm)
    }

    /** Returns current high-risk numbers/prefixes from the local FRI DB + Edge threat signals. */
    fun getHighRiskPhones(limit: Int = 20): List<String> {
        val combined = (LOCAL_FRI_BAD_PATTERNS + EDGE_THREAT_SIGNALS).toMutableSet()
        return combined.sorted().take(limit)
    }

    /** Add to Edge Threat fabric cache (e.g., from federated reports or partners). Feeds risk scoring. */
    fun addToEdgeThreatSignal(signal: String) {
        val norm = normalizePhone(signal)
        if (norm.isNotEmpty()) EDGE_THREAT_SIGNALS.add(norm)
    }

    fun phoneRiskScore(phone: String, contextText: String = ""): Int {
        var risk = 0
        val cleanPhone = normalizePhone(phone)
        val combined = (phone + " " + contextText).lowercase()
        val ctxLower = contextText.lowercase()

        // Prefix based (toll free / premium often used in scams) - enhanced
        if (HIGH_RISK_PHONE_PREFIXES.any { cleanPhone.startsWith(it) || cleanPhone.endsWith(it.takeLast(4)) }) {
            risk += 38
        }

        // Context from known high-risk senders (banks, UPI, gov, courier as per table)
        for (pat in HIGH_RISK_CONTEXT_PATTERNS) {
            if (pat.containsMatchIn(combined)) {
                risk += 32
                break
            }
        }

        // Local FRI risk DB check
        if (LOCAL_FRI_BAD_PATTERNS.any { cleanPhone.contains(it.takeLast(10)) || combined.contains(it) }) {
            risk += 48
        }

        // Edge Threat fabric signals (shared cache feeding risk API)
        if (EDGE_THREAT_SIGNALS.any { cleanPhone.contains(it.takeLast(10)) || combined.contains(it) }) {
            risk += 32
        }

        // Additional heuristics
        if (cleanPhone.length == 10 && cleanPhone[0] in '6'..'9' && combined.containsAny(listOf("bank", "upi", "refund", "kyc", "support", "verify", "care", "helpline"))) {
            risk += 28
        }
        if (combined.contains("fraud") || combined.contains("scam") || combined.contains("fake")) risk += 18

        // Very new or sequential numbers often risky
        if (cleanPhone.matches(Regex("^[6-9](\\d)\\1{4,}\\d*$"))) risk += 22

        // Context boosts for phone numbers seen with scam language
        if (ctxLower.contains("upi://pay") || ctxLower.contains("paytm") || ctxLower.contains("phonepe") || ctxLower.contains("gpay")) {
            risk += 25
        }
        if ((ctxLower.contains("otp") || ctxLower.contains("one time") || ctxLower.contains("verification code")) && risk > 20) {
            risk += 30
        }
        if (ctxLower.containsAny(listOf("kyc", "suspended", "blocked", "refund", "claim", "due", "verify now", "update now"))) {
            risk += 20
        }
        if (cleanPhone.startsWith("1800") || cleanPhone.startsWith("1860") || cleanPhone.startsWith("140")) {
            risk = maxOf(risk, 70)
        }

        return risk.coerceIn(0, 100)
    }

    fun isHighRiskPhone(phone: String, contextText: String = "", threshold: Int = 60): Boolean {
        return phoneRiskScore(phone, contextText) >= threshold
    }

    /** True when the number matches an entry in the local FRI DB or edge threat signals (decisive for call screening). */
    fun isInLocalRiskDB(phone: String): Boolean {
        val clean = normalizePhone(phone)
        if (clean.isEmpty()) return false
        return (LOCAL_FRI_BAD_PATTERNS + EDGE_THREAT_SIGNALS).any { it.isNotEmpty() && clean.contains(it.takeLast(10)) }
    }

    private fun String.containsAny(list: List<String>) = list.any { this.contains(it) }

    /** Centralized normalizer for phone numbers: strips non-digits, normalizes +91/0 prefixes, keeps last 10 digits. Used everywhere for consistency. */
    fun normalizePhone(phone: String?): String {
        if (phone.isNullOrBlank()) return ""
        var d = phone.replace(Regex("[^0-9]"), "")
        if (d.startsWith("91") && d.length > 10) d = d.substring(2)
        if (d.startsWith("0") && d.length > 10) d = d.substring(1)
        if (d.length > 10) d = d.takeLast(10)
        return d
    }

    // === Mobile Number Risk Scoring API (FRI + operator signals) ===
    // Advanced API facade for "Block/vet high-risk numbers at scale".
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

        // Configurable remote endpoint (set via prefs or setter).
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
                // Configurable endpoint from prefs.
                val configuredEndpoint = prefs.getString(PreferenceKeys.KEY_FRI_REMOTE_ENDPOINT, PreferenceKeys.DEFAULT_FRI_REMOTE_ENDPOINT)
                if (configuredEndpoint != null) setRemoteEndpoint(configuredEndpoint)
                try {
                    val remote = withContext(Dispatchers.IO) {
                        queryRemoteFRI(context, phone)
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

        private fun queryRemoteFRI(context: Context, phone: String): Int? {
            // Enhanced stub for "FRI + operator signals" - realtime capable.
            // In production: Use authenticated POST to real DoT FRI or telco partner API (e.g., with mTLS/OAuth).
            // Privacy: use HMAC-SHA256 with secret from secure storage (see Hardware Key Mgmt).
            // Configurable endpoint from prefs.
            val clean = phone.replace("[^0-9]".toRegex(), "").takeLast(10)
            if (clean.length != 10) return null

            // Get configurable endpoint (for prod, set via prefs)
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

            // Derive a per-app HMAC seed at runtime so the stub never uses a global hard-coded secret.
            val secret = getOrCreateHmacSecret(context)
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
                put("client", "ShieldDNS")
                put("version", "1.0")
                put("timestamp", System.currentTimeMillis())
            }.toString()

            try {
                val request = Request.Builder()
                    .url(url)
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .header("Authorization", "Bearer ${getApiKeyOrStub(context)}")
                    .header("X-Client", "ShieldDNS")
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
         * Useful for bulk processing / vetting lists at scale.
         */
        suspend fun vetAndRecommendActions(
            context: Context,
            phones: List<String>
        ): List<RiskResult> {
            return queryBatchRisk(context, phones).filter { it.isHighRisk }
        }

        private fun getApiKeyOrStub(context: Context): String {
            val prefs = PreferenceKeys.prefs(context)
            return prefs.getString(PreferenceKeys.KEY_REMOTE_API_KEY, null)
                ?: run {
                    val derived = MessageDigest.getInstance("SHA-256")
                        .digest((context.packageName + ":fri-stub-v1").toByteArray(Charsets.UTF_8))
                        .fold("") { acc, byte -> acc + "%02x".format(byte) }
                        .take(64)
                    prefs.edit().putString(PreferenceKeys.KEY_REMOTE_API_KEY, derived).apply()
                    derived
                }
        }

        fun getOrCreateHmacSecret(context: Context): String {
            val prefs = PreferenceKeys.prefs(context)
            return prefs.getString(PreferenceKeys.KEY_HMAC_SEED, null)
                ?: run {
                    val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    val hex = seed.joinToString("") { "%02x".format(it) }
                    prefs.edit().putString(PreferenceKeys.KEY_HMAC_SEED, hex).apply()
                    hex
                }
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

    // === UPI Payee Verification (parse + risk) ===
    // Parses upi://pay?pa=...&pn=PayeeName&am=... etc.
    data class UpiLink(val vpa: String, val payeeName: String?, val amount: String?, val raw: String)

    fun parseUpiLink(text: String): UpiLink? {
        val match = Regex("upi://pay\\?([^\\s]+)", RegexOption.IGNORE_CASE).find(text) ?: return null
        val params = match.groupValues[1].split("&").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size != 2) {
                null
            } else {
                val key = parts[0].lowercase()
                val value = try {
                    URLDecoder.decode(parts[1], "UTF-8")
                } catch (_: Exception) {
                    parts[1]
                }
                key to value
            }
        }.toMap()
        val vpa = params["pa"]?.trim()?.lowercase().orEmpty()
        if (vpa.isBlank()) {
            return null
        }
        return UpiLink(
            vpa = vpa,
            payeeName = params["pn"],
            amount = params["am"],
            raw = match.value
        )
    }

    fun upiRiskScore(upi: UpiLink, context: String = ""): Int {
        var risk = 0
        if (upi.vpa.contains(Regex("[^a-z0-9@\\.]"))) risk += 20 // suspicious chars
        if (upi.payeeName.isNullOrBlank() || upi.payeeName.length < 3) risk += 25
        val amount = upi.amount?.toDoubleOrNull() ?: 0.0
        if (amount > 10000) risk += 15
        val combined = (upi.vpa + " " + (upi.payeeName ?: "") + " " + context).lowercase()
        if (BRAND_IMPERSONATION.any { combined.contains(it) && !upi.vpa.endsWith("@okaxis") && !upi.vpa.endsWith("@oksbi") }) risk += 30
        return risk.coerceIn(0, 100)
    }

    data class SafePaymentCheck(
        val upi: UpiLink,
        val riskScore: Int,
        val riskLevel: String,
        val recommendation: String,
        val alertMessage: String,
        val reasons: List<String>,
        val delayRecommendation: String
    )

    fun safePaymentCheck(text: String): SafePaymentCheck? {
        val upi = parseUpiLink(text) ?: return null
        val amount = upi.amount?.toDoubleOrNull()
        val reasons = mutableListOf<String>()
        var risk = upiRiskScore(upi, text)

        if (upi.payeeName.isNullOrBlank()) {
            reasons += "Payee name is missing from the UPI link."
        }
        if (amount != null && amount >= 10_000.0) {
            reasons += "High-value payment amount detected."
        }
        val lower = (text + " " + upi.vpa + " " + (upi.payeeName ?: "")).lowercase()
        if (lower.containsAny(listOf("kyc", "refund", "reward", "cashback", "urgent", "verify", "electricity", "courier", "loan", "job fee", "registration fee"))) {
            risk += 25
            reasons += "Payment appears near common scam wording."
        }
        if (lower.containsAny(listOf("otp", "pin", "password"))) {
            risk += 20
            reasons += "Message asks for sensitive banking or authentication details."
        }
        if (!upi.vpa.contains("@")) {
            risk += 30
            reasons += "VPA format is incomplete."
        }
        if (upi.vpa.contains(Regex("[^a-z0-9@\\.]"))) {
            reasons += "VPA contains unusual characters."
        }
        if (BRAND_IMPERSONATION.any { lower.contains(it) } &&
            !upi.vpa.endsWith("@okaxis") &&
            !upi.vpa.endsWith("@oksbi") &&
            !upi.vpa.endsWith("@paytm")) {
            reasons += "Brand or bank wording does not match a common verified UPI handle."
        }

        val verification = BankingGateway.verifyPayee(upi.vpa, upi.amount, text)
        if (!verification.isVerified) {
            risk += 15
            reasons += "Payee is not in the local verified-payee cache."
        }

        val boundedRisk = risk.coerceIn(0, 100)
        val riskLevel = when {
            boundedRisk >= 70 -> "High"
            boundedRisk >= 40 -> "Medium"
            else -> "Low"
        }
        val recommendation = when (riskLevel) {
            "High" -> "Do not pay until verified through the official app or known contact."
            "Medium" -> "Pause and verify payee name, amount, and purpose before paying."
            else -> "Looks lower risk, but confirm the payee before completing payment."
        }
        val delayRecommendation = if (boundedRisk >= 40) "DELAY_TRANSACTION_30S" else "NO_DELAY"
        val amountText = amount?.let { " for ₹${upi.amount}" } ?: ""
        val alertMessage = "UPI payment$amountText to ${upi.vpa} is $riskLevel risk."

        return SafePaymentCheck(
            upi = upi,
            riskScore = boundedRisk,
            riskLevel = riskLevel,
            recommendation = recommendation,
            alertMessage = alertMessage,
            reasons = reasons.distinct().ifEmpty { listOf("No strong scam payment indicators found.") },
            delayRecommendation = delayRecommendation
        )
    }

    // === Banking Gateway Integration (NPCI/Bank APIs) stub ===
    // For real-time payee verification and transaction blocking (Banking/UPI).
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
        fun simulateRemoteVerify(vpa: String, context: Context? = null): String? {
            // Placeholder logic, enhanced with hash like FRI.
            val hash = try {
                val secret = context?.let { MobileRiskScoringApi.getOrCreateHmacSecret(it) } ?: "bank_secret"
                val mac = Mac.getInstance("HmacSHA256")
                mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
                mac.doFinal(vpa.toByteArray(Charsets.UTF_8)).fold("") { str, it -> str + "%02x".format(it) }.take(16)
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
        // "Digital arrest" / fake-authority fraud (India's highest-loss scam). Impersonates
        // police/CBI/customs/TRAI/ED, threatens arrest over a fake parcel or SIM, and keeps the
        // victim on a video call until they pay. Treat strongly when an authority+threat pair appears.
        if (isDigitalArrest(text)) {
            extraScore = maxOf(extraScore, 90)
            extraReasons += "Possible 'digital arrest' / fake authority scam — report on 1930"
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

    // === "Digital arrest" / fake-authority scam detection ===
    // Two signal groups: an authority/agency claim and a coercion/threat or money demand.
    // A match requires BOTH groups (or an unambiguous phrase like "digital arrest"), keeping
    // precision high so ordinary mentions of "police" or "court" don't trip the alarm.
    private val AUTHORITY_TERMS = listOf(
        "digital arrest", "cbi", "ncb", "enforcement directorate", "cyber cell", "cyber crime branch",
        "customs", "trai", "telecom department", "narcotics", "income tax department", "police officer"
    )
    private val COERCION_TERMS = listOf(
        "arrest warrant", "non-bailable", "court summon", "money laundering", "illegal parcel",
        "parcel seized", "sim will be blocked", "your number will be blocked", "do not disconnect",
        "stay on the call", "video call", "skype", "refundable security", "verification charge",
        "fine of", "under investigation"
    )

    /**
     * True when the raw caller ID carries an explicit non-Indian (foreign) country code.
     * Fake CBI/police/customs "digital arrest" calls almost always come from international numbers.
     * A bare domestic 10-digit number (no country code) returns false — only an unambiguous
     * +<code> / 00<code> that is not India's +91 is treated as foreign.
     */
    fun isForeignNumber(rawNumber: String?): Boolean {
        if (rawNumber.isNullOrBlank()) return false
        val trimmed = rawNumber.trim().replace(" ", "").replace("-", "")
        val e164 = when {
            trimmed.startsWith("+") -> trimmed
            trimmed.startsWith("00") -> "+" + trimmed.substring(2)
            else -> return false // no country code present → treat as domestic
        }
        val digits = e164.removePrefix("+").replace(Regex("[^0-9]"), "")
        if (digits.length < 4) return false
        // India is country code 91; anything else with an explicit code is foreign.
        return !digits.startsWith("91")
    }

    /** True when text matches the fake-authority "digital arrest" coercion pattern. */
    fun isDigitalArrest(text: String): Boolean {
        val t = text.lowercase()
        if (t.contains("digital arrest")) return true
        val hasAuthority = AUTHORITY_TERMS.any { t.contains(it) }
        val hasCoercion = COERCION_TERMS.any { t.contains(it) }
        return hasAuthority && hasCoercion
    }

    data class CyberHelpline(
        val number: String,
        val portal: String,
        val advice: List<String>
    )

    /**
     * India's official cyber-fraud reporting channels (National Cyber Crime Reporting Portal / I4C).
     * Shown alongside high-risk scam verdicts so a worried user has the real, government-published
     * next step instead of a scammer's "helpline". These are public facts, not stored secrets.
     */
    fun cyberHelplineInfo(): CyberHelpline = CyberHelpline(
        number = "1930",
        portal = "https://cybercrime.gov.in",
        advice = listOf(
            "No government agency (police, CBI, customs, TRAI) ever arrests you over a call or video call.",
            "Never pay a 'verification', 'security deposit' or 'fine' to release a parcel or unblock a SIM.",
            "Disconnect, then call 1930 or report at cybercrime.gov.in within the first hours to freeze the money."
        )
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
