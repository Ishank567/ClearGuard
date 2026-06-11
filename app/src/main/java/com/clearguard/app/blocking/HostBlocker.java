package com.clearguard.app.blocking;

import android.content.Context;
import android.content.SharedPreferences;

import com.clearguard.app.PreferenceKeys;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.IDN;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HostBlocker {
    private static volatile HostBlocker instance;

    private final Context context;
    private volatile Set<String> blockedHosts = Collections.emptySet();
    private volatile Set<String> allowedHosts = Collections.emptySet();

    private HostBlocker(Context context) {
        this.context = context.getApplicationContext();
    }

    public static HostBlocker get(Context context) {
        if (instance == null) {
            synchronized (HostBlocker.class) {
                if (instance == null) {
                    instance = new HostBlocker(context);
                }
            }
        }
        return instance;
    }

    public synchronized Snapshot reload() {
        PreferenceKeys.ensureDefaults(context);

        Set<String> nextBlocked = new HashSet<>();
        Set<String> nextAllowed = new HashSet<>();

        loadAssetHosts(nextBlocked);
        loadDownloadedHosts(nextBlocked);
        loadPreferenceHosts(PreferenceKeys.KEY_CUSTOM_BLOCKS, nextBlocked);
        loadPreferenceHosts(PreferenceKeys.KEY_ALLOWLIST, nextAllowed);

        nextBlocked.removeAll(nextAllowed);
        blockedHosts = Collections.unmodifiableSet(nextBlocked);
        allowedHosts = Collections.unmodifiableSet(nextAllowed);
        return snapshot();
    }

    public boolean shouldBlock(String domain) {
        String normalized = normalizeDomain(domain);
        if (normalized == null) {
            return false;
        }
        if (containsDomain(allowedHosts, normalized)) {
            return false;
        }
        return containsDomain(blockedHosts, normalized);
    }

    public boolean isAllowed(String domain) {
        String normalized = normalizeDomain(domain);
        if (normalized == null) {
            return false;
        }
        return containsDomain(allowedHosts, normalized);
    }

    public Snapshot snapshot() {
        return new Snapshot(blockedHosts.size(), allowedHosts.size());
    }

    public static List<String> hostsFromLine(String rawLine) {
        if (rawLine == null) {
            return Collections.emptyList();
        }

        String line = stripComment(rawLine).trim();
        if (line.isEmpty()) {
            return Collections.emptyList();
        }

        String dnsmasq = hostFromDnsmasqAddress(line);
        if (dnsmasq != null) {
            return Collections.singletonList(dnsmasq);
        }

        String adblock = hostFromAdblockRule(line);
        if (adblock != null) {
            return Collections.singletonList(adblock);
        }

        List<String> hosts = new ArrayList<>();
        String[] parts = line.split("\\s+");
        for (String part : parts) {
            String normalized = normalizeDomain(part);
            if (normalized != null) {
                hosts.add(normalized);
            }
        }
        return hosts;
    }

    public static String normalizeDomain(String input) {
        if (input == null) {
            return null;
        }

        String value = input.trim();
        if (value.isEmpty()) {
            return null;
        }

        value = value.replace("\uFEFF", "");
        value = value.replace("[", "").replace("]", "");

        if (value.contains("://")) {
            try {
                URI uri = URI.create(value);
                value = uri.getHost() == null ? value : uri.getHost();
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        if (value.startsWith("||")) {
            value = value.substring(2);
        }
        int ruleEnd = firstIndexOf(value, '^', '/', '$');
        if (ruleEnd >= 0) {
            value = value.substring(0, ruleEnd);
        }

        value = value.trim()
                .replace("*", "")
                .replace("|", "")
                .toLowerCase(Locale.US);

        while (value.startsWith(".")) {
            value = value.substring(1);
        }
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }

        int portIndex = value.lastIndexOf(':');
        if (portIndex > 0 && value.indexOf(':') == portIndex) {
            value = value.substring(0, portIndex);
        }

        if (value.equals("localhost") || looksLikeIpAddress(value) || !value.contains(".")) {
            return null;
        }

        try {
            value = IDN.toASCII(value, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.US);
        } catch (IllegalArgumentException ignored) {
            return null;
        }

        if (value.length() > 253) {
            return null;
        }

        String[] labels = value.split("\\.");
        for (String label : labels) {
            if (label.isEmpty() || label.length() > 63) {
                return null;
            }
            if (label.startsWith("-") || label.endsWith("-")) {
                return null;
            }
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                boolean valid = (c >= 'a' && c <= 'z')
                        || (c >= '0' && c <= '9')
                        || c == '-';
                if (!valid) {
                    return null;
                }
            }
        }
        return value;
    }

    private void loadAssetHosts(Set<String> target) {
        try (InputStream input = context.getAssets().open("blocklists/base-hosts.txt")) {
            loadHosts(input, target);
        } catch (IOException ignored) {
            // A missing built-in list should not prevent the VPN from starting.
        }
    }

    private void loadDownloadedHosts(Set<String> target) {
        File file = downloadedHostsFile(context);
        if (!file.exists()) {
            return;
        }
        try (InputStream input = new FileInputStream(file)) {
            loadHosts(input, target);
        } catch (IOException ignored) {
            // Keep the last successfully loaded in-memory set if a file read fails.
        }
    }

    private void loadPreferenceHosts(String key, Set<String> target) {
        SharedPreferences prefs = PreferenceKeys.prefs(context);
        Set<String> values = prefs.getStringSet(key, Collections.emptySet());
        for (String value : values) {
            String host = normalizeDomain(value);
            if (host != null) {
                target.add(host);
            }
        }
    }

    private static void loadHosts(InputStream input, Set<String> target) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            for (String host : hostsFromLine(line)) {
                target.add(host);
            }
        }
    }

    public static File downloadedHostsFile(Context context) {
        File dir = new File(context.getFilesDir(), "blocklists");
        return new File(dir, "hosts.txt");
    }

    private static boolean containsDomain(Set<String> set, String domain) {
        String cursor = domain;
        while (true) {
            if (set.contains(cursor)) {
                return true;
            }
            int dot = cursor.indexOf('.');
            if (dot < 0) {
                return false;
            }
            cursor = cursor.substring(dot + 1);
        }
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    private static String hostFromDnsmasqAddress(String line) {
        if (!line.startsWith("address=/")) {
            return null;
        }
        int start = "address=/".length();
        int end = line.indexOf('/', start);
        if (end <= start) {
            return null;
        }
        return normalizeDomain(line.substring(start, end));
    }

    private static String hostFromAdblockRule(String line) {
        if (!line.startsWith("||")) {
            return null;
        }
        return normalizeDomain(line);
    }

    private static int firstIndexOf(String value, char a, char b, char c) {
        int best = -1;
        for (char candidate : new char[]{a, b, c}) {
            int index = value.indexOf(candidate);
            if (index >= 0 && (best < 0 || index < best)) {
                best = index;
            }
        }
        return best;
    }

    private static boolean looksLikeIpAddress(String value) {
        boolean hasDot = value.indexOf('.') >= 0;
        boolean numericOrDot = true;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isDigit(c) && c != '.') {
                numericOrDot = false;
                break;
            }
        }
        return value.indexOf(':') >= 0 || (hasDot && numericOrDot);
    }

    public static final class Snapshot {
        public final int blockedHostCount;
        public final int allowedHostCount;

        Snapshot(int blockedHostCount, int allowedHostCount) {
            this.blockedHostCount = blockedHostCount;
            this.allowedHostCount = allowedHostCount;
        }
    }
}
