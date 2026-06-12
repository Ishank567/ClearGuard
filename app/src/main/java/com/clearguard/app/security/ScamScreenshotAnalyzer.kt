package com.clearguard.app.security

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Scam Screenshot Scanner for Indian Scam Shield.
 * Uses on-device ML Kit OCR + heuristics to detect:
 * - Fake reward / "You won"
 * - Fake KYC
 * - Fake payment / bill
 * - Fake investment / group
 * - Fake job / registration fee
 * - Fake customer support / helpline
 * - Fake APK link / reward claim
 *
 * All processing is 100% on-device. No data leaves the phone.
 */
object ScamScreenshotAnalyzer {

    data class Detection(
        val category: String,
        val reason: String,
        val snippet: String,
        val confidence: Int  // 0-100 rough score
    )

    // Keyword groups (modeled directly after ScamDetector + new ones for screenshots/text)
    private val FAKE_REWARD = listOf("won", "win", "₹", "rupee", "lakh", "crore", "prize", "lottery", "jackpot", "reward", "claim now", "get reward")
    private val FAKE_KYC = listOf("kyc", "update kyc", "ekyc", "verify kyc", "complete kyc", "kyc now", "kyc update")
    private val FAKE_PAYMENT = listOf("pay now", "payment due", "bill", "electricity", "ebill", "current bill", "power bill", "due amount", "pay ₹", "transfer immediately", "upi payment", "bill payment")
    private val FAKE_INVESTMENT = listOf("investment", "double money", "guaranteed return", "trading", "crypto", "bitcoin", "forex", "mutual fund", "profit", "group link", "whatsapp group", "telegram group")
    private val FAKE_JOB = listOf("job", "part time", "work from home", "earn from home", "registration fee", "job fee", "joining fee", "apply now", "salary", "recruitment", "hiring", "task earn")
    private val FAKE_CUSTOMER_SUPPORT = listOf("customer care", "helpline", "toll free", "1800", "call now", "customer support", "support number", "whatsapp support", "refund helpline", "official support")
    private val FAKE_APK = listOf("install apk", "download apk", "claim reward apk", "get apk", "fake app", "install app", "apk link", "direct apk")

    private val PHONE_REGEX = Regex("\\b(?:\\+?91|0)?[6-9]\\d{9}\\b")

    /**
     * Main entry: pick a Bitmap (from screenshot), run OCR then scan.
     */
    suspend fun analyze(context: Context, bitmap: Bitmap): List<Detection> = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val fullText = visionText.text.lowercase()
                val detections = scanText(context, fullText, visionText).toMutableList()

                // === Multi-modal Phishing Engine (regex + TFLite + UPI/QR/Phone risk) ===
                try {
                    val upi = com.clearguard.app.security.OnDeviceRuleEngine.parseUpiLink(fullText)
                    val phone = PHONE_REGEX.find(fullText)?.value
                    val multiRes = com.clearguard.app.security.OnDeviceRuleEngine.multiModalClassify(fullText, null, phone, upi)
                    if (multiRes.isPhishing || multiRes.score >= 60) {
                        detections.add(
                            Detection(
                                "Multi-modal Phishing",
                                multiRes.label + " | " + com.clearguard.app.security.OnDeviceRuleEngine.getRegionalExplanation(multiRes),
                                fullText.take(100),
                                multiRes.score.coerceAtMost(95)
                            )
                        )
                    }

                    // TFLite (if enabled)
                    val prefs = com.clearguard.app.PreferenceKeys.prefs(context)
                    val tfliteOn = prefs.getBoolean(
                        com.clearguard.app.PreferenceKeys.KEY_PHISHING_TFLITE_ENABLED,
                        com.clearguard.app.PreferenceKeys.DEFAULT_PHISHING_TFLITE_ENABLED
                    )
                    if (tfliteOn) {
                        val mlRes = com.clearguard.app.security.PhishingClassifier.classify(context, fullText, null, true, phone, upi)
                        if (mlRes.phishingProbability > 0.58f) {
                            detections.add(
                                Detection(
                                    "Phishing (TFLite multi-modal)",
                                    mlRes.label,
                                    fullText.take(80),
                                    (mlRes.phishingProbability * 100).toInt().coerceAtMost(97)
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Never break scanner
                }

                continuation.resume(detections)
            }
            .addOnFailureListener { e ->
                continuation.resumeWithException(e)
            }
    }

    private fun scanText(context: Context, fullText: String, visionText: Text): List<Detection> {
        val results = mutableListOf<Detection>()

        // Helper to find best snippet
        fun findSnippet(keywords: List<String>): String? {
            for (kw in keywords) {
                val idx = fullText.indexOf(kw)
                if (idx >= 0) {
                    val start = (idx - 25).coerceAtLeast(0)
                    val end = (idx + kw.length + 35).coerceAtMost(fullText.length)
                    return fullText.substring(start, end).trim()
                }
            }
            return null
        }

        fun addIfMatch(category: String, keywords: List<String>, baseConfidence: Int) {
            val snippet = findSnippet(keywords)
            if (snippet != null) {
                // Boost if multiple keywords or urgency words present
                var conf = baseConfidence
                if (snippet.contains("now") || snippet.contains("urgent") || snippet.contains("immediately") || snippet.contains("today")) conf += 12
                if (snippet.contains("₹") || snippet.contains("rs")) conf += 8
                if (snippet.contains("link") || snippet.contains("click")) conf += 6
                results.add(Detection(category, "Matches typical $category phrasing", snippet, conf.coerceIn(50, 98)))
            }
        }

        // Run the 7 categories requested
        addIfMatch("Fake reward", FAKE_REWARD, 78)
        addIfMatch("Fake KYC", FAKE_KYC, 82)
        addIfMatch("Fake payment", FAKE_PAYMENT, 75)
        addIfMatch("Fake investment", FAKE_INVESTMENT, 80)
        addIfMatch("Fake job", FAKE_JOB, 76)
        addIfMatch("Fake customer support", FAKE_CUSTOMER_SUPPORT, 73)
        addIfMatch("Fake APK link", FAKE_APK, 85)

        // Bonus: detect phone numbers + customer support language (very common in these scams)
        if (PHONE_REGEX.containsMatchIn(fullText) && fullText.containsAny(FAKE_CUSTOMER_SUPPORT)) {
            val match = PHONE_REGEX.find(fullText)?.value ?: ""
            results.add(Detection("Fake customer support", "Phone number + support language detected", "Phone: $match", 88))
        }

        // Multi-modal: UPI/QR detection + Banking Gateway verification (NPCI stub)
        val upiLink = com.clearguard.app.security.OnDeviceRuleEngine.parseUpiLink(fullText)
        if (upiLink != null) {
            val verification = com.clearguard.app.security.OnDeviceRuleEngine.BankingGateway.verifyPayee(
                upiLink.vpa, upiLink.amount, fullText
            )
            results.add(Detection(
                "UPI Payee Verification (Banking Gateway)",
                verification.recommendedAction,
                "VPA: ${verification.vpa} | ${verification.explanation}",
                verification.riskScore
            ))
        }

        // Phone risk scoring (FRI-like) - gated by setting
        val prefs = com.clearguard.app.PreferenceKeys.prefs(context)
        if (prefs.getBoolean(com.clearguard.app.PreferenceKeys.KEY_MOBILE_RISK_SCORING_ENABLED, com.clearguard.app.PreferenceKeys.DEFAULT_MOBILE_RISK_SCORING_ENABLED)) {
            val phones = PHONE_REGEX.findAll(fullText).map { it.value }.toList()
            phones.forEach { ph ->
                val phoneRisk = com.clearguard.app.security.OnDeviceRuleEngine.phoneRiskScore(ph, fullText)
                if (phoneRisk > 40) {
                    results.add(Detection("High-risk phone (FRI heuristic)", "Potential scam sender", "Phone: $ph Risk: $phoneRisk", phoneRisk))
                }
            }
        }

        // Detect obvious APK / link lures in context of reward
        if (fullText.contains("apk") && (fullText.containsAny(FAKE_REWARD) || fullText.contains("claim"))) {
            if (results.none { it.category == "Fake APK link" }) {
                results.add(Detection("Fake APK link", "APK download mentioned together with reward/claim language", findSnippet(listOf("apk")) ?: fullText.take(80), 90))
            }
        }

        // Deduplicate by category (keep highest confidence)
        return results.groupBy { it.category }
            .map { (_, group) -> group.maxByOrNull { it.confidence }!! }
            .sortedByDescending { it.confidence }
    }

    private fun String.containsAny(list: List<String>): Boolean {
        val lower = this.lowercase()
        return list.any { lower.contains(it.lowercase()) }
    }

    /**
     * Extract potential domains/URLs from OCR text for optional blocking.
     */
    fun extractSuspiciousDomains(text: String): List<String> {
        val domainRegex = Regex("(?:https?://)?(?:www\\.)?([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})")
        return domainRegex.findAll(text.lowercase())
            .mapNotNull { it.groupValues.getOrNull(1) }
            .filter { it.contains(".") && !it.endsWith(".") && it.length > 4 }
            .distinct()
            .take(8)
            .toList()
    }
}