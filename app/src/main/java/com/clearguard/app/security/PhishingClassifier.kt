package com.clearguard.app.security

import android.content.Context
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Small on-device TensorFlow Lite model for phishing classification.
 * Runs entirely locally.
 *
 * Expected model (place in assets/phishing_model.tflite):
 * - Input: float32 feature vector of size FEATURE_SIZE (e.g. 32-64 features from text/URL heuristics)
 * - Output: float32 [phishing_probability]
 *
 * The model is OPTIONAL. If missing, only the fast regex + heuristics engine is used.
 * You can train a tiny quantized model (e.g. 50-200KB) on phishing URL + SMS datasets.
 */
object PhishingClassifier {

    private const val MODEL_FILE = "phishing_model.tflite"
    private const val FEATURE_SIZE = 48  // adjustable based on your trained model
    private const val TAG = "PhishingClassifier"

    private var interpreter: Interpreter? = null
    private val initialized = AtomicBoolean(false)
    private val modelAvailable = AtomicBoolean(false)

    /**
     * Initialize (loads model if present in assets). Call once from Application or MainActivity.
     */
    fun initialize(context: Context) {
        if (initialized.get()) return
        try {
            val model = FileUtil.loadMappedFile(context, MODEL_FILE)
            val options = Interpreter.Options().apply {
                setNumThreads(2)           // small model, 2 threads is plenty
                setUseNNAPI(true)          // use NNAPI on supported devices (faster)
            }
            interpreter = Interpreter(model, options)
            modelAvailable.set(true)
            Log.i(TAG, "TFLite phishing model loaded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "No phishing_model.tflite found in assets or failed to load. Falling back to rule engine only. ${e.message}")
            modelAvailable.set(false)
        }
        initialized.set(true)
    }

    /**
     * Extract a small deterministic feature vector from text + optional URL.
     * Keep this fast and on-device. These features feed the TFLite model.
     */
    private fun extractFeatures(text: String, url: String?): FloatArray {
        val features = FloatArray(FEATURE_SIZE)
        val combined = (text + " " + (url ?: "")).lowercase()

        var i = 0

        // 1-8: Keyword presence (binary)
        val keywords = listOf("verify", "update", "secure", "login", "kyc", "otp", "claim", "urgent")
        keywords.forEach { kw ->
            features[i++] = if (combined.contains(kw)) 1f else 0f
        }

        // 9-12: Character / symbol stats (normalized)
        val len = combined.length.coerceAtLeast(1).toFloat()
        features[i++] = combined.count { it == '!' }.toFloat() / len * 10
        features[i++] = combined.count { it == '₹' || it == '$' }.toFloat() / len * 10
        features[i++] = combined.count { it.isDigit() }.toFloat() / len
        features[i++] = combined.count { it == '@' || it == '#' }.toFloat() / len * 5

        // 13-20: Regex-based scores from OnDeviceRuleEngine (scaled)
        val ruleScore = OnDeviceRuleEngine.score(text, url).toFloat() / 100f
        features[i++] = ruleScore
        features[i++] = if (url != null && url.contains(Regex("(?i)\\.(tk|ml|ga|cf|top)"))) 1f else 0f
        features[i++] = if (url != null && url.length > 60) 1f else 0f   // long suspicious URLs
        features[i++] = combined.split("\\s+".toRegex()).size.toFloat() / 30f   // word count normalized

        // 21-30: Brand impersonation signals (simplified one-hot style)
        val brands = listOf("sbi", "hdfc", "icici", "paytm", "phonepe", "amazon", "irctc", "epfo")
        brands.forEach { brand ->
            features[i++] = if (combined.contains(brand) && url?.contains(brand) == false) 1f else 0f
        }

        // 31-40: Entropy & randomness heuristics (fast approximation)
        val entropy = approximateEntropy(combined.take(200))
        features[i++] = entropy / 5f
        features[i++] = if (combined.count { it == '-' } > 3) 1f else 0f

        // Fill remaining with 0 (room for future features without breaking model shape)
        while (i < FEATURE_SIZE) {
            features[i++] = 0f
        }

        return features
    }

    private fun approximateEntropy(s: String): Float {
        if (s.isEmpty()) return 0f
        val freq = IntArray(256)
        for (c in s) freq[c.code and 0xFF]++
        var ent = 0.0
        val n = s.length.toDouble()
        for (f in freq) {
            if (f > 0) {
                val p = f / n
                ent -= p * kotlin.math.ln(p)
            }
        }
        return (ent / kotlin.math.ln(2.0)).toFloat()
    }

    /**
     * Main classification entry point.
     * Returns phishing probability (0.0 = safe, 1.0 = phishing).
     * Always runs fast rule engine first. Uses TFLite only if model is present and enabled.
     * Supports multi-modal: pass phone, upi for richer classification.
     */
    fun classify(context: Context, text: String, url: String? = null, useTFLite: Boolean = true,
                 phone: String? = null, upi: OnDeviceRuleEngine.UpiLink? = null): PhishingResult {
        if (!initialized.get()) initialize(context)

        // Always run the fast deterministic rule engine (now multi-modal aware)
        val ruleResult = OnDeviceRuleEngine.multiModalClassify(text, url, phone, upi)
        val ruleScore = ruleResult.score / 100f

        var mlScore = 0f
        var usedML = false

        if (useTFLite && modelAvailable.get() && interpreter != null) {
            try {
                val features = extractFeatures(text, url)
                val input = ByteBuffer.allocateDirect(FEATURE_SIZE * 4).order(ByteOrder.nativeOrder())
                features.forEach { input.putFloat(it) }
                input.rewind()

                val output = Array(1) { FloatArray(1) }
                interpreter?.run(input, output)

                mlScore = output[0][0].coerceIn(0f, 1f)
                usedML = true
            } catch (e: Exception) {
                Log.w(TAG, "TFLite inference failed, falling back to rule engine: ${e.message}")
            }
        }

        // Ensemble: blend rule engine (trustworthy & fast) with ML (when available)
        val finalScore = if (usedML) {
            (ruleScore * 0.65f + mlScore * 0.35f).coerceIn(0f, 1f)
        } else {
            ruleScore
        }

        val label = when {
            finalScore >= 0.78f -> "High phishing probability"
            finalScore >= 0.55f -> "Suspicious / likely phishing"
            finalScore >= 0.35f -> "Elevated risk - review carefully"
            else -> "Low risk"
        }

        return PhishingResult(
            phishingProbability = finalScore,
            ruleEngineScore = ruleScore,
            mlScore = if (usedML) mlScore else null,
            label = label,
            reasons = ruleResult.reasons,
            usedML = usedML
        )
    }

    fun isModelAvailable(): Boolean = modelAvailable.get()

    fun close() {
        interpreter?.close()
        interpreter = null
        initialized.set(false)
        modelAvailable.set(false)
    }

    data class PhishingResult(
        val phishingProbability: Float,   // 0.0 - 1.0
        val ruleEngineScore: Float,
        val mlScore: Float?,              // null if TFLite not used
        val label: String,
        val reasons: List<String>,
        val usedML: Boolean
    )
}