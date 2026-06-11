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

    public static final String KEY_ALLOWED_COUNT = "allowed_count";
    public static final String KEY_BLOCKED_COUNT = "blocked_count";
    public static final String KEY_BLOCKED_TODAY = "blocked_today";
    public static final String KEY_CACHE_HIT_COUNT = "cache_hit_count";
    public static final String KEY_LAST_BLOCK_DAY = "last_block_day";
    public static final String KEY_ALLOWLIST = "allowlist";
    public static final String KEY_CACHE_TTL_SECONDS = "cache_ttl_seconds";
    public static final String KEY_CUSTOM_BLOCKS = "custom_blocks";
    public static final String KEY_LAST_UPDATE_COUNT = "last_update_count";
    public static final String KEY_LAST_UPDATE_MILLIS = "last_update_millis";
    public static final String KEY_SCAM_BLOCKED_COUNT = "scam_blocked_count";
    public static final String KEY_SCAM_BLOCKED_TODAY = "scam_blocked_today";
    public static final String KEY_SCAM_SHIELD_ENABLED = "scam_shield_enabled";
    public static final String KEY_UPSTREAM_AVERAGE_LATENCY_MS = "upstream_average_latency_ms";
    public static final String KEY_UPSTREAM_QUERY_COUNT = "upstream_query_count";
    public static final String KEY_SOURCE_URLS = "source_urls";
    public static final String KEY_UPSTREAM_DNS = "upstream_dns";
    public static final String KEY_DOH_QUERY_COUNT = "doh_query_count";
    public static final String KEY_DOH_FALLBACK_COUNT = "doh_fallback_count";
    public static final String KEY_DOH_ENABLED = "doh_enabled";
    public static final String KEY_DOH_URL = "doh_url";
    public static final String KEY_DOH_PROVIDER = "doh_provider";

    public static final int DEFAULT_CACHE_TTL_SECONDS = 300;
    public static final boolean DEFAULT_SCAM_SHIELD_ENABLED = true;
    public static final String DEFAULT_UPSTREAM_DNS = "9.9.9.9";
    public static final boolean DEFAULT_DOH_ENABLED = true;
    public static final String DEFAULT_DOH_URL = "https://dns.quad9.net/dns-query";
    public static final String DEFAULT_DOH_PROVIDER = "quad9";

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

        if (changed) {
            editor.apply();
        }
    }

    public static Set<String> defaultSources() {
        return new LinkedHashSet<>(Arrays.asList(
                "https://adaway.org/hosts.txt",
                "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        ));
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
}
