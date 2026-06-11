package com.clearguard.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PreferenceKeys {
    public static final String PREFS = "clear_guard";

    public static final String KEY_ALLOWED_COUNT = "allowed_count";
    public static final String KEY_BLOCKED_COUNT = "blocked_count";
    public static final String KEY_BLOCKED_TODAY = "blocked_today";
    public static final String KEY_LAST_BLOCK_DAY = "last_block_day";
    public static final String KEY_ALLOWLIST = "allowlist";
    public static final String KEY_CACHE_TTL_SECONDS = "cache_ttl_seconds";
    public static final String KEY_CUSTOM_BLOCKS = "custom_blocks";
    public static final String KEY_LAST_UPDATE_COUNT = "last_update_count";
    public static final String KEY_LAST_UPDATE_MILLIS = "last_update_millis";
    public static final String KEY_SOURCE_URLS = "source_urls";
    public static final String KEY_UPSTREAM_DNS = "upstream_dns";

    public static final int DEFAULT_CACHE_TTL_SECONDS = 300;
    public static final String DEFAULT_UPSTREAM_DNS = "9.9.9.9";

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
}
