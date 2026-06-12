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
            AnimatedVisibility(
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
