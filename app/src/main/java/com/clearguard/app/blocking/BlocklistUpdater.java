package com.clearguard.app.blocking;

import android.content.Context;
import android.content.SharedPreferences;

import com.clearguard.app.PreferenceKeys;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BlocklistUpdater {
    private static final int CONNECT_TIMEOUT_MILLIS = 10000;
    private static final int READ_TIMEOUT_MILLIS = 20000;
    private static final int MAX_LINES_PER_SOURCE = 800000;

    private BlocklistUpdater() {
    }

    public static Result updateNow(Context context) {
        Context appContext = context.getApplicationContext();
        PreferenceKeys.ensureDefaults(appContext);
        SharedPreferences prefs = PreferenceKeys.prefs(appContext);

        Set<String> sources = prefs.getStringSet(
                PreferenceKeys.KEY_SOURCE_URLS,
                PreferenceKeys.defaultSources());
        if (sources == null) {
            sources = PreferenceKeys.defaultSources();
        }
        Set<String> disabledSources = prefs.getStringSet(
                PreferenceKeys.KEY_DISABLED_SOURCE_URLS,
                Collections.emptySet());
        if (disabledSources == null) {
            disabledSources = Collections.emptySet();
        }

        List<String> activeSources = new ArrayList<>();
        for (String source : sources) {
            if (!disabledSources.contains(source)) {
                activeSources.add(source);
            }
        }

        if (sources.isEmpty() || activeSources.isEmpty()) {
            try {
                writeHosts(appContext, Collections.emptySet());
                prefs.edit()
                        .putLong(PreferenceKeys.KEY_LAST_UPDATE_MILLIS, System.currentTimeMillis())
                        .putInt(PreferenceKeys.KEY_LAST_UPDATE_COUNT, 0)
                        .apply();
                HostBlocker.get(appContext).reload();
            } catch (IOException error) {
                return new Result(false, 0, activeSources.size(), 0,
                        "Could not clear disabled lists: " + error.getMessage());
            }
            String message = sources.isEmpty()
                    ? "No filter sources configured"
                    : "All filter sources are turned off";
            return new Result(true, 0, activeSources.size(), 0, message);
        }

        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        int successfulSources = 0;
        List<String> errors = new ArrayList<>();

        for (String source : activeSources) {
            try {
                int before = hosts.size();
                downloadSource(source, hosts);
                if (hosts.size() >= before) {
                    successfulSources++;
                }
            } catch (IOException error) {
                errors.add(shortName(source) + ": " + error.getMessage());
            }
        }

        if (successfulSources > 0) {
            try {
                writeHosts(appContext, hosts);
                prefs.edit()
                        .putLong(PreferenceKeys.KEY_LAST_UPDATE_MILLIS, System.currentTimeMillis())
                        .putInt(PreferenceKeys.KEY_LAST_UPDATE_COUNT, hosts.size())
                        .apply();
                HostBlocker.get(appContext).reload();
            } catch (IOException error) {
                return new Result(false, successfulSources, activeSources.size(), hosts.size(),
                        "Could not save list: " + error.getMessage());
            }
            return new Result(true, successfulSources, activeSources.size(), hosts.size(), "");
        }

        String message = errors.isEmpty() ? "No blocklist sources configured" : errors.get(0);
        return new Result(false, 0, activeSources.size(), 0, message);
    }

    private static void downloadSource(String source, Set<String> target) throws IOException {
        // Enforced here as well as in the UI so stale or imported preferences can
        // never downgrade a list download to cleartext.
        if (!source.regionMatches(true, 0, "https://", 0, 8)) {
            throw new IOException("only https:// sources are allowed");
        }
        URL url = new URL(source);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("User-Agent", "ShieldDNS/0.4 local-dns-filter");

        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            connection.disconnect();
            throw new IOException("HTTP " + code);
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                lineCount++;
                if (lineCount > MAX_LINES_PER_SOURCE) {
                    throw new IOException("source is too large");
                }
                for (String host : HostBlocker.hostsFromLine(line)) {
                    target.add(host);
                }
            }
        } finally {
            connection.disconnect();
        }
    }

    private static void writeHosts(Context context, Set<String> hosts) throws IOException {
        File destination = HostBlocker.downloadedHostsFile(context);
        File dir = destination.getParentFile();
        if (dir != null && !dir.exists() && !dir.mkdirs()) {
            throw new IOException("could not create blocklist directory");
        }

        List<String> sortedHosts = new ArrayList<>(hosts);
        Collections.sort(sortedHosts);

        File temp = new File(destination.getParentFile(), "hosts.txt.tmp");
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(new java.io.FileOutputStream(temp), StandardCharsets.UTF_8))) {
            for (String host : sortedHosts) {
                writer.write("0.0.0.0 ");
                writer.write(host);
                writer.newLine();
            }
        }

        if (destination.exists() && !destination.delete()) {
            throw new IOException("could not replace old blocklist");
        }
        if (!temp.renameTo(destination)) {
            throw new IOException("could not activate new blocklist");
        }
    }

    private static String shortName(String source) {
        String displayName = PreferenceKeys.sourceDisplayName(source);
        if (!"Custom filter list".equals(displayName)) {
            return displayName;
        }
        int slash = source.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < source.length()) {
            return source.substring(slash + 1);
        }
        return source;
    }

    public static final class Result {
        public final boolean success;
        public final int successfulSources;
        public final int totalSources;
        public final int hostCount;
        public final String message;

        Result(boolean success, int successfulSources, int totalSources, int hostCount, String message) {
            this.success = success;
            this.successfulSources = successfulSources;
            this.totalSources = totalSources;
            this.hostCount = hostCount;
            this.message = message;
        }
    }
}
