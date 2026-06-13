package com.clearguard.app.ui.screens

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.theme.ClearColors
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen() {
    val context = LocalContext.current
    val prefs = remember { PreferenceKeys.prefs(context) }

    var urlInput by remember { mutableStateOf("https://google.com") }
    var currentUrl by remember { mutableStateOf("https://google.com") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    // Shields config
    var antiFingerprint by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_ANTI_FINGERPRINT, PreferenceKeys.DEFAULT_BROWSER_ANTI_FINGERPRINT))
    }
    var cookieRemover by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_COOKIE_REMOVER, PreferenceKeys.DEFAULT_BROWSER_COOKIE_REMOVER))
    }
    var elementCleanerMode by remember { mutableStateOf(false) }

    // New toggles for Dark Pattern + Fake Customer Care (tied to Indian Scam Shield / Elder mode vibe)
    var darkPatternBlocker by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_DARK_PATTERN_BLOCKER, PreferenceKeys.DEFAULT_BROWSER_DARK_PATTERN_BLOCKER))
    }
    var phoneCareWarner by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_FAKE_PHONE_WARNER, PreferenceKeys.DEFAULT_BROWSER_FAKE_PHONE_WARNER))
    }

    // Anti-adblock bypass tools (advanced, opt-in)
    var scriptletInjection by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_SCRIPTLET_INJECTION, PreferenceKeys.DEFAULT_BROWSER_SCRIPTLET_INJECTION))
    }
    var antiAdblockDefuser by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_ANTI_ADBLOCK_DEFUSER, PreferenceKeys.DEFAULT_BROWSER_ANTI_ADBLOCK_DEFUSER))
    }
    var popupTrapBlocker by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_POPUP_TRAP_BLOCKER, PreferenceKeys.DEFAULT_BROWSER_POPUP_TRAP_BLOCKER))
    }
    var redirectChainCleaner by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_REDIRECT_CHAIN_CLEANER, PreferenceKeys.DEFAULT_BROWSER_REDIRECT_CHAIN_CLEANER))
    }
    var antiPaywallWarning by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_ANTI_PAYWALL_WARNING, PreferenceKeys.DEFAULT_BROWSER_ANTI_PAYWALL_WARNING))
    }
    var sponsoredWidgetRemover by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_SPONSORED_WIDGET_REMOVER, PreferenceKeys.DEFAULT_BROWSER_SPONSORED_WIDGET_REMOVER))
    }
    var fakeCountdownRemover by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_FAKE_COUNTDOWN_REMOVER, PreferenceKeys.DEFAULT_BROWSER_FAKE_COUNTDOWN_REMOVER))
    }
    var overlayRemover by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BROWSER_OVERLAY_REMOVER, PreferenceKeys.DEFAULT_BROWSER_OVERLAY_REMOVER))
    }

    // On-device rule engine + TFLite phishing classification (text + current URL)
    var phishingRisk by remember { mutableStateOf<com.clearguard.app.security.PhishingClassifier.PhishingResult?>(null) }

    // Indian Regional Clean mode rules (fake download, job fraud warning inside WebView)
    var fakeDownloadBlockedAlert by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search/URL bar & Shield switches row
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            cornerRadius = 14.dp
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = ClearColors.text),
                        placeholder = { Text("Search or type URL", color = ClearColors.muted, fontSize = 13.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Transparent),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = ClearColors.text,
                            unfocusedTextColor = ClearColors.text
                        ),
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Language, contentDescription = null, tint = ClearColors.muted)
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = {
                            var target = urlInput.trim()
                            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                                target = "https://$target"
                            }
                            webView?.loadUrl(target)
                        }
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Go", tint = ClearColors.green)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Shields status row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShieldTogglePill(
                        label = "Anti-Fingerprint",
                        active = antiFingerprint,
                        onClick = {
                            antiFingerprint = !antiFingerprint
                            prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_ANTI_FINGERPRINT, antiFingerprint).apply()
                            webView?.reload()
                        }
                    )
                    ShieldTogglePill(
                        label = "Cookie Shield",
                        active = cookieRemover,
                        onClick = {
                            cookieRemover = !cookieRemover
                            prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_COOKIE_REMOVER, cookieRemover).apply()
                            webView?.reload()
                        }
                    )
                    ShieldTogglePill(
                        label = "Cleaner Mode",
                        active = elementCleanerMode,
                        onClick = {
                            elementCleanerMode = !elementCleanerMode
                            if (elementCleanerMode) {
                                // Enable overlay cleaner JS script
                                injectWebsiteCleanerActivator(webView)
                            } else {
                                webView?.reload()
                            }
                        }
                    )
                    ShieldTogglePill(
                        label = "Dark Patterns",
                        active = darkPatternBlocker,
                        onClick = {
                            darkPatternBlocker = !darkPatternBlocker
                            prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_DARK_PATTERN_BLOCKER, darkPatternBlocker).apply()
                            webView?.reload()
                        }
                    )
                    ShieldTogglePill(
                        label = "Phone Shield",
                        active = phoneCareWarner,
                        onClick = {
                            phoneCareWarner = !phoneCareWarner
                            prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_FAKE_PHONE_WARNER, phoneCareWarner).apply()
                            webView?.reload()
                        }
                    )
                }
            }
            // Second row for advanced anti-adblock bypass tools (opt-in for advanced users)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShieldTogglePill(
                    label = "Scriptlets",
                    active = scriptletInjection,
                    onClick = {
                        scriptletInjection = !scriptletInjection
                        prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_SCRIPTLET_INJECTION, scriptletInjection).apply()
                        webView?.reload()
                    }
                )
                ShieldTogglePill(
                    label = "Defuser",
                    active = antiAdblockDefuser,
                    onClick = {
                        antiAdblockDefuser = !antiAdblockDefuser
                        prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_ANTI_ADBLOCK_DEFUSER, antiAdblockDefuser).apply()
                        webView?.reload()
                    }
                )
                ShieldTogglePill(
                    label = "Popup Trap",
                    active = popupTrapBlocker,
                    onClick = {
                        popupTrapBlocker = !popupTrapBlocker
                        prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_POPUP_TRAP_BLOCKER, popupTrapBlocker).apply()
                        webView?.reload()
                    }
                )
                ShieldTogglePill(
                    label = "Redirect",
                    active = redirectChainCleaner,
                    onClick = {
                        redirectChainCleaner = !redirectChainCleaner
                        prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_REDIRECT_CHAIN_CLEANER, redirectChainCleaner).apply()
                        webView?.reload()
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShieldTogglePill(
                    label = "Paywall Warn",
                    active = antiPaywallWarning,
                    onClick = {
                        antiPaywallWarning = !antiPaywallWarning
                        prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_ANTI_PAYWALL_WARNING, antiPaywallWarning).apply()
                        webView?.reload()
                    }
                )
                ShieldTogglePill(
                    label = "Sponsored",
                    active = sponsoredWidgetRemover,
                    onClick = {
                        sponsoredWidgetRemover = !sponsoredWidgetRemover
                        prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_SPONSORED_WIDGET_REMOVER, sponsoredWidgetRemover).apply()
                        webView?.reload()
                    }
                )
                ShieldTogglePill(
                    label = "Countdown",
                    active = fakeCountdownRemover,
                    onClick = {
                        fakeCountdownRemover = !fakeCountdownRemover
                        prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_FAKE_COUNTDOWN_REMOVER, fakeCountdownRemover).apply()
                        webView?.reload()
                    }
                )
                ShieldTogglePill(
                    label = "Overlay",
                    active = overlayRemover,
                    onClick = {
                        overlayRemover = !overlayRemover
                        prefs.edit().putBoolean(PreferenceKeys.KEY_BROWSER_OVERLAY_REMOVER, overlayRemover).apply()
                        webView?.reload()
                    }
                )
            }
        }

        // Phishing risk banner (on-device rule engine + optional TFLite) + UPI Payee Verification
        phishingRisk?.let { risk ->
            if (risk.phishingProbability > 0.55f) {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    cornerRadius = 10.dp
                ) {
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ClearColors.danger, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${risk.label} • ${(risk.phishingProbability * 100).toInt()}%  ${if (risk.usedML) "(TFLite+rules)" else "(rules)"}",
                            fontSize = 11.sp,
                            color = ClearColors.danger,
                            fontWeight = FontWeight.Medium
                        )
                        val regional = com.clearguard.app.security.OnDeviceRuleEngine.getRegionalExplanation(
                            com.clearguard.app.security.OnDeviceRuleEngine.ClassificationResult(
                                (risk.phishingProbability * 100).toInt(),
                                risk.label,
                                risk.reasons,
                                risk.phishingProbability > 0.5f
                            )
                        )
                        Text(regional, fontSize = 9.sp, color = ClearColors.muted)
                    }
                }
            }
        }

        // Safe Payment Checks: risky UPI transaction alert (from current URL or page)
        currentUrl?.let { url ->
            if (prefs.getBoolean(
                    PreferenceKeys.KEY_SAFE_PAYMENT_CHECKS_ENABLED,
                    PreferenceKeys.DEFAULT_SAFE_PAYMENT_CHECKS_ENABLED
                ) && url.startsWith("upi://", ignoreCase = true)
            ) {
                val paymentCheck = com.clearguard.app.security.OnDeviceRuleEngine.safePaymentCheck("$url in-app browser transaction")
                if (paymentCheck != null) {
                    val accent = when (paymentCheck.riskLevel) {
                        "High" -> ClearColors.danger
                        "Medium" -> ClearColors.warning
                        else -> ClearColors.green
                    }
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                        cornerRadius = 10.dp
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Safe Payment Check", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = accent)
                            Text(paymentCheck.alertMessage, fontSize = 11.sp, color = ClearColors.text)
                            Text("Risk: ${paymentCheck.riskScore}/100 | ${paymentCheck.recommendation}", fontSize = 10.sp, color = ClearColors.muted)
                            paymentCheck.reasons.take(2).forEach { reason ->
                                Text("• $reason", fontSize = 10.sp, color = ClearColors.muted)
                            }
                        }
                    }
                }
            }
        }

        // Web view box container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, ClearColors.border.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true

                        // Inject JS Interface for the element selection website cleaner
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onElementClicked(selector: String) {
                                // Save element selector rule
                                saveCleanerRule(context, currentUrl, selector)
                            }

                            @JavascriptInterface
                            fun onFakeDownloadDetected() {
                                fakeDownloadBlockedAlert = true
                            }
                        }, "ShieldBrowserInterface")

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                if (url != null) {
                                    currentUrl = url
                                    urlInput = url
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false

                                // Apply saved cleaner rules for this domain
                                applySavedCleanerRules(view, url)

                                // Inject Cookie Consent Remover script
                                if (cookieRemover) {
                                    injectCookieRemoverScript(view)
                                }

                                // Inject Anti-Fingerprinting script
                                if (antiFingerprint) {
                                    injectAntiFingerprintScript(view)
                                }

                                // Inject Fake download button warning & blocker script
                                injectFakeDownloadScript(view)

                                // Inject Hindi/Hinglish Website Cleaner
                                injectHindiHinglishCleanerScript(view)

                                // Inject Cricket streaming popup blocker
                                injectCricketPopupBlockerScript(view)

                                // Dark Pattern Blocker (new)
                                if (darkPatternBlocker) {
                                    injectDarkPatternBlocker(view)
                                }

                                // Fake Customer Care Number Warner (very useful for India)
                                if (phoneCareWarner) {
                                    injectFakeCustomerCareWarner(view)
                                }

                                // Anti-adblock bypass tools (advanced)
                                if (scriptletInjection) {
                                    injectScriptletInjection(view)
                                }
                                if (antiAdblockDefuser) {
                                    injectAntiAdblockDefuser(view)
                                }
                                if (popupTrapBlocker) {
                                    injectPopupTrapBlocker(view)
                                }
                                if (redirectChainCleaner) {
                                    injectRedirectChainCleaner(view)
                                }
                                if (antiPaywallWarning) {
                                    injectAntiPaywallWarning(view)
                                }
                                if (sponsoredWidgetRemover) {
                                    injectSponsoredWidgetRemover(view)
                                }
                                if (fakeCountdownRemover) {
                                    injectFakeCountdownRemover(view)
                                }
                                if (overlayRemover) {
                                    injectOverlayRemover(view)
                                }

                                // On-device rule engine + TFLite phishing classification (runs fast regex/heuristics + optional model)
                                val prefs = PreferenceKeys.prefs(context)
                                val ruleEngineOn = prefs.getBoolean(
                                    PreferenceKeys.KEY_ON_DEVICE_RULE_ENGINE_ENABLED,
                                    PreferenceKeys.DEFAULT_ON_DEVICE_RULE_ENGINE_ENABLED
                                )
                                val tfliteOn = ruleEngineOn && prefs.getBoolean(
                                    PreferenceKeys.KEY_PHISHING_TFLITE_ENABLED,
                                    PreferenceKeys.DEFAULT_PHISHING_TFLITE_ENABLED
                                )
                                if (ruleEngineOn) {
                                    try {
                                        val pageText = view?.title ?: ""
                                        val res = com.clearguard.app.security.PhishingClassifier.classify(
                                            context, pageText, url, tfliteOn
                                        )
                                        phishingRisk = res
                                        if (res.phishingProbability > 0.65f) {
                                            // Show non-intrusive warning (reuse existing alert pattern)
                                            // In a real polish we could trigger a dedicated phishingRiskAlert state
                                        }
                                    } catch (_: Exception) {}
                                }

                                // If element cleaner mode is active, trigger activator script
                                if (elementCleanerMode) {
                                    injectWebsiteCleanerActivator(view)
                                }
                            }
                        }

                        webView = this
                        loadUrl(currentUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Alert warning overlay for Fake Download attempts or scam links
            androidx.compose.animation.AnimatedVisibility(
                visible = fakeDownloadBlockedAlert,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = ClearColors.danger)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Adware/Fake Button Blocked", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = ClearColors.danger)
                            Text("ShieldDNS auto-blocked a fake download overlay trigger.", fontSize = 11.sp, color = ClearColors.text)
                        }
                        IconButton(onClick = { fakeDownloadBlockedAlert = false }) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = ClearColors.muted)
                        }
                    }
                }
            }
        }

        // Navigation bottom control bar
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            cornerRadius = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = if (canGoBack) ClearColors.green else ClearColors.muted)
                }
                IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward", tint = if (canGoForward) ClearColors.green else ClearColors.muted)
                }
                IconButton(onClick = { webView?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ClearColors.green)
                }
                IconButton(onClick = {
                    webView?.loadUrl("https://google.com")
                }) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = ClearColors.green)
                }

                // === "Clean My Website" Button (prominent per user request #5) ===
                IconButton(onClick = {
                    webView?.let { wv ->
                        injectComprehensivePageCleaner(wv)
                        injectDarkPatternBlocker(wv)
                        injectFakeCustomerCareWarner(wv)
                        // Anti-adblock bypass tools (if user has them enabled, or force some)
                        if (scriptletInjection || antiAdblockDefuser || popupTrapBlocker || redirectChainCleaner || antiPaywallWarning || sponsoredWidgetRemover || fakeCountdownRemover || overlayRemover) {
                            injectScriptletInjection(wv)
                            injectAntiAdblockDefuser(wv)
                            injectPopupTrapBlocker(wv)
                            injectRedirectChainCleaner(wv)
                            injectAntiPaywallWarning(wv)
                            injectSponsoredWidgetRemover(wv)
                            injectFakeCountdownRemover(wv)
                            injectOverlayRemover(wv)
                        }
                    }
                }) {
                    Icon(Icons.Default.CleaningServices, contentDescription = "Clean this page", tint = ClearColors.green)
                }
            }
        }
    }
}

@Composable
private fun ShieldTogglePill(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) ClearColors.green.copy(alpha = 0.15f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (active) ClearColors.green.copy(alpha = 0.35f) else ClearColors.border.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) ClearColors.green else ClearColors.muted
        )
    }
}

// === JS Script Injections ===
private fun injectCookieRemoverScript(view: WebView?) {
    val js = """
        (function() {
            var selectors = [
                '[id*="cookie-consent"]', '[class*="cookie-consent"]',
                '[id*="cookieconsent"]', '[class*="cookieconsent"]',
                '[id*="cookie-banner"]', '[class*="cookie-banner"]',
                '[id*="cookiebanner"]', '[class*="cookiebanner"]',
                '[class*="cookie-law"]', '[id*="cookie-law"]',
                '[class*="consent-banner"]', '[id*="consent-banner"]'
            ];
            selectors.forEach(function(sel) {
                var elements = document.querySelectorAll(sel);
                elements.forEach(function(el) {
                    el.style.display = 'none';
                });
            });
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

private fun injectAntiFingerprintScript(view: WebView?) {
    val js = """
        (function() {
            try {
                // Spoof canvas
                var originalToDataURL = HTMLCanvasElement.prototype.toDataURL;
                HTMLCanvasElement.prototype.toDataURL = function() {
                    return ""; 
                };
                
                // Spoof WebGL
                var originalGetParameter = WebGLRenderingContext.prototype.getParameter;
                WebGLRenderingContext.prototype.getParameter = function(p) {
                    if (p === 37445) return "Intel Open Source Technology Center"; // UNMASKED_RENDERER
                    if (p === 37446) return "Mesa Project"; // UNMASKED_VENDOR
                    return originalGetParameter.apply(this, arguments);
                };
                
                // Spoof device properties
                Object.defineProperty(navigator, 'deviceMemory', { get: function() { return 8; } });
                Object.defineProperty(navigator, 'hardwareConcurrency', { get: function() { return 8; } });
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

private fun injectFakeDownloadScript(view: WebView?) {
    // Detect elements that look like "Download" buttons but load iframe sources or third-party links
    val js = """
        (function() {
            var buttons = document.querySelectorAll('a, button, div[class*="download"], a[class*="btn"]');
            buttons.forEach(function(btn) {
                var text = (btn.innerText || btn.textContent || "").toLowerCase();
                if (text.includes("download") || text.includes("fast download") || text.includes("direct download")) {
                    // Check if it leads to dynamic/unsafe scripts or ad redirection
                    btn.addEventListener('click', function(e) {
                        var href = btn.getAttribute('href');
                        if (href && (href.includes("adclk") || href.includes("onclick") || href.includes("fastclick") || href.includes("linkvertise"))) {
                            e.preventDefault();
                            e.stopPropagation();
                            if (window.ShieldBrowserInterface) {
                                window.ShieldBrowserInterface.onFakeDownloadDetected();
                            }
                        }
                    }, true);
                }
            });
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

private fun injectHindiHinglishCleanerScript(view: WebView?) {
    val js = """
        (function() {
            var regionalSelectors = [
                '.ad-box', '.ad-slot', '.advertisement', '[id*="-ad-"]', '[class*="-ad-"]',
                '.bhaskar-ad', '.dainik-ad', '.ndtv-ad', '.jagran-ad',
                'div[id*="google_ads_iframe"]', 'div[class*="native-ad"]',
                '.cricket-ad', '.ipl-ad-banner'
            ];
            regionalSelectors.forEach(function(sel) {
                var elements = document.querySelectorAll(sel);
                elements.forEach(function(el) {
                    el.style.display = 'none';
                });
            });
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

private fun injectCricketPopupBlockerScript(view: WebView?) {
    val js = """
        (function() {
            try {
                // Prevent window.open popups
                window.open = function() { return null; };
                
                // Disable target="_blank" redirects which bypass simple pop-up checks
                var links = document.querySelectorAll('a[target="_blank"]');
                links.forEach(function(link) {
                    link.removeAttribute('target');
                });
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

private fun injectWebsiteCleanerActivator(view: WebView?) {
    val js = """
        (function() {
            var style = document.getElementById('shield-cleaner-style');
            if (!style) {
                style = document.createElement('style');
                style.id = 'shield-cleaner-style';
                style.innerHTML = ' .shield-highlight { outline: 2px solid #22C55E !important; cursor: crosshair !important; } ';
                document.head.appendChild(style);
            }
            
            var hoveredElement = null;
            
            function onMouseOver(e) {
                if (hoveredElement) hoveredElement.classList.remove('shield-highlight');
                hoveredElement = e.target;
                hoveredElement.classList.add('shield-highlight');
            }
            
            function onMouseOut(e) {
                if (hoveredElement) hoveredElement.classList.remove('shield-highlight');
                hoveredElement = null;
            }
            
            function onClick(e) {
                e.preventDefault();
                e.stopPropagation();
                var el = e.target;
                
                var selector = el.tagName.toLowerCase();
                if (el.id) {
                    selector += '#' + el.id;
                } else if (el.className) {
                    var cls = Array.from(el.classList).filter(function(c) { return c !== 'shield-highlight'; }).join('.');
                    if (cls.length > 0) selector += '.' + cls;
                }
                
                el.style.display = 'none';
                
                if (window.ShieldBrowserInterface) {
                    window.ShieldBrowserInterface.onElementClicked(selector);
                }
                
                disableCleaner();
            }
            
            window.disableCleaner = function() {
                document.removeEventListener('mouseover', onMouseOver, true);
                document.removeEventListener('mouseout', onMouseOut, true);
                document.removeEventListener('click', onClick, true);
                if (hoveredElement) hoveredElement.classList.remove('shield-highlight');
                var styleTag = document.getElementById('shield-cleaner-style');
                if (styleTag) styleTag.remove();
            };
            
            document.addEventListener('mouseover', onMouseOver, true);
            document.addEventListener('mouseout', onMouseOut, true);
            document.addEventListener('click', onClick, true);
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

private fun applySavedCleanerRules(view: WebView?, url: String?) {
    if (url == null) return
    val context = view?.context ?: return
    try {
        val prefs = PreferenceKeys.prefs(context)
        val host = java.net.URI.create(url).host ?: return
        val rulesJson = prefs.getString(PreferenceKeys.KEY_BROWSER_CLEANER_RULES, "{}")
        val obj = JSONObject(rulesJson)
        if (obj.has(host)) {
            val arr = obj.getJSONArray(host)
            var css = ""
            for (i in 0 until arr.length()) {
                css += arr.getString(i) + " { display: none !important; }\n"
            }
            val js = """
                (function() {
                    var style = document.createElement('style');
                    style.innerHTML = `$css`;
                    document.head.appendChild(style);
                })();
            """.trimIndent()
            view.evaluateJavascript(js, null)
        }
    } catch (e: Exception) {}
}

private fun saveCleanerRule(context: Context, url: String, selector: String) {
    try {
        val prefs = PreferenceKeys.prefs(context)
        val host = java.net.URI.create(url).host ?: return
        val rulesJson = prefs.getString(PreferenceKeys.KEY_BROWSER_CLEANER_RULES, "{}")
        val obj = JSONObject(rulesJson)
        val arr = if (obj.has(host)) obj.getJSONArray(host) else JSONArray()
        
        // Prevent duplicate rules
        var exists = false
        for (i in 0 until arr.length()) {
            if (arr.getString(i) == selector) {
                exists = true
                break
            }
        }
        if (!exists) {
            arr.put(selector)
            obj.put(host, arr)
            prefs.edit().putString(PreferenceKeys.KEY_BROWSER_CLEANER_RULES, obj.toString()).apply()
        }
    } catch (e: Exception) {}
}

// === New: Comprehensive "Clean My Website" + Dark Pattern Blocker ===
private fun injectComprehensivePageCleaner(view: WebView?) {
    val js = """
        (function() {
            try {
                var style = document.createElement('style');
                style.id = 'shield-full-clean';
                style.innerHTML = `
                    /* Ads & empty ad spaces (exact match to request) */
                    [class*="ad"], [id*="ad"], .advertisement, .ad-container, .ad-slot, .google-ad,
                    [class*="sponsored"], [class*="promo"], .native-ad, [class*="banner"],
                    .ad-wrapper, [data-ad], [id*="ad-"] { display: none !important; height: 0 !important; }
                    
                    /* Floating video (exact) */
                    video, [class*="video"], iframe[src*="youtube"], .floating-video, 
                    .video-container, [class*="autoplay"] { display: none !important; }
                    
                    /* Sticky header (exact) */
                    header[style*="position: fixed"], header[style*="position:fixed"], 
                    .sticky, .fixed-header, [class*="sticky-header"], [class*="fixed-top"] { display: none !important; }
                    
                    /* Sidebar clutter (exact) */
                    .sidebar, [class*="sidebar"], .right-col, .left-col, aside, 
                    [class*="side-bar"], .widget-sidebar { display: none !important; }
                    
                    /* Newsletter popup (exact) */
                    [class*="newsletter"], [id*="newsletter"], .newsletter-popup, 
                    [class*="subscribe-popup"] { display: none !important; }
                    
                    /* Cookie banner (exact) */
                    [class*="cookie"], [id*="cookie"], .consent-banner, .cookie-law, 
                    #cookie-consent, [aria-label*="cookie"] { display: none !important; }
                    
                    /* Recommended articles (exact) */
                    [class*="recommend"], [class*="related"], .trending, .you-may-like,
                    .footer-recommend, .more-from, .recommended-articles, [class*="read-more"] { display: none !important; }
                    
                    /* Empty ad spaces & hidden close buttons */
                    [style*="height:0"], [style*="min-height:0"], [class*="empty-ad"],
                    button[style*="opacity:0"], .close[style*="display:none"], [aria-hidden="true"] { display: none !important; }
                `;
                document.head.appendChild(style);
                
                // Force remove persistent elements
                var selectors = [
                    '[class*="popup"]', '[id*="popup"]', '.modal', '[class*="newsletter"]', 
                    '[class*="subscribe"]', '[class*="cookie"]', '.ad', '.sponsored',
                    'aside', '.sidebar', 'video', 'iframe[src*="youtube"]'
                ];
                selectors.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) { 
                        el.style.display = 'none'; 
                        el.setAttribute('data-shield-removed', 'clean-website');
                    });
                });
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null)
}

private fun injectDarkPatternBlocker(view: WebView?) {
    val js = """
        (function() {
            try {
                // 1. Uncheck auto-selected subscriptions / "accept all"
                document.querySelectorAll('input[type="checkbox"], input[type="radio"]').forEach(function(cb) {
                    var label = cb.closest('label') || cb.parentElement;
                    var txt = (label ? label.innerText : cb.value || '').toLowerCase();
                    if (txt.includes('subscribe') || txt.includes('newsletter') || txt.includes('marketing') || txt.includes('accept all') || txt.includes('opt in')) {
                        if (cb.checked) cb.checked = false;
                        // Also uncheck "I agree to terms" if bundled with subscription
                        if (txt.includes('terms') || txt.includes('conditions')) cb.checked = false;
                    }
                });
                
                // 2. Confirm-shaming text & tricky buttons
                document.querySelectorAll('button, a, [role="button"], div[onclick]').forEach(function(btn) {
                    var txt = (btn.innerText || btn.textContent || '').toLowerCase().trim();
                    if (txt.includes("no thanks") || txt.includes("no, i don't") || txt.includes("don't want") || txt.includes("maybe later") || txt.includes("i'll pass")) {
                        btn.style.opacity = '0.5';
                        btn.style.border = '1px dashed #22C55E';
                        btn.title = 'This looks like confirm-shaming. Click if you really want to skip.';
                    }
                    if (txt.includes("accept all cookies") || txt.includes("agree and continue") || txt.includes("allow all")) {
                        btn.style.border = '2px solid #22C55E';
                        btn.title = 'Consider "Reject all" or customize instead';
                    }
                });
                
                // 3. Subscription cancellation obstacles - make cancel easy to find
                document.querySelectorAll('a, button, [role="button"]').forEach(function(el) {
                    var txt = (el.innerText || el.textContent || '').toLowerCase();
                    if (txt.includes('cancel') || txt.includes('unsubscribe') || txt.includes('stop') || txt.includes('end subscription')) {
                        el.style.outline = '3px solid #22C55E';
                        el.style.outlineOffset = '3px';
                        el.style.fontWeight = 'bold';
                        if (el.style.display === 'none') el.style.display = '';
                    }
                });
                
                // 4. Hidden close buttons - make them obvious
                document.querySelectorAll('[class*="close"], [id*="close"], .modal-close, button[aria-label*="close"], [title*="close"]').forEach(function(el) {
                    el.style.minWidth = '28px';
                    el.style.minHeight = '28px';
                    el.style.opacity = '1';
                    el.style.fontSize = '18px';
                    el.style.zIndex = '999999';
                });
                
                // 5. Forced newsletter / popup removal (aggressive)
                setTimeout(function() {
                    var popups = document.querySelectorAll('[class*="newsletter"], [id*="newsletter"], [class*="popup"], [class*="modal"], [class*="overlay"], [aria-modal="true"]');
                    popups.forEach(function(el) {
                        if (el.offsetHeight > 80 || el.offsetWidth > 200) {
                            el.style.display = 'none';
                            el.setAttribute('data-shield-hidden', 'dark-pattern');
                        }
                    });
                }, 600);
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}

// === Mobile Number Risk Scoring + Fake Customer Care Number Blocker (FRI integration, first feature) ===
// Covers high-risk senders for: Banks, UPI apps, Airlines, Courier services, E-commerce support, Gas/electricity bill help
// Uses on-device risk scoring (see OnDeviceRuleEngine.phoneRiskScore). High risk -> prominent warning.
// "Block or warn on high risk" via user action (add to security blocks) or scanner/browser banner.
// For true incoming SMS/calls: use scanner on message screenshot (or future BroadcastReceiver for full interception).
private fun injectFakeCustomerCareWarner(view: WebView?) {
    val js = """
        (function() {
            try {
                var suspiciousKeywords = [
                    'customer care', 'helpline', 'toll free', 'support', 'call us', 'contact us', 
                    'customer support', 'refund', 'kyc', 
                    'bank', 'sbi', 'hdfc', 'icici', 'axis', 'paytm', 'phonepe', 'gpay', 'bhim',
                    'delhivery', 'ekart', 'dtdc', 'fedex', 'ups',
                    'air india', 'indigo', 'spicejet', 'goair', 'vistara',
                    'electricity', 'mseb', 'bescom', 'tneb', 'gas', 'lpg', 'indane', 'hp gas',
                    'ecommerce', 'amazon', 'flipkart', 'myntra', 'support number'
                ];
                var phoneRegex = /(?:\+?91[\s-]?)?[6-9]\d{9}/g;
                
                function showWarning(num, context, risk) {
                    var banner = document.createElement('div');
                    banner.style.cssText = 'position:fixed;bottom:80px;left:8px;right:8px;background:#fef2f2;border:1px solid #ef4444;color:#7f1d1d;padding:12px;border-radius:12px;z-index:999999;font-size:13px;box-shadow:0 6px 16px rgba(0,0,0,0.2);font-family:sans-serif';
                    var riskText = risk ? ' (Risk: ' + risk + '/100 - High risk sender per local FRI heuristic)' : '';
                    banner.innerHTML = 
                        '<div style="display:flex;align-items:flex-start">' +
                        '<span style="font-size:16px;margin-right:8px">⚠️</span>' +
                        '<div style="flex:1">' +
                        '<strong style="display:block;margin-bottom:2px">This number may not be official.' + riskText + '</strong>' +
                        '<span style="font-size:12px">Verify on the official app or website before calling.</span>' +
                        '<div style="margin-top:4px;font-size:11px;opacity:0.85">' + num + ' — ' + context.substring(0, 70) + (context.length > 70 ? '...' : '') + '</div>' +
                        '</div>' +
                        '<span style="margin-left:8px;cursor:pointer;font-weight:bold" onclick="this.parentElement.remove()">✕</span>' +
                        '</div>';
                    
                    document.body.appendChild(banner);
                    setTimeout(function() { if (banner && banner.parentNode) banner.parentNode.removeChild(banner); }, 20000);
                }
                
                // Scan visible text
                var walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, null, false);
                var node;
                while ((node = walker.nextNode())) {
                    var txt = node.textContent || '';
                    var matches = txt.match(phoneRegex);
                    if (matches && matches.length > 0) {
                        var parentEl = node.parentElement;
                        var parentTxt = (parentEl ? parentEl.innerText || parentEl.textContent || '' : txt).toLowerCase();
                        var isSuspicious = suspiciousKeywords.some(function(kw) { return parentTxt.includes(kw); });
                        if (isSuspicious) {
                            matches.forEach(function(num) {
                                showWarning(num, parentTxt, null); // risk computed on-device in Kotlin side for scanner
                            });
                        }
                    }
                }
                
                // Scan tel: links and click-to-call
                document.querySelectorAll('a[href^="tel:"], a[href^="callto:"], [onclick*="tel"], [onclick*="call"]').forEach(function(link) {
                    var href = link.getAttribute('href') || '';
                    var num = href.replace(/tel:|callto:|\D/g, '').trim();
                    if (!num) num = (link.innerText || link.textContent || '').replace(/\D/g, '');
                    if (num.length >= 10) {
                        var context = (link.innerText || '') + ' ' + (link.closest('div,section,p,li') ? link.closest('div,section,p,li').innerText || '' : '');
                        if (suspiciousKeywords.some(function(kw){ return context.toLowerCase().includes(kw); })) {
                            showWarning(num, context, null);
                        }
                    }
                });
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}

// === Anti-adblock bypass tools (advanced features for sites that detect adblockers) ===

private fun injectScriptletInjection(view: WebView?) {
    // Basic uBO-style scriptlets to neuter common adblock detections
    val js = """
        (function() {
            try {
                // Scriptlet: abort-on-property-read (common anti-adblock)
                var abort = function(prop) {
                    var parts = prop.split('.');
                    var obj = window;
                    for (var i = 0; i < parts.length - 1; i++) {
                        if (!obj[parts[i]]) obj[parts[i]] = {};
                        obj = obj[parts[i]];
                    }
                    var last = parts[parts.length-1];
                    Object.defineProperty(obj, last, { get: function() { throw new Error(''); } });
                };
                // Common detections
                abort('adblock');
                abort('isAdblock');
                abort('adBlockDetected');
                abort('adblockerDetected');
                // Prevent some bait scripts
                document.querySelectorAll('script[src*="ad"], script[src*="block"]').forEach(function(s){ s.remove(); });
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}

private fun injectAntiAdblockDefuser(view: WebView?) {
    val js = """
        (function() {
            try {
                // Defuse common anti-adblock patterns
                if (window.adblock) window.adblock = false;
                if (window.adblocker) window.adblocker = false;
                if (typeof window.canRunAds !== 'undefined') window.canRunAds = true;
                
                // Override common anti-adblock functions
                var noop = function(){};
                if (window.detectAdblock) window.detectAdblock = noop;
                if (window.checkAdblock) window.checkAdblock = noop;
                if (window.showAdblockWarning) window.showAdblockWarning = noop;
                
                // Remove elements that are anti-adblock messages
                setTimeout(function() {
                    document.querySelectorAll('[class*="adblock"], [id*="adblock"], [class*="ad-block"], .adblocker, .ad-blocker, [data-adblock]').forEach(function(el){
                        if (el.innerText && el.innerText.length < 300) el.remove();
                    });
                }, 1200);
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}

private fun injectPopupTrapBlocker(view: WebView?) {
    val js = """
        (function() {
            try {
                // Prevent popup traps and forced window.open
                var originalOpen = window.open;
                window.open = function(url, name, specs) {
                    if (url && (url.includes('ad') || url.includes('pop') || url.includes('track') || specs)) {
                        console.log('[Shield] Blocked potential popup trap');
                        return null;
                    }
                    return originalOpen.apply(this, arguments);
                };
                
                // Block addEventListener for certain popup events
                var origAdd = EventTarget.prototype.addEventListener;
                EventTarget.prototype.addEventListener = function(type, listener, options) {
                    if (type === 'click' || type === 'touchstart') {
                        var orig = listener;
                        listener = function(e) {
                            if (e && e.isTrusted === false) return; // synthetic
                            return orig.apply(this, arguments);
                        };
                    }
                    return origAdd.apply(this, arguments);
                };
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}

private fun injectRedirectChainCleaner(view: WebView?) {
    val js = """
        (function() {
            try {
                // Detect and warn on suspicious redirect chains (common for ad walls)
                var redirectCount = 0;
                var lastLocation = location.href;
                var observer = new MutationObserver(function() {
                    if (location.href !== lastLocation) {
                        redirectCount++;
                        lastLocation = location.href;
                        if (redirectCount > 3) {
                            var banner = document.createElement('div');
                            banner.style.cssText = 'position:fixed;top:0;left:0;right:0;background:#fef3c7;color:#92400e;padding:8px;font-size:12px;z-index:999999;text-align:center';
                            banner.textContent = '[Shield] Multiple redirects detected. This site may be using redirect chains to serve ads.';
                            document.body.appendChild(banner);
                            setTimeout(function(){ banner.remove(); }, 8000);
                            redirectCount = 0;
                        }
                    }
                });
                observer.observe(document, { childList: true, subtree: true });
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}

private fun injectAntiPaywallWarning(view: WebView?) {
    // Warning only, no bypass (ethical)
    val js = """
        (function() {
            try {
                var paywallSelectors = ['.paywall', '#paywall', '.subscription-wall', '[class*="paywall"]', '[id*="paywall"]', '.metered-paywall', '.premium-content'];
                setTimeout(function() {
                    paywallSelectors.forEach(function(sel) {
                        document.querySelectorAll(sel).forEach(function(el) {
                            if (el.offsetHeight > 50) {
                                el.style.filter = 'blur(2px)';
                                var warn = document.createElement('div');
                                warn.style.cssText = 'background:#dbeafe;color:#1e40af;padding:8px;margin:8px;border-radius:6px;font-size:12px';
                                warn.textContent = '[Shield] Paywall detected. This tool shows a warning only and does not bypass paywalls.';
                                el.parentNode.insertBefore(warn, el);
                            }
                        });
                    });
                }, 1500);
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}

private fun injectSponsoredWidgetRemover(view: WebView?) {
    val js = """
        (function() {
            try {
                var sponsoredSelectors = [
                    '[class*="sponsored"]', '[id*="sponsored"]', '[data-sponsored]', 
                    '.sponsored-content', '.promoted', '[class*="recommendation"]', 
                    '.widget-sponsored', '[aria-label*="sponsored"]', '.ad-recommend'
                ];
                sponsoredSelectors.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        el.style.display = 'none';
                        el.setAttribute('data-shield-removed', 'sponsored');
                    });
                });
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}

private fun injectFakeCountdownRemover(view: WebView?) {
    val js = """
        (function() {
            try {
                // Remove fake countdowns / timers that force waiting
                var countdownSelectors = [
                    '[class*="countdown"]', '[id*="countdown"]', '[class*="timer"]', 
                    '[class*="wait"]', '.fake-timer', '[data-countdown]'
                ];
                countdownSelectors.forEach(function(sel) {
                    document.querySelectorAll(sel).forEach(function(el) {
                        if (el.innerText && /\d+[:\s]\d+/.test(el.innerText)) {
                            el.remove();
                        }
                    });
                });
                // Also kill setInterval/setTimeout that look like ad countdowns (heuristic)
                var origSetInterval = window.setInterval;
                window.setInterval = function(fn, delay) {
                    if (delay < 1000 && String(fn).includes('count') || String(fn).includes('timer')) {
                        return -1; // block suspicious short timers
                    }
                    return origSetInterval.apply(this, arguments);
                };
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}

private fun injectOverlayRemover(view: WebView?) {
    val js = """
        (function() {
            try {
                // Remove full-page blocking overlays / modals used for anti-adblock
                setTimeout(function() {
                    document.querySelectorAll('div, section, aside').forEach(function(el) {
                        var style = window.getComputedStyle(el);
                        var z = parseInt(style.zIndex) || 0;
                        if (z > 1000 && (style.position === 'fixed' || style.position === 'absolute') && 
                            el.offsetWidth > window.innerWidth * 0.7 && el.offsetHeight > window.innerHeight * 0.6) {
                            if (!el.querySelector('input, textarea, form')) { // don't kill real modals
                                el.style.display = 'none';
                                el.setAttribute('data-shield-removed', 'overlay');
                            }
                        }
                    });
                }, 800);
            } catch(e) {}
        })();
    """.trimIndent()
    view?.evaluateJavascript(js, null);
}
