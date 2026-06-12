package com.clearguard.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PreferenceKeys {
    public static final String PREFS = "clear_guard";

    public static final String KEY_ONBOARDING_SEEN = "onboarding_seen";

    public static final String KEY_ALLOWED_COUNT = "allowed_count";
    public static final String KEY_BLOCKED_COUNT = "blocked_count";
    public static final String KEY_BLOCKED_TODAY = "blocked_today";
    public static final String KEY_CACHE_HIT_COUNT = "cache_hit_count";
    public static final String KEY_LAST_BLOCK_DAY = "last_block_day";
    public static final String KEY_ALLOWLIST = "allowlist";
    public static final String KEY_CACHE_TTL_SECONDS = "cache_ttl_seconds";
    public static final String KEY_CUSTOM_BLOCKS = "custom_blocks";
    public static final String KEY_SECURITY_BLOCKS = "security_blocks";
    public static final String KEY_LAST_UPDATE_COUNT = "last_update_count";
    public static final String KEY_LAST_UPDATE_MILLIS = "last_update_millis";
    public static final String KEY_SCAM_BLOCKED_COUNT = "scam_blocked_count";
    public static final String KEY_SCAM_BLOCKED_TODAY = "scam_blocked_today";
    public static final String KEY_SCAM_SHIELD_ENABLED = "scam_shield_enabled";
    public static final String KEY_UPSTREAM_AVERAGE_LATENCY_MS = "upstream_average_latency_ms";
    public static final String KEY_UPSTREAM_QUERY_COUNT = "upstream_query_count";
    public static final String KEY_SOURCE_URLS = "source_urls";
    public static final String KEY_DISABLED_SOURCE_URLS = "disabled_source_urls";
    public static final String KEY_UPSTREAM_DNS = "upstream_dns";
    public static final String KEY_DOH_QUERY_COUNT = "doh_query_count";
    public static final String KEY_DOH_FALLBACK_COUNT = "doh_fallback_count";
    public static final String KEY_DOH_ENABLED = "doh_enabled";
    public static final String KEY_DOH_URL = "doh_url";
    public static final String KEY_DOH_PROVIDER = "doh_provider";
    public static final String KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled";
    public static final String KEY_BYPASS_GUARD_ENABLED = "bypass_guard_enabled";
    public static final String KEY_RESUME_ON_BOOT = "resume_on_boot";
    /** Tracks whether the user last wanted protection on, so it can resume after a reboot. */
    public static final String KEY_PROTECTION_DESIRED = "protection_desired";
    /** Appearance: "system", "light", or "dark". */
    public static final String KEY_THEME_MODE = "theme_mode";
    /** Package names excluded from the VPN (their traffic bypasses filtering). */
    public static final String KEY_EXCLUDED_APPS = "excluded_apps";
    /** JSON object of domain -> block count, maintained by the VPN service. */
    public static final String KEY_TOP_BLOCKED_JSON = "top_blocked_json";
    /**
     * Tracks one-time migrations that add new bundled default sources without
     * re-adding sources the user removes later.
     */
    private static final String KEY_DEFAULT_SOURCES_VERSION = "default_sources_version";

    // ShieldDNS Custom Firewall and Privacy Keys
    public static final String KEY_FIREWALL_BLOCKED_APPS = "firewall_blocked_apps";
    public static final String KEY_FIREWALL_BLOCKED_WIFI = "firewall_blocked_wifi";
    public static final String KEY_FIREWALL_BLOCKED_MOBILE = "firewall_blocked_mobile";
    public static final String KEY_BLOCKED_COUNTRIES = "blocked_countries";
    public static final String KEY_TIME_RULES_ENABLED = "time_rules_enabled";
    public static final String KEY_BACKGROUND_BLOCK_ENABLED = "background_block_enabled";
    public static final String KEY_SECURITY_MODE = "security_mode";
    public static final String KEY_WIFI_PROTECTION_ENABLED = "wifi_protection_enabled";
    public static final String KEY_REGIONAL_PACK_INDIA = "regional_pack_india";
    public static final String KEY_DATA_SAVER_ENABLED = "data_saver_enabled";
    public static final String KEY_BROWSER_COOKIE_REMOVER = "browser_cookie_remover";
    public static final String KEY_BROWSER_ANTI_FINGERPRINT = "browser_anti_fingerprint";
    public static final String KEY_BROWSER_CLEANER_RULES = "browser_cleaner_rules";
    public static final String KEY_BROWSER_DARK_PATTERN_BLOCKER = "browser_dark_pattern_blocker";
    public static final String KEY_BROWSER_FAKE_PHONE_WARNER = "browser_fake_phone_warner";

    // Anti-adblock bypass tools (advanced, for users who encounter detection)
    public static final String KEY_BROWSER_SCRIPTLET_INJECTION = "browser_scriptlet_injection";
    public static final String KEY_BROWSER_ANTI_ADBLOCK_DEFUSER = "browser_anti_adblock_defuser";
    public static final String KEY_BROWSER_POPUP_TRAP_BLOCKER = "browser_popup_trap_blocker";
    public static final String KEY_BROWSER_REDIRECT_CHAIN_CLEANER = "browser_redirect_chain_cleaner";
    public static final String KEY_BROWSER_ANTI_PAYWALL_WARNING = "browser_anti_paywall_warning";
    public static final String KEY_BROWSER_SPONSORED_WIDGET_REMOVER = "browser_sponsored_widget_remover";
    public static final String KEY_BROWSER_FAKE_COUNTDOWN_REMOVER = "browser_fake_countdown_remover";
    public static final String KEY_BROWSER_OVERLAY_REMOVER = "browser_overlay_remover";

    /** Intent-based protection mode (Study / Work / Kids / Elder / Shopping / Spiritual / BatterySaver / Default). */
    public static final String KEY_PROTECTION_MODE = "protection_mode";
    public static final String DEFAULT_PROTECTION_MODE = "default";

    /** Dedicated Indian Scam Shield - targeted protection for the 9 common India-specific scams. */
    public static final String KEY_INDIAN_SCAM_SHIELD_ENABLED = "indian_scam_shield_enabled";
    public static final boolean DEFAULT_INDIAN_SCAM_SHIELD_ENABLED = true;

    /** On-device rule engine + TFLite phishing classifier (advanced, opt-in for maximum local analysis). */
    public static final String KEY_ON_DEVICE_RULE_ENGINE_ENABLED = "on_device_rule_engine_enabled";
    public static final String KEY_PHISHING_TFLITE_ENABLED = "phishing_tflite_enabled";
    public static final boolean DEFAULT_ON_DEVICE_RULE_ENGINE_ENABLED = true;
    public static final boolean DEFAULT_PHISHING_TFLITE_ENABLED = false;  // off by default until model is provided in assets

    // New advanced features from roadmap
    public static final String KEY_MOBILE_RISK_SCORING_ENABLED = "mobile_risk_scoring_enabled";
    public static final String KEY_UPI_PAYEE_VERIFICATION_ENABLED = "upi_payee_verification_enabled";
    public static final String KEY_RASP_ENABLED = "rasp_enabled";
    public static final boolean DEFAULT_MOBILE_RISK_SCORING_ENABLED = true;
    public static final boolean DEFAULT_UPI_PAYEE_VERIFICATION_ENABLED = true;
    public static final boolean DEFAULT_RASP_ENABLED = false; // high complexity, opt-in

    /** Enable remote FRI/operator signals for the Mobile Risk Scoring API (enterprise). */
    public static final String KEY_MOBILE_RISK_REMOTE_SIGNALS = "mobile_risk_remote_signals";
    public static final boolean DEFAULT_MOBILE_RISK_REMOTE_SIGNALS = false;

    /** Configurable endpoint for remote FRI/operator signals (default is stub). */
    public static final String KEY_FRI_REMOTE_ENDPOINT = "fri_remote_endpoint";
    public static final String DEFAULT_FRI_REMOTE_ENDPOINT = "https://api.example-fri.gov.in/v1/risk";

    public static final int DEFAULT_CACHE_TTL_SECONDS = 300;
    public static final boolean DEFAULT_SCAM_SHIELD_ENABLED = true;
    public static final String DEFAULT_UPSTREAM_DNS = "9.9.9.9";
    public static final boolean DEFAULT_DOH_ENABLED = true;
    public static final String DEFAULT_DOH_URL = "https://dns.quad9.net/dns-query";
    public static final String DEFAULT_DOH_PROVIDER = "quad9";
    public static final boolean DEFAULT_AUTO_UPDATE_ENABLED = true;
    public static final boolean DEFAULT_BYPASS_GUARD_ENABLED = true;
    public static final boolean DEFAULT_RESUME_ON_BOOT = true;
    public static final String DEFAULT_THEME_MODE = "system";
    private static final int DEFAULT_SOURCES_VERSION = 3;

    // ShieldDNS Defaults
    public static final String DEFAULT_SECURITY_MODE = "strict";
    public static final boolean DEFAULT_TIME_RULES_ENABLED = false;
    public static final boolean DEFAULT_BACKGROUND_BLOCK_ENABLED = false;
    public static final boolean DEFAULT_WIFI_PROTECTION_ENABLED = true;
    public static final boolean DEFAULT_REGIONAL_PACK_INDIA = false;
    public static final boolean DEFAULT_DATA_SAVER_ENABLED = false;
    public static final boolean DEFAULT_BROWSER_COOKIE_REMOVER = true;
    public static final boolean DEFAULT_BROWSER_ANTI_FINGERPRINT = true;
    public static final boolean DEFAULT_BROWSER_DARK_PATTERN_BLOCKER = true;
    public static final boolean DEFAULT_BROWSER_FAKE_PHONE_WARNER = true;

    // Advanced anti-adblock tools default to off (user must opt-in)
    public static final boolean DEFAULT_BROWSER_SCRIPTLET_INJECTION = false;
    public static final boolean DEFAULT_BROWSER_ANTI_ADBLOCK_DEFUSER = false;
    public static final boolean DEFAULT_BROWSER_POPUP_TRAP_BLOCKER = false;
    public static final boolean DEFAULT_BROWSER_REDIRECT_CHAIN_CLEANER = false;
    public static final boolean DEFAULT_BROWSER_ANTI_PAYWALL_WARNING = false;
    public static final boolean DEFAULT_BROWSER_SPONSORED_WIDGET_REMOVER = false;
    public static final boolean DEFAULT_BROWSER_FAKE_COUNTDOWN_REMOVER = false;
    public static final boolean DEFAULT_BROWSER_OVERLAY_REMOVER = false;

    private PreferenceKeys() {
    }

    public static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void ensureDefaults(Context context) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;

        if (!prefs.contains(KEY_SOURCE_URLS)) {
            editor.putStringSet(KEY_SOURCE_URLS, defaultSources());
            editor.putInt(KEY_DEFAULT_SOURCES_VERSION, DEFAULT_SOURCES_VERSION);
            changed = true;
        } else if (prefs.getInt(KEY_DEFAULT_SOURCES_VERSION, 0) < DEFAULT_SOURCES_VERSION) {
            int oldVersion = prefs.getInt(KEY_DEFAULT_SOURCES_VERSION, 0);
            Set<String> storedSources = prefs.getStringSet(KEY_SOURCE_URLS, Collections.emptySet());
            Set<String> previousSources = storedSources == null ? Collections.emptySet() : storedSources;
            Set<String> merged = new LinkedHashSet<>(previousSources);
            for (int version = oldVersion + 1; version <= DEFAULT_SOURCES_VERSION; version++) {
                merged.addAll(defaultSourcesAddedInVersion(version));
            }
            if (!merged.equals(previousSources)) {
                editor.putStringSet(KEY_SOURCE_URLS, merged);
            }
            editor.putInt(KEY_DEFAULT_SOURCES_VERSION, DEFAULT_SOURCES_VERSION);
            changed = true;
        }
        if (!prefs.contains(KEY_UPSTREAM_DNS)) {
            editor.putString(KEY_UPSTREAM_DNS, DEFAULT_UPSTREAM_DNS);
            changed = true;
        }
        if (!prefs.contains(KEY_CACHE_TTL_SECONDS)) {
            editor.putInt(KEY_CACHE_TTL_SECONDS, DEFAULT_CACHE_TTL_SECONDS);
            changed = true;
        }
        if (!prefs.contains(KEY_SCAM_SHIELD_ENABLED)) {
            editor.putBoolean(KEY_SCAM_SHIELD_ENABLED, DEFAULT_SCAM_SHIELD_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_INDIAN_SCAM_SHIELD_ENABLED)) {
            editor.putBoolean(KEY_INDIAN_SCAM_SHIELD_ENABLED, DEFAULT_INDIAN_SCAM_SHIELD_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_ON_DEVICE_RULE_ENGINE_ENABLED)) {
            editor.putBoolean(KEY_ON_DEVICE_RULE_ENGINE_ENABLED, DEFAULT_ON_DEVICE_RULE_ENGINE_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_PHISHING_TFLITE_ENABLED)) {
            editor.putBoolean(KEY_PHISHING_TFLITE_ENABLED, DEFAULT_PHISHING_TFLITE_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_MOBILE_RISK_SCORING_ENABLED)) {
            editor.putBoolean(KEY_MOBILE_RISK_SCORING_ENABLED, DEFAULT_MOBILE_RISK_SCORING_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_MOBILE_RISK_REMOTE_SIGNALS)) {
            editor.putBoolean(KEY_MOBILE_RISK_REMOTE_SIGNALS, DEFAULT_MOBILE_RISK_REMOTE_SIGNALS);
            changed = true;
        }
        if (!prefs.contains(KEY_FRI_REMOTE_ENDPOINT)) {
            editor.putString(KEY_FRI_REMOTE_ENDPOINT, DEFAULT_FRI_REMOTE_ENDPOINT);
            changed = true;
        }
        if (!prefs.contains(KEY_UPI_PAYEE_VERIFICATION_ENABLED)) {
            editor.putBoolean(KEY_UPI_PAYEE_VERIFICATION_ENABLED, DEFAULT_UPI_PAYEE_VERIFICATION_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_RASP_ENABLED)) {
            editor.putBoolean(KEY_RASP_ENABLED, DEFAULT_RASP_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_DOH_ENABLED)) {
            editor.putBoolean(KEY_DOH_ENABLED, DEFAULT_DOH_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_DOH_URL)) {
            editor.putString(KEY_DOH_URL, DEFAULT_DOH_URL);
            changed = true;
        }
        if (!prefs.contains(KEY_DOH_PROVIDER)) {
            editor.putString(KEY_DOH_PROVIDER, DEFAULT_DOH_PROVIDER);
            changed = true;
        }
        if (!prefs.contains(KEY_AUTO_UPDATE_ENABLED)) {
            editor.putBoolean(KEY_AUTO_UPDATE_ENABLED, DEFAULT_AUTO_UPDATE_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_BYPASS_GUARD_ENABLED)) {
            editor.putBoolean(KEY_BYPASS_GUARD_ENABLED, DEFAULT_BYPASS_GUARD_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_RESUME_ON_BOOT)) {
            editor.putBoolean(KEY_RESUME_ON_BOOT, DEFAULT_RESUME_ON_BOOT);
            changed = true;
        }
        if (!prefs.contains(KEY_SECURITY_MODE)) {
            editor.putString(KEY_SECURITY_MODE, DEFAULT_SECURITY_MODE);
            changed = true;
        }
        if (!prefs.contains(KEY_TIME_RULES_ENABLED)) {
            editor.putBoolean(KEY_TIME_RULES_ENABLED, DEFAULT_TIME_RULES_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_BACKGROUND_BLOCK_ENABLED)) {
            editor.putBoolean(KEY_BACKGROUND_BLOCK_ENABLED, DEFAULT_BACKGROUND_BLOCK_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_WIFI_PROTECTION_ENABLED)) {
            editor.putBoolean(KEY_WIFI_PROTECTION_ENABLED, DEFAULT_WIFI_PROTECTION_ENABLED);
            changed = true;
        }
        if (!prefs.contains(KEY_REGIONAL_PACK_INDIA)) {
            editor.putBoolean(KEY_REGIONAL_PACK_INDIA, DEFAULT_REGIONAL_PACK_INDIA);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_COOKIE_REMOVER)) {
            editor.putBoolean(KEY_BROWSER_COOKIE_REMOVER, DEFAULT_BROWSER_COOKIE_REMOVER);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_ANTI_FINGERPRINT)) {
            editor.putBoolean(KEY_BROWSER_ANTI_FINGERPRINT, DEFAULT_BROWSER_ANTI_FINGERPRINT);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_DARK_PATTERN_BLOCKER)) {
            editor.putBoolean(KEY_BROWSER_DARK_PATTERN_BLOCKER, DEFAULT_BROWSER_DARK_PATTERN_BLOCKER);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_FAKE_PHONE_WARNER)) {
            editor.putBoolean(KEY_BROWSER_FAKE_PHONE_WARNER, DEFAULT_BROWSER_FAKE_PHONE_WARNER);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_SCRIPTLET_INJECTION)) {
            editor.putBoolean(KEY_BROWSER_SCRIPTLET_INJECTION, DEFAULT_BROWSER_SCRIPTLET_INJECTION);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_ANTI_ADBLOCK_DEFUSER)) {
            editor.putBoolean(KEY_BROWSER_ANTI_ADBLOCK_DEFUSER, DEFAULT_BROWSER_ANTI_ADBLOCK_DEFUSER);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_POPUP_TRAP_BLOCKER)) {
            editor.putBoolean(KEY_BROWSER_POPUP_TRAP_BLOCKER, DEFAULT_BROWSER_POPUP_TRAP_BLOCKER);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_REDIRECT_CHAIN_CLEANER)) {
            editor.putBoolean(KEY_BROWSER_REDIRECT_CHAIN_CLEANER, DEFAULT_BROWSER_REDIRECT_CHAIN_CLEANER);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_ANTI_PAYWALL_WARNING)) {
            editor.putBoolean(KEY_BROWSER_ANTI_PAYWALL_WARNING, DEFAULT_BROWSER_ANTI_PAYWALL_WARNING);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_SPONSORED_WIDGET_REMOVER)) {
            editor.putBoolean(KEY_BROWSER_SPONSORED_WIDGET_REMOVER, DEFAULT_BROWSER_SPONSORED_WIDGET_REMOVER);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_FAKE_COUNTDOWN_REMOVER)) {
            editor.putBoolean(KEY_BROWSER_FAKE_COUNTDOWN_REMOVER, DEFAULT_BROWSER_FAKE_COUNTDOWN_REMOVER);
            changed = true;
        }
        if (!prefs.contains(KEY_BROWSER_OVERLAY_REMOVER)) {
            editor.putBoolean(KEY_BROWSER_OVERLAY_REMOVER, DEFAULT_BROWSER_OVERLAY_REMOVER);
            changed = true;
        }
        if (!prefs.contains(KEY_PROTECTION_MODE)) {
            editor.putString(KEY_PROTECTION_MODE, DEFAULT_PROTECTION_MODE);
            changed = true;
        }

        if (changed) {
            editor.apply();
        }
    }

    public static Set<String> defaultSources() {
        LinkedHashSet<String> sources = new LinkedHashSet<>();
        for (int version = 1; version <= DEFAULT_SOURCES_VERSION; version++) {
            sources.addAll(defaultSourcesAddedInVersion(version));
        }
        return sources;
    }

    private static Set<String> defaultSourcesAddedInVersion(int version) {
        if (version == 1) {
            return new LinkedHashSet<>(Arrays.asList(
                    "https://adaway.org/hosts.txt",
                    "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
            ));
        }
        if (version == 2) {
            return new LinkedHashSet<>(Arrays.asList(
                    "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt",
                    "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
                    "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
                    "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
                    "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/brave-specific.txt",
                    "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/brave-android-specific.txt"
            ));
        }
        if (version == 3) {
            return new LinkedHashSet<>(Arrays.asList(
                    "https://big.oisd.nl/",
                    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/multi.txt",
                    "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/tif.txt",
                    "https://adguardteam.github.io/HostlistsRegistry/assets/filter_11.txt",
                    "https://adguardteam.github.io/HostlistsRegistry/assets/filter_18.txt",
                    "https://adguardteam.github.io/HostlistsRegistry/assets/filter_8.txt",
                    "https://adguardteam.github.io/HostlistsRegistry/assets/filter_12.txt",
                    "https://adguardteam.github.io/HostlistsRegistry/assets/filter_30.txt",
                    "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"
            ));
        }
        return Collections.emptySet();
    }

    public static String sourceDisplayName(String source) {
        if (source == null) {
            return "Custom filter list";
        }
        switch (source) {
            case "https://adaway.org/hosts.txt":
                return "AdAway Default Blocklist";
            case "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts":
                return "StevenBlack Unified Hosts";
            case "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt":
                return "AdGuard DNS Filter";
            case "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt":
                return "uBlock Origin Filters";
            case "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt":
                return "uBlock Origin Privacy";
            case "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt":
                return "uBlock Origin Badware";
            case "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/brave-specific.txt":
                return "Brave Specific Filters";
            case "https://raw.githubusercontent.com/brave/adblock-lists/master/brave-lists/brave-android-specific.txt":
                return "Brave Android Filters";
            case "https://big.oisd.nl/":
                return "OISD Big";
            case "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/multi.txt":
                return "HaGeZi Multi";
            case "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/tif.txt":
                return "HaGeZi Threat Intelligence";
            case "https://adguardteam.github.io/HostlistsRegistry/assets/filter_11.txt":
                return "URLhaus Malicious URLs";
            case "https://adguardteam.github.io/HostlistsRegistry/assets/filter_18.txt":
                return "Phishing Army";
            case "https://adguardteam.github.io/HostlistsRegistry/assets/filter_8.txt":
                return "NoCoin Filter List";
            case "https://adguardteam.github.io/HostlistsRegistry/assets/filter_12.txt":
                return "Dandelion Sprout Anti-Malware";
            case "https://adguardteam.github.io/HostlistsRegistry/assets/filter_30.txt":
                return "PhishTank and OpenPhish";
            case "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext":
                return "Peter Lowe Ad and Tracking Servers";
            default:
                return "Custom filter list";
        }
    }

    /**
     * Returns a sorted copy of a string-set preference. Never returns the live set held by
     * SharedPreferences, so callers may use it freely.
     */
    public static List<String> stringSetSorted(Context context, String key) {
        Set<String> stored = prefs(context).getStringSet(key, Collections.emptySet());
        List<String> values = new ArrayList<>(stored == null ? Collections.emptySet() : stored);
        Collections.sort(values);
        return values;
    }

    /**
     * Adds a value to a string-set preference. Copies the stored set first because the set
     * returned by SharedPreferences must not be mutated in place.
     *
     * @return true if the value was newly added, false if it was already present.
     */
    public static boolean addToStringSet(Context context, String key, String value) {
        SharedPreferences prefs = prefs(context);
        Set<String> current = new LinkedHashSet<>(prefs.getStringSet(key, new LinkedHashSet<>()));
        boolean added = current.add(value);
        if (added) {
            prefs.edit().putStringSet(key, current).apply();
        }
        return added;
    }

    /** Removes a value from a string-set preference, copying the stored set before editing. */
    public static void removeFromStringSet(Context context, String key, String value) {
        SharedPreferences prefs = prefs(context);
        Set<String> current = new LinkedHashSet<>(prefs.getStringSet(key, new LinkedHashSet<>()));
        if (current.remove(value)) {
            prefs.edit().putStringSet(key, current).apply();
        }
    }

    // ===== Intent-Based Protection Modes (user-facing names from vision) =====
    public static final String[] PROTECTION_MODES = {
        "default", "study", "work", "kids", "elder", "shopping", "spiritual", "battery"
    };

    public static String modeDisplayName(String mode) {
        if (mode == null) return "Default";
        switch (mode) {
            case "study": return "Study Mode";
            case "work": return "Work Mode";
            case "kids": return "Kids Mode";
            case "elder": return "Elder Mode (Safe Search)";
            case "shopping": return "Shopping Mode";
            case "spiritual": return "Spiritual / Satvik Mode";
            case "battery": return "Battery Saver Mode";
            default: return "Default (Balanced)";
        }
    }

    public static String modeDescription(String mode) {
        if (mode == null) return "Balanced ad & tracker blocking";
        switch (mode) {
            case "study": return "Blocks YouTube recs, reels, distracting popups for focus";
            case "work": return "Hides social widgets, notification trackers, chat popups";
            case "kids": return "Strong blocks for adult, gambling, violent, scam content";
            case "elder": return "Protects against fake banks, loans, KYC, medicine, APK scams + full Indian Scam Shield (9 categories)";
            case "shopping": return "Kills fake discounts, aggressive trackers, coupon scams";
            case "spiritual": return "Satvik/Dharma Clean — removes vulgar, gambling, political, dating ads on scripture & bhakti sites";
            case "battery": return "Aggressive media ad + background tracker blocking to save power/data";
            default: return "Standard protection with scam shield and regional filters";
        }
    }

    public static String getCurrentMode(Context context) {
        return prefs(context).getString(KEY_PROTECTION_MODE, DEFAULT_PROTECTION_MODE);
    }

    public static boolean isIndianScamShieldEnabled(Context context) {
        return prefs(context).getBoolean(
            KEY_INDIAN_SCAM_SHIELD_ENABLED,
            DEFAULT_INDIAN_SCAM_SHIELD_ENABLED
        );
    }
}
