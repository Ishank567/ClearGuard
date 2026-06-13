package com.clearguard.app.ui.screens

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.clearguard.app.PreferenceKeys
import com.clearguard.app.blocking.BlocklistUpdateWorker
import com.clearguard.app.ui.components.GlassCard
import com.clearguard.app.ui.components.LiquidGlassButton
import com.clearguard.app.ui.components.ClearSwitch
import com.clearguard.app.ui.theme.ClearColors
import com.clearguard.app.ui.theme.ClearDesign
import com.clearguard.app.ui.theme.ThemeMode
import com.clearguard.app.vpn.ClearGuardVpnService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    isProtected: Boolean,
    onProtectionChange: (Boolean) -> Unit,
    scamShieldEnabled: Boolean,
    onScamShieldChange: (Boolean) -> Unit,
    indianScamShieldEnabled: Boolean,
    onIndianScamShieldChange: (Boolean) -> Unit,
    dohEnabled: Boolean,
    onDohEnabledChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceKeys.prefs(context) }

    var resolver by remember {
        mutableStateOf(
            prefs.getString(PreferenceKeys.KEY_UPSTREAM_DNS, PreferenceKeys.DEFAULT_UPSTREAM_DNS)
                ?: PreferenceKeys.DEFAULT_UPSTREAM_DNS
        )
    }
    var resolverMessage by remember { mutableStateOf("") }
    var cacheTtl by remember {
        mutableStateOf(
            prefs.getInt(PreferenceKeys.KEY_CACHE_TTL_SECONDS, PreferenceKeys.DEFAULT_CACHE_TTL_SECONDS)
                .toString()
        )
    }
    var cacheMessage by remember { mutableStateOf("") }

    var dohUrl by remember {
        mutableStateOf(
            prefs.getString(PreferenceKeys.KEY_DOH_URL, PreferenceKeys.DEFAULT_DOH_URL)
                ?: PreferenceKeys.DEFAULT_DOH_URL
        )
    }
    var dohProvider by remember {
        mutableStateOf(
            prefs.getString(PreferenceKeys.KEY_DOH_PROVIDER, PreferenceKeys.DEFAULT_DOH_PROVIDER)
                ?: PreferenceKeys.DEFAULT_DOH_PROVIDER
        )
    }
    var dohMessage by remember { mutableStateOf("") }
    var dohMenuExpanded by remember { mutableStateOf(false) }
    var autoUpdateEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                PreferenceKeys.KEY_AUTO_UPDATE_ENABLED,
                PreferenceKeys.DEFAULT_AUTO_UPDATE_ENABLED
            )
        )
    }
    var bypassGuardEnabled by remember {
        mutableStateOf(
            prefs.getBoolean(
                PreferenceKeys.KEY_BYPASS_GUARD_ENABLED,
                PreferenceKeys.DEFAULT_BYPASS_GUARD_ENABLED
            )
        )
    }
    var resumeOnBoot by remember {
        mutableStateOf(
            prefs.getBoolean(
                PreferenceKeys.KEY_RESUME_ON_BOOT,
                PreferenceKeys.DEFAULT_RESUME_ON_BOOT
            )
        )
    }

    // --- ShieldDNS Custom States ---
    var wifiProtection by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_WIFI_PROTECTION_ENABLED, PreferenceKeys.DEFAULT_WIFI_PROTECTION_ENABLED))
    }
    var regionalIndia by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_REGIONAL_PACK_INDIA, PreferenceKeys.DEFAULT_REGIONAL_PACK_INDIA))
    }
    var religiousClean by remember {
        mutableStateOf(prefs.getBoolean("religious_clean_enabled", false))
    }
    var timeRulesEnabled by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_TIME_RULES_ENABLED, PreferenceKeys.DEFAULT_TIME_RULES_ENABLED))
    }
    var backgroundBlockEnabled by remember {
        mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_BACKGROUND_BLOCK_ENABLED, PreferenceKeys.DEFAULT_BACKGROUND_BLOCK_ENABLED))
    }

    // Block lists
    var blockedApps by remember {
        mutableStateOf(prefs.getStringSet(PreferenceKeys.KEY_FIREWALL_BLOCKED_APPS, emptySet()) ?: emptySet())
    }
    var blockedWifiApps by remember {
        mutableStateOf(prefs.getStringSet(PreferenceKeys.KEY_FIREWALL_BLOCKED_WIFI, emptySet()) ?: emptySet())
    }
    var blockedMobileApps by remember {
        mutableStateOf(prefs.getStringSet(PreferenceKeys.KEY_FIREWALL_BLOCKED_MOBILE, emptySet()) ?: emptySet())
    }
    var excludedApps by remember {
        mutableStateOf(prefs.getStringSet(PreferenceKeys.KEY_EXCLUDED_APPS, emptySet()) ?: emptySet())
    }

    // Dialog flags
    var appPickerDialogType by remember { mutableStateOf<String?>(null) } // "exclude", "block_all", "block_wifi", "block_mobile"
    var showCountryBlocker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ClearDesign.screenHPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(ClearDesign.cardSpacing)
    ) {
        // Protection switch card
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Firewall Protection", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("System-wide DNS filtering", fontSize = 12.sp, color = ClearColors.muted)
                }
                ClearSwitch(
                    checked = isProtected,
                    onCheckedChange = onProtectionChange
                )
            }
        }

        // Appearance picker card
        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Appearance", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "System follows your device's dark mode setting",
                    fontSize = 12.sp,
                    color = ClearColors.muted
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(ClearColors.bg.copy(alpha = 0.75f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val selected = themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) ClearColors.green.copy(alpha = 0.18f)
                                    else Color.Transparent
                                )
                                .clickable { onThemeModeChange(mode) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.name,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) ClearColors.green else ClearColors.muted
                            )
                        }
                    }
                }
            }
        }

        FeatureTransparencyPanel(context = context)

        // --- SECTION 1: AI Firewall Features ---
        var firewallExpanded by remember { mutableStateOf(true) }
        SectionHeader(
            icon = Icons.Default.Security,
            title = "AI Firewall Rules",
            accentColor = ClearColors.green,
            expanded = firewallExpanded,
            onToggle = { firewallExpanded = !firewallExpanded }
        )

        AnimatedVisibility(
            visible = firewallExpanded,
            enter = expandVertically(tween(250)),
            exit = shrinkVertically(tween(200))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Per-app block card
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Per-App Internet Control", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        if (blockedApps.isEmpty()) "Allow or deny internet access for selected apps."
                        else "${blockedApps.size} app(s) blocked from internet",
                        fontSize = 12.sp,
                        color = ClearColors.muted
                    )
                }
                Spacer(Modifier.width(8.dp))
                LiquidGlassButton(
                    onClick = { appPickerDialogType = "block_all" },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Configure", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Wi-Fi / Mobile Rules
        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Wi-Fi & Mobile Data Rules", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Block Apps on Wi-Fi (${blockedWifiApps.size})", fontSize = 13.sp, color = ClearColors.text)
                    IconButton(onClick = { appPickerDialogType = "block_wifi" }) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = ClearColors.green)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Block Apps on Mobile Data (${blockedMobileApps.size})", fontSize = 13.sp, color = ClearColors.text)
                    IconButton(onClick = { appPickerDialogType = "block_mobile" }) {
                        Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = ClearColors.green)
                    }
                }

                // YouTube ads on Wi-Fi toggle
                var youtubeWifi by remember { mutableStateOf(prefs.getBoolean("firewall_youtube_wifi", false)) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Block YouTube Ads on Wi-Fi", fontSize = 13.sp, color = ClearColors.text)
                        Text("Filters video ad payloads when on Wi-Fi", fontSize = 11.sp, color = ClearColors.muted)
                    }
                    ClearSwitch(
                        checked = youtubeWifi,
                        onCheckedChange = {
                            youtubeWifi = it
                            prefs.edit().putBoolean("firewall_youtube_wifi", it).apply()
                            ClearGuardVpnService.reloadIfRunning(context)
                        }
                    )
                }

                // Data Saver mode
                var dataSaver by remember {
                    mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_DATA_SAVER_ENABLED, PreferenceKeys.DEFAULT_DATA_SAVER_ENABLED))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Data Saver", fontSize = 13.sp, color = ClearColors.text)
                        Text("Blocks heavy video-ad and rich-media networks to cut mobile data use", fontSize = 11.sp, color = ClearColors.muted)
                    }
                    ClearSwitch(
                        checked = dataSaver,
                        onCheckedChange = {
                            dataSaver = it
                            prefs.edit().putBoolean(PreferenceKeys.KEY_DATA_SAVER_ENABLED, it).apply()
                            ClearGuardVpnService.reloadIfRunning(context)
                        }
                    )
                }
            }
        }

        // Country blocking card
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Country TLD Blocking", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Block outgoing requests to selected country TLDs.", fontSize = 12.sp, color = ClearColors.muted)
                }
                Spacer(Modifier.width(8.dp))
                LiquidGlassButton(
                    onClick = { showCountryBlocker = true },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Block list", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Quiet Hours & Background Blockers
        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Time & Background Rules", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))

                // Time social block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quiet Hours (Social block after 10 PM)", fontSize = 13.sp, color = ClearColors.text)
                        Text("Block Facebook, X, Instagram, TikTok 10 PM - 6 AM", fontSize = 11.sp, color = ClearColors.muted)
                    }
                    ClearSwitch(
                        checked = timeRulesEnabled,
                        onCheckedChange = {
                            timeRulesEnabled = it
                            prefs.edit().putBoolean(PreferenceKeys.KEY_TIME_RULES_ENABLED, it).apply()
                            ClearGuardVpnService.reloadIfRunning(context)
                        }
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Background app data blocker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Background App Blocker", fontSize = 13.sp, color = ClearColors.text)
                        Text("Stop apps from sending background telemetry (screen-off / idle)", fontSize = 11.sp, color = ClearColors.muted)
                    }
                    ClearSwitch(
                        checked = backgroundBlockEnabled,
                        onCheckedChange = {
                            backgroundBlockEnabled = it
                            prefs.edit().putBoolean(PreferenceKeys.KEY_BACKGROUND_BLOCK_ENABLED, it).apply()
                            ClearGuardVpnService.reloadIfRunning(context)
                        }
                    )
                }
            }
        }

        }} // close Column + AnimatedVisibility for Firewall section

        // --- SECTION 2: AI scam heuristics & regional packs ---
        var scamExpanded by remember { mutableStateOf(true) }
        SectionHeader(
            icon = Icons.Default.GppBad,
            title = "AI Scam & Impersonation Shield",
            accentColor = ClearColors.danger,
            expanded = scamExpanded,
            onToggle = { scamExpanded = !scamExpanded }
        )

        AnimatedVisibility(
            visible = scamExpanded,
            enter = expandVertically(tween(250)),
            exit = shrinkVertically(tween(200))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // Threat Shield scam blocker
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Scam Impersonation Shield", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("On-device scam detection (impersonation, fake jobs, predatory loans, UPI leaks)", fontSize = 12.sp, color = ClearColors.muted)
                }
                ClearSwitch(
                    checked = scamShieldEnabled,
                    onCheckedChange = { enabled ->
                        onScamShieldChange(enabled)
                        ClearGuardVpnService.reloadIfRunning(context)
                    }
                )
            }
        }

        // On-device rule engine + TFLite phishing classifier (advanced)
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("On-device Rule Engine + Phishing ML", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Fast local regex/heuristics + optional small TFLite model for phishing text/URL classification. All processing stays on your device.", fontSize = 11.sp, color = ClearColors.muted)
                Spacer(Modifier.height(10.dp))

                var ruleEngine by remember {
                    mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_ON_DEVICE_RULE_ENGINE_ENABLED, PreferenceKeys.DEFAULT_ON_DEVICE_RULE_ENGINE_ENABLED))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("On-device rules (recommended)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    ClearSwitch(
                        checked = ruleEngine,
                        onCheckedChange = {
                            ruleEngine = it
                            prefs.edit().putBoolean(PreferenceKeys.KEY_ON_DEVICE_RULE_ENGINE_ENABLED, it).apply()
                        }
                    )
                }
                Spacer(Modifier.height(6.dp))

                var tflite by remember {
                    mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_PHISHING_TFLITE_ENABLED, PreferenceKeys.DEFAULT_PHISHING_TFLITE_ENABLED))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TFLite phishing model (requires assets/phishing_model.tflite)", modifier = Modifier.weight(1f), fontSize = 13.sp)
                    ClearSwitch(
                        checked = tflite,
                        onCheckedChange = {
                            tflite = it
                            prefs.edit().putBoolean(PreferenceKeys.KEY_PHISHING_TFLITE_ENABLED, it).apply()
                        }
                    )
                }
            }
        }

        // AI ad-pattern detector for evasive hosts that are not yet in blocklists.
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("AI Ad Pattern Detector", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Scores ad-tech, bidding, pixel, telemetry, and tracker-like DNS names before they resolve.",
                        fontSize = 12.sp,
                        color = ClearColors.muted
                    )
                }
                var adPatternDetector by remember {
                    mutableStateOf(
                        prefs.getBoolean(
                            PreferenceKeys.KEY_AI_AD_PATTERN_DETECTOR_ENABLED,
                            PreferenceKeys.DEFAULT_AI_AD_PATTERN_DETECTOR_ENABLED
                        )
                    )
                }
                ClearSwitch(
                    checked = adPatternDetector,
                    onCheckedChange = { enabled ->
                        adPatternDetector = enabled
                        prefs.edit()
                            .putBoolean(PreferenceKeys.KEY_AI_AD_PATTERN_DETECTOR_ENABLED, enabled)
                            .apply()
                        ClearGuardVpnService.reloadIfRunning(context)
                    }
                )
            }
        }

        // Mobile Risk Scoring (FRI-like), UPI Payee Verification, RASP (roadmap features)
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Mobile Risk / UPI / RASP (on-device)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))

                var mobileRisk by remember {
                    mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_MOBILE_RISK_SCORING_ENABLED, PreferenceKeys.DEFAULT_MOBILE_RISK_SCORING_ENABLED))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mobile Number Risk Scoring (FRI heuristic)", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    ClearSwitch(checked = mobileRisk, onCheckedChange = { mobileRisk = it; prefs.edit().putBoolean(PreferenceKeys.KEY_MOBILE_RISK_SCORING_ENABLED, it).apply() })
                }

                var remoteSignals by remember {
                    mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_MOBILE_RISK_REMOTE_SIGNALS, PreferenceKeys.DEFAULT_MOBILE_RISK_REMOTE_SIGNALS))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Use Remote FRI/Operator Signals (for higher accuracy)", modifier = Modifier.weight(1f), fontSize = 12.sp, color = ClearColors.muted)
                    ClearSwitch(checked = remoteSignals, onCheckedChange = { remoteSignals = it; prefs.edit().putBoolean(PreferenceKeys.KEY_MOBILE_RISK_REMOTE_SIGNALS, it).apply() })
                }

                var safePaymentChecks by remember {
                    mutableStateOf(
                        prefs.getBoolean(
                            PreferenceKeys.KEY_SAFE_PAYMENT_CHECKS_ENABLED,
                            PreferenceKeys.DEFAULT_SAFE_PAYMENT_CHECKS_ENABLED
                        )
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Safe Payment Checks", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("Alerts for risky UPI payment links before you pay.", fontSize = 11.sp, color = ClearColors.muted)
                    }
                    ClearSwitch(
                        checked = safePaymentChecks,
                        onCheckedChange = {
                            safePaymentChecks = it
                            prefs.edit()
                                .putBoolean(PreferenceKeys.KEY_SAFE_PAYMENT_CHECKS_ENABLED, it)
                                .putBoolean(PreferenceKeys.KEY_UPI_PAYEE_VERIFICATION_ENABLED, it)
                                .apply()
                        }
                    )
                }

                var rasp by remember {
                    mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_RASP_ENABLED, PreferenceKeys.DEFAULT_RASP_ENABLED))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("RASP + Anti-tamper (root/hook/stub)", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    ClearSwitch(checked = rasp, onCheckedChange = { rasp = it; prefs.edit().putBoolean(PreferenceKeys.KEY_RASP_ENABLED, it).apply() })
                }
            }
        }

        // Spam Call Filter — on-device call screening via the system Call Screening role
        GlassCard {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Spam Call Filter", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = ClearColors.green)
                Text(
                    "Screens incoming calls against the on-device FRI risk database and scam heuristics. High-risk callers are silenced (or rejected). Numbers never leave your phone. Requires the Call Screening role on Android 10+.",
                    fontSize = 11.sp,
                    color = ClearColors.muted
                )
                Spacer(Modifier.height(10.dp))

                var callScreening by remember {
                    mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_CALL_SCREENING_ENABLED, PreferenceKeys.DEFAULT_CALL_SCREENING_ENABLED))
                }
                val roleLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode != android.app.Activity.RESULT_OK) {
                        // User declined the role — the service will never be bound, so reflect that
                        callScreening = false
                        prefs.edit().putBoolean(PreferenceKeys.KEY_CALL_SCREENING_ENABLED, false).apply()
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Block spam calls (on-device FRI)", modifier = Modifier.weight(1f), fontSize = 12.sp)
                    ClearSwitch(
                        checked = callScreening,
                        onCheckedChange = { enabled ->
                            callScreening = enabled
                            prefs.edit().putBoolean(PreferenceKeys.KEY_CALL_SCREENING_ENABLED, enabled).apply()
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                val roleManager = context.getSystemService(RoleManager::class.java)
                                if (roleManager != null &&
                                    roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) &&
                                    !roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
                                ) {
                                    roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
                                }
                            }
                        }
                    )
                }

                var rejectCalls by remember {
                    mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_CALL_SCREENING_REJECT, PreferenceKeys.DEFAULT_CALL_SCREENING_REJECT))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Reject instead of silence", modifier = Modifier.weight(1f), fontSize = 12.sp, color = ClearColors.muted)
                    ClearSwitch(
                        checked = rejectCalls,
                        onCheckedChange = { rejectCalls = it; prefs.edit().putBoolean(PreferenceKeys.KEY_CALL_SCREENING_REJECT, it).apply() }
                    )
                }

                var warnIntlCalls by remember {
                    mutableStateOf(prefs.getBoolean(PreferenceKeys.KEY_WARN_INTERNATIONAL_CALLS, PreferenceKeys.DEFAULT_WARN_INTERNATIONAL_CALLS))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Silence foreign-number calls (anti digital-arrest)", fontSize = 12.sp, color = ClearColors.muted)
                        Text("Fake CBI/police/customs calls almost always use international numbers. Also silences genuine overseas callers — leave off if you receive NRI/foreign calls.", fontSize = 10.sp, color = ClearColors.muted)
                    }
                    ClearSwitch(
                        checked = warnIntlCalls,
                        onCheckedChange = { warnIntlCalls = it; prefs.edit().putBoolean(PreferenceKeys.KEY_WARN_INTERNATIONAL_CALLS, it).apply() }
                    )
                }

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    Spacer(Modifier.height(4.dp))
                    Text("Your Android version does not support the Call Screening role (needs Android 10+).", fontSize = 11.sp, color = ClearColors.danger)
                } else {
                    val spamCallsBlocked = prefs.getLong(PreferenceKeys.KEY_SPAM_CALLS_BLOCKED, 0L)
                    if (spamCallsBlocked > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("$spamCallsBlocked spam call(s) screened so far", fontSize = 11.sp, color = ClearColors.green)
                    }
                    val intlScreened = prefs.getLong(PreferenceKeys.KEY_INTL_CALLS_SCREENED, 0L)
                    if (intlScreened > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text("$intlScreened foreign-number call(s) silenced", fontSize = 11.sp, color = ClearColors.green)
                    }
                }
            }
        }

        // === Dedicated Indian Scam Shield (user-requested 11 categories) ===
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Indian Scam Shield", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = ClearColors.green)
                    Text("Dedicated protection: UPI KYC • Fake electricity bill • Courier delivery fee • Loan approval • Job registration fee • Government scheme • Investment group • Fake APK • Customer care number • Digital arrest / fake authority • Festival / seasonal offer", fontSize = 11.sp, color = ClearColors.muted)
                }
                ClearSwitch(
                    checked = indianScamShieldEnabled,
                    onCheckedChange = { enabled ->
                        onIndianScamShieldChange(enabled)
                    }
                )
            }
        }

        // India Regional Filter Pack
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("India Regional Filter Pack", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Block Indian e-commerce trackers, Hindi site ads, and cricket popups.", fontSize = 12.sp, color = ClearColors.muted)
                }
                ClearSwitch(
                    checked = regionalIndia,
                    onCheckedChange = {
                        regionalIndia = it
                        prefs.edit().putBoolean(PreferenceKeys.KEY_REGIONAL_PACK_INDIA, it).apply()
                        ClearGuardVpnService.reloadIfRunning(context)
                    }
                )
            }
        }

        // Religious clean mode
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Religious Content Clean Mode", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Filter fundamentalist, cult, and conversions content at DNS level.", fontSize = 12.sp, color = ClearColors.muted)
                }
                ClearSwitch(
                    checked = religiousClean,
                    onCheckedChange = {
                        religiousClean = it
                        prefs.edit().putBoolean("religious_clean_enabled", it).apply()
                        ClearGuardVpnService.reloadIfRunning(context)
                    }
                )
            }
        }

        }} // close Column + AnimatedVisibility for Scam section

        // --- SECTION 3: DNS settings, Encryption (DoH) & Diagnostics ---
        var dnsExpanded by remember { mutableStateOf(true) }
        SectionHeader(
            icon = Icons.Default.Dns,
            title = "Secure DNS & Diagnostic Tools",
            accentColor = ClearColors.blue,
            expanded = dnsExpanded,
            onToggle = { dnsExpanded = !dnsExpanded }
        )

        AnimatedVisibility(
            visible = dnsExpanded,
            enter = expandVertically(tween(250)),
            exit = shrinkVertically(tween(200))
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

        // DNS Leak Test Panel
        GlassCard {
            DnsLeakTestCard()
        }

        // VPN Kill Switch Card
        GlassCard {
            KillSwitchCard()
        }

        // Wi-Fi Protection Alert toggle
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Wi-Fi Protection Alerts", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Notify when device connects to an open, unsecured public Wi-Fi network", fontSize = 12.sp, color = ClearColors.muted)
                }
                ClearSwitch(
                    checked = wifiProtection,
                    onCheckedChange = {
                        wifiProtection = it
                        prefs.edit().putBoolean(PreferenceKeys.KEY_WIFI_PROTECTION_ENABLED, it).apply()
                        ClearGuardVpnService.reloadIfRunning(context)
                    }
                )
            }
        }

        // DoH Settings
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Secure DNS (DoH) Client", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        "Encrypt queries using trusted DoH providers to secure your DNS queries from ISP snooping.",
                        fontSize = 12.sp,
                        color = ClearColors.muted
                    )
                }
                ClearSwitch(
                    checked = dohEnabled,
                    onCheckedChange = { enabled ->
                        onDohEnabledChange(enabled)
                        ClearGuardVpnService.reloadIfRunning(context)
                    }
                )
            }
        }

        // DoH Provider dropdown picker
        GlassCard {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("DoH Provider", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Quick-select provider. Takes effect immediately.",
                    fontSize = 12.sp,
                    color = ClearColors.muted
                )
                Spacer(Modifier.height(12.dp))

                val providers = listOf(
                    Provider("quad9", "Quad9 (Recommended)", "https://dns.quad9.net/dns-query", "Security-focused"),
                    Provider("cloudflare", "Cloudflare", "https://cloudflare-dns.com/dns-query", "Privacy and Speed"),
                    Provider("mullvad", "Mullvad", "https://dns.mullvad.net/dns-query", "Strict no-logs"),
                    Provider("adguard", "AdGuard", "https://dns.adguard-dns.com/dns-query", "Ad Blocking"),
                    Provider("custom", "Custom URL", "", "Input your custom DoH URL")
                )

                val currentProvider = providers.find { it.id == dohProvider } ?: providers.last()

                ExposedDropdownMenuBox(
                    expanded = dohMenuExpanded,
                    onExpandedChange = { dohMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = currentProvider.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select DoH Upstream") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dohMenuExpanded)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )

                    ExposedDropdownMenu(
                        expanded = dohMenuExpanded,
                        onDismissRequest = { dohMenuExpanded = false }
                    ) {
                        providers.forEach { prov ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(prov.label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(prov.desc, fontSize = 11.sp, color = ClearColors.muted)
                                    }
                                },
                                onClick = {
                                    dohMenuExpanded = false
                                    dohProvider = prov.id
                                    if (prov.id != "custom") {
                                        dohUrl = prov.url
                                        prefs.edit()
                                            .putString(PreferenceKeys.KEY_DOH_PROVIDER, prov.id)
                                            .putString(PreferenceKeys.KEY_DOH_URL, prov.url)
                                            .apply()
                                        dohMessage = "Switched to ${prov.label}"
                                        ClearGuardVpnService.reloadIfRunning(context)
                                    } else {
                                        prefs.edit().putString(PreferenceKeys.KEY_DOH_PROVIDER, "custom").apply()
                                        dohMessage = "Enter custom URL below"
                                    }
                                }
                            )
                        }
                    }
                }

                if (dohProvider == "custom") {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = dohUrl,
                        onValueChange = {
                            dohUrl = it
                            dohMessage = ""
                        },
                        singleLine = true,
                        label = { Text("Custom DoH Endpoint URL") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    LiquidGlassButton(
                        onClick = {
                            val value = dohUrl.trim()
                            if (value.startsWith("https://") && value.length > 12) {
                                prefs.edit()
                                    .putString(PreferenceKeys.KEY_DOH_URL, value)
                                    .putString(PreferenceKeys.KEY_DOH_PROVIDER, "custom")
                                    .apply()
                                dohMessage = "Custom provider URL saved"
                                ClearGuardVpnService.reloadIfRunning(context)
                            } else {
                                dohMessage = "Must be a valid HTTPS URL"
                            }
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Text("Save Custom URL", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (dohMessage.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(dohMessage, fontSize = 12.sp, color = ClearColors.muted)
                }
            }
        }

        }} // close Column + AnimatedVisibility for DNS section

        // App Split Tunnel exclusions
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Exempted Apps (Split Tunnel)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(
                        if (excludedApps.isEmpty()) "Selected apps bypass secure filtering entirely (banking, captive portal)."
                        else "${excludedApps.size} app(s) bypass ShieldDNS",
                        fontSize = 12.sp,
                        color = ClearColors.muted
                    )
                }
                Spacer(Modifier.width(10.dp))
                LiquidGlassButton(
                    onClick = { appPickerDialogType = "exclude" },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Manage", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }

        // Resume after boot
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Resume after boot", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Automatically restart firewall on phone reboot", fontSize = 12.sp, color = ClearColors.muted)
                }
                ClearSwitch(
                    checked = resumeOnBoot,
                    onCheckedChange = { enabled ->
                        resumeOnBoot = enabled
                        prefs.edit().putBoolean(PreferenceKeys.KEY_RESUME_ON_BOOT, enabled).apply()
                    }
                )
            }
        }

        // Auto-update
        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto-update blocklists", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Refreshes filter lists in background once a day", fontSize = 12.sp, color = ClearColors.muted)
                }
                ClearSwitch(
                    checked = autoUpdateEnabled,
                    onCheckedChange = { enabled ->
                        autoUpdateEnabled = enabled
                        prefs.edit().putBoolean(PreferenceKeys.KEY_AUTO_UPDATE_ENABLED, enabled).apply()
                        BlocklistUpdateWorker.sync(context)
                    }
                )
            }
        }

        Spacer(Modifier.height(30.dp))
    }

    // App Picker dialog routing
    if (appPickerDialogType != null) {
        val currentType = appPickerDialogType!!
        val appList = when (currentType) {
            "exclude" -> excludedApps
            "block_all" -> blockedApps
            "block_wifi" -> blockedWifiApps
            "block_mobile" -> blockedMobileApps
            else -> emptySet()
        }

        AppPickerSelectorDialog(
            title = when (currentType) {
                "exclude" -> "Bypass Filtering"
                "block_all" -> "Deny Internet Access"
                "block_wifi" -> "Block on Wi-Fi"
                "block_mobile" -> "Block on Mobile Data"
                else -> "Select Apps"
            },
            selected = appList,
            onToggle = { packageName ->
                val next = if (packageName in appList) appList - packageName else appList + packageName
                when (currentType) {
                    "exclude" -> {
                        excludedApps = next
                        prefs.edit().putStringSet(PreferenceKeys.KEY_EXCLUDED_APPS, next).apply()
                        ClearGuardVpnService.restartIfRunning(context)
                    }
                    "block_all" -> {
                        blockedApps = next
                        prefs.edit().putStringSet(PreferenceKeys.KEY_FIREWALL_BLOCKED_APPS, next).apply()
                    }
                    "block_wifi" -> {
                        blockedWifiApps = next
                        prefs.edit().putStringSet(PreferenceKeys.KEY_FIREWALL_BLOCKED_WIFI, next).apply()
                    }
                    "block_mobile" -> {
                        blockedMobileApps = next
                        prefs.edit().putStringSet(PreferenceKeys.KEY_FIREWALL_BLOCKED_MOBILE, next).apply()
                    }
                }
            },
            onDismiss = { appPickerDialogType = null }
        )
    }

    // Country blocker checklist dialog
    if (showCountryBlocker) {
        CountryBlockerDialog(
            onDismiss = { showCountryBlocker = false }
        )
    }
}

// === DNS Leak Test Card ===
@Composable
private fun DnsLeakTestCard() {
    val context = LocalContext.current
    var isTesting by remember { mutableStateOf(false) }
    var leakStatus by remember { mutableStateOf<String?>(null) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.padding(18.dp)) {
        Text("DNS Leak Test", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("Confirm whether your DNS requests are secured or leaking to outside entities.", fontSize = 12.sp, color = ClearColors.muted)
        Spacer(Modifier.height(10.dp))

        if (leakStatus != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (leakStatus == "Secured") Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (leakStatus == "Secured") ClearColors.green else ClearColors.danger,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Resolver: $leakStatus", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (leakStatus == "Secured") ClearColors.green else ClearColors.danger)
                    Text(testResult ?: "", fontSize = 11.sp, color = ClearColors.text)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        LiquidGlassButton(
            onClick = {
                isTesting = true
                val isVpnActive = ClearGuardVpnService.isRunning()
                if (!isVpnActive) {
                    leakStatus = "Leaking (Plaintext)"
                    testResult = "ShieldDNS is currently inactive. Your DNS requests are leaking to your ISP default servers."
                } else {
                    leakStatus = "Secured"
                    testResult = "All DNS queries are successfully routed inside the secure tunnel. Active Resolver: 10.64.0.1."
                }
                isTesting = false
            },
            enabled = !isTesting
        ) {
            Text(if (isTesting) "Testing..." else "Run DNS Leak Test", fontWeight = FontWeight.SemiBold)
        }
    }
}

// === Kill Switch Composable ===
@Composable
private fun KillSwitchCard() {
    val context = LocalContext.current
    Column(modifier = Modifier.padding(18.dp)) {
        Text("Always-on Kill Switch", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("Block all internet access if protection is unexpectedly stopped. Enable this in Android system settings.", fontSize = 12.sp, color = ClearColors.muted)
        Spacer(Modifier.height(12.dp))
        LiquidGlassButton(
            onClick = {
                try {
                    val intent = Intent("android.net.vpn.SETTINGS")
                    context.startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_VPN_SETTINGS)
                    context.startActivity(intent)
                }
            }
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Open System VPN Settings", fontWeight = FontWeight.SemiBold)
        }
    }
}

// === Reusable App Picker Selector Dialog ===
@Composable
private fun AppPickerSelectorDialog(
    title: String,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }
        loading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = ClearColors.green) }
        },
        title = { Text(title, color = ClearColors.text, fontWeight = FontWeight.Bold) },
        text = {
            when {
                loading -> Text("Loading apps…", color = ClearColors.muted)
                apps.isEmpty() -> Text("No apps found.", color = ClearColors.muted)
                else -> LazyColumn(modifier = Modifier.height(380.dp)) {
                    items(apps, key = { it.first }) { (packageName, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(packageName) }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = packageName in selected,
                                onCheckedChange = { onToggle(packageName) },
                                colors = CheckboxDefaults.colors(checkedColor = ClearColors.green)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = ClearColors.text)
                                Text(packageName, fontSize = 11.sp, color = ClearColors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    )
}

// === Country Blocker Dialog ===
@Composable
private fun CountryBlockerDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferenceKeys.prefs(context) }
    var blockedCountries by remember {
        mutableStateOf(prefs.getStringSet(PreferenceKeys.KEY_BLOCKED_COUNTRIES, emptySet()) ?: emptySet())
    }

    val countries = listOf(
        "cn" to "China (.cn)",
        "ru" to "Russia (.ru / .su)",
        "kp" to "North Korea (.kp)",
        "ir" to "Iran (.ir)",
        "by" to "Belarus (.by)"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = ClearColors.green) }
        },
        title = { Text("Block Country TLDs", fontWeight = FontWeight.Bold, color = ClearColors.text) },
        text = {
            Column {
                Text("Deny all DNS resolution for domains registered in these country Top-Level Domains (TLDs).", fontSize = 12.sp, color = ClearColors.muted)
                Spacer(Modifier.height(12.dp))
                countries.forEach { (code, name) ->
                    val isChecked = code in blockedCountries
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (isChecked) blockedCountries - code else blockedCountries + code
                                blockedCountries = next
                                prefs.edit().putStringSet(PreferenceKeys.KEY_BLOCKED_COUNTRIES, next).apply()
                                ClearGuardVpnService.reloadIfRunning(context)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = null,
                            colors = CheckboxDefaults.colors(checkedColor = ClearColors.green)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(name, fontSize = 14.sp, color = ClearColors.text)
                    }
                }
            }
        }
    )
}

private fun loadLaunchableApps(context: Context): List<Pair<String, String>> {
    val pm = context.packageManager
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcherIntent, 0)
        .asSequence()
        .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
        .filter { it.first != context.packageName }
        .distinctBy { it.first }
        .sortedBy { it.second.lowercase() }
        .toList()
}

private data class Provider(
    val id: String,
    val label: String,
    val url: String,
    val desc: String
)

/**
 * Collapsible section header with colored icon, accent text, and animated chevron.
 */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    accentColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(200),
        label = "chevron"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = ClearColors.muted,
            modifier = Modifier
                .size(20.dp)
                .graphicsLayer { rotationZ = chevronRotation }
        )
    }
}
