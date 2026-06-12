package com.clearguard.app.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import com.clearguard.app.MainActivity;
import com.clearguard.app.PreferenceKeys;
import com.clearguard.app.blocking.HostBlocker;
import com.clearguard.app.security.DnsBypassGuard;
import com.clearguard.app.security.ScamDetector;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClearGuardVpnService extends VpnService {
    public static final String ACTION_START = "com.clearguard.app.START";
    public static final String ACTION_STOP = "com.clearguard.app.STOP";
    public static final String ACTION_RELOAD = "com.clearguard.app.RELOAD";
    public static final String ACTION_RESTART = "com.clearguard.app.RESTART";
    public static final String ACTION_STATS_CHANGED = "com.clearguard.app.STATS_CHANGED";

    private static final String CHANNEL_ID = "clear_guard_vpn";
    private static final int NOTIFICATION_ID = 31;
    private static final String VPN_ADDRESS = "10.64.0.2";
    private static final String VIRTUAL_DNS = "10.64.0.1";

    private static final int RECENT_QUERIES_MAX = 100;
    private static final java.util.ArrayDeque<BlockedQuery> RECENT_QUERIES = new java.util.ArrayDeque<>();

    private static volatile boolean runningVisibleToUi;
    public static volatile boolean isRunning;

    // Each DNS query is handled on this pool so one slow upstream lookup never blocks
    // the rest of the device's DNS traffic. CallerRunsPolicy gives natural backpressure.
    private static final int MAX_QUERY_THREADS = 8;

    /** How many per-domain block counters are persisted for the statistics screen. */
    private static final int TOP_DOMAINS_LIMIT = 50;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final DnsResponseCache responseCache = new DnsResponseCache();
    private final Object statsLock = new Object();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> blockedDomainCounts =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Bumped on every successful start so a packet loop from a previous VPN session
     * (e.g. before an in-place restart) cannot tear down the current one.
     */
    private volatile int vpnGeneration;

    private ParcelFileDescriptor vpnInterface;
    private HostBlocker blocker;
    private SharedPreferences prefs;
    private Thread workerThread;
    private ExecutorService queryExecutor;
    private DohResolver dohResolver;

    private long allowedTotal;
    private long blockedTotal;
    private long blockedToday;
    private long cacheHitTotal;
    private long scamBlockedTotal;
    private long scamBlockedToday;
    private long upstreamQueryTotal;
    private long dohQueryTotal;
    private long dohFallbackTotal;
    private long pendingAllowed;
    private long pendingBlocked;
    private long pendingCacheHits;
    private long pendingScamBlocked;
    private long pendingUpstreamQueries;
    private long pendingDohQueries;
    private long pendingDohFallbacks;
    private float upstreamAverageLatencyMs;
    private long lastStatsFlushMillis;
    private String lastBlockDay;

    private boolean dohEnabled;
    private String dohUrl;
    private boolean bypassGuardEnabled;
    private String bypassExemptHost;

    public static boolean isRunning() {
        return runningVisibleToUi;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, ClearGuardVpnService.class).setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, ClearGuardVpnService.class).setAction(ACTION_STOP);
        context.startService(intent);
    }

    /**
     * Asks a running service to rebuild its host lists and drop its DNS cache so list edits
     * take effect immediately. No-op when protection is off.
     */
    public static void reloadIfRunning(Context context) {
        if (!runningVisibleToUi) {
            return;
        }
        Intent intent = new Intent(context, ClearGuardVpnService.class).setAction(ACTION_RELOAD);
        context.startService(intent);
    }

    /**
     * Rebuilds the VPN interface in place so changes that require a new Builder
     * (such as per-app exclusions) take effect. No-op when protection is off.
     */
    public static void restartIfRunning(Context context) {
        if (!runningVisibleToUi) {
            return;
        }
        Intent intent = new Intent(context, ClearGuardVpnService.class).setAction(ACTION_RESTART);
        context.startService(intent);
    }

    /** A point-in-time snapshot of recent queries (most recent first). */
    public static java.util.List<BlockedQuery> recentBlocked() {
        synchronized (RECENT_QUERIES) {
            return new java.util.ArrayList<>(RECENT_QUERIES);
        }
    }

    public static void clearRecentBlocked() {
        synchronized (RECENT_QUERIES) {
            RECENT_QUERIES.clear();
        }
    }

    private static void addRecentQuery(String domain, String reason, int threatScore, boolean blocked, String status, int latencyMs) {
        synchronized (RECENT_QUERIES) {
            RECENT_QUERIES.addFirst(new BlockedQuery(
                    domain,
                    System.currentTimeMillis(),
                    reason,
                    threatScore,
                    blocked,
                    status,
                    latencyMs));
            while (RECENT_QUERIES.size() > RECENT_QUERIES_MAX) {
                RECENT_QUERIES.removeLast();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        PreferenceKeys.ensureDefaults(this);
        prefs = PreferenceKeys.prefs(this);
        blocker = HostBlocker.get(this);
        blocker.reload();
        allowedTotal = prefs.getLong(PreferenceKeys.KEY_ALLOWED_COUNT, 0L);
        blockedTotal = prefs.getLong(PreferenceKeys.KEY_BLOCKED_COUNT, 0L);
        blockedToday = prefs.getLong(PreferenceKeys.KEY_BLOCKED_TODAY, 0L);
        cacheHitTotal = prefs.getLong(PreferenceKeys.KEY_CACHE_HIT_COUNT, 0L);
        scamBlockedTotal = prefs.getLong(PreferenceKeys.KEY_SCAM_BLOCKED_COUNT, 0L);
        scamBlockedToday = prefs.getLong(PreferenceKeys.KEY_SCAM_BLOCKED_TODAY, 0L);
        upstreamQueryTotal = prefs.getLong(PreferenceKeys.KEY_UPSTREAM_QUERY_COUNT, 0L);
        dohQueryTotal = prefs.getLong(PreferenceKeys.KEY_DOH_QUERY_COUNT, 0L);
        dohFallbackTotal = prefs.getLong(PreferenceKeys.KEY_DOH_FALLBACK_COUNT, 0L);
        upstreamAverageLatencyMs = prefs.getFloat(PreferenceKeys.KEY_UPSTREAM_AVERAGE_LATENCY_MS, 0f);
        lastBlockDay = prefs.getString(PreferenceKeys.KEY_LAST_BLOCK_DAY, "");
        lastStatsFlushMillis = System.currentTimeMillis();
        resetTodayIfNewDay();
        loadTopBlocked();

        dohResolver = new DohResolver();
        loadDohConfig();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            prefs.edit().putBoolean(PreferenceKeys.KEY_PROTECTION_DESIRED, false).apply();
            stopVpn();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_RELOAD.equals(action)) {
            blocker.reload();
            responseCache.clear();
            loadDohConfig();
            if (!running.get()) {
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            return START_STICKY;
        }
        if (ACTION_RESTART.equals(action)) {
            if (!running.get()) {
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            stopVpn();
            startVpn();
            return START_STICKY;
        }

        startVpn();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        if (dohResolver != null) {
            dohResolver.shutdown();
        }
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        // The user (or another VPN) revoked consent; do not auto-resume on the next boot.
        prefs.edit().putBoolean(PreferenceKeys.KEY_PROTECTION_DESIRED, false).apply();
        stopVpn();
        super.onRevoke();
    }

    private void startVpn() {
        if (running.get()) {
            return;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting DNS shield"));
        try {
            Builder builder = new Builder()
                    .setSession("ClearGuard")
                    .setMtu(1500)
                    .addAddress(VPN_ADDRESS, 32)
                    .addDnsServer(VIRTUAL_DNS)
                    .addRoute(VIRTUAL_DNS, 32)
                    .setBlocking(true)
                    .setConfigureIntent(buildConfigureIntent());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false);
            }

            // Per-app exclusions: traffic from these apps never enters the tunnel.
            java.util.Set<String> excludedApps = prefs.getStringSet(
                    PreferenceKeys.KEY_EXCLUDED_APPS, java.util.Collections.emptySet());
            if (excludedApps != null) {
                for (String packageName : excludedApps) {
                    try {
                        builder.addDisallowedApplication(packageName);
                    } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
                        // The app was uninstalled; skip it.
                    }
                }
            }

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                throw new IOException("VPN permission was not granted");
            }

            running.set(true);
            runningVisibleToUi = true;
            isRunning = true;
            queryExecutor = new ThreadPoolExecutor(
                    0,
                    MAX_QUERY_THREADS,
                    30L,
                    TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    new ThreadPoolExecutor.CallerRunsPolicy());
            vpnGeneration++;
            final int generation = vpnGeneration;
            final ParcelFileDescriptor iface = vpnInterface;
            workerThread = new Thread(() -> runPacketLoop(iface, generation), "ClearGuardDnsVpn");
            workerThread.start();
            prefs.edit().putBoolean(PreferenceKeys.KEY_PROTECTION_DESIRED, true).apply();
            updateNotification("DNS shield active");
        } catch (IOException | RuntimeException error) {
            running.set(false);
            runningVisibleToUi = false;
            isRunning = false;
            closeVpnInterface();
            stopForegroundCompat();
            stopSelf();
        }
    }

    private void stopVpn() {
        running.set(false);
        runningVisibleToUi = false;
        isRunning = false;
        if (queryExecutor != null) {
            queryExecutor.shutdownNow();
            queryExecutor = null;
        }
        closeVpnInterface();
        flushStats(true);
        clearRecentBlocked();
        stopForegroundCompat();
    }

    private void runPacketLoop(ParcelFileDescriptor iface, int generation) {
        byte[] packet = new byte[32767];
        try (
                FileInputStream input = new FileInputStream(iface.getFileDescriptor());
                FileOutputStream output = new FileOutputStream(iface.getFileDescriptor())
        ) {
            while (running.get() && generation == vpnGeneration) {
                int length = input.read(packet);
                if (length > 0) {
                    byte[] copy = Arrays.copyOf(packet, length);
                    ExecutorService executor = queryExecutor;
                    if (executor == null) {
                        break;
                    }
                    executor.execute(() -> {
                        try {
                            handlePacket(copy, copy.length, output);
                        } catch (IOException | RuntimeException ignored) {
                            // A failed query must not take down the other in-flight queries.
                        }
                    });
                }
            }
        } catch (IOException ignored) {
            // Closing the VPN descriptor during stop also lands here.
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
            // The executor shut down while we were dispatching; the loop is ending anyway.
        } finally {
            // Only tear the service down if this loop still belongs to the live session;
            // after an in-place restart the old loop must exit without side effects.
            if (generation == vpnGeneration) {
                stopVpn();
            }
        }
    }

    private void handlePacket(byte[] packet, int length, FileOutputStream output) throws IOException {
        DnsPacket.Request request = DnsPacket.parse(packet, length);
        if (request == null) {
            return;
        }

        DnsMessage.Question question;
        try {
            question = DnsMessage.parseQuestion(request.dnsPayload, request.dnsPayload.length);
        } catch (IOException ignored) {
            return;
        }

        byte[] dnsResponse;
        ScamDetector.Result scamThreat = scamThreat(question.name);
        if (blocker.shouldBlock(question.name)) {
            dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
            if (blocker.isSecurityBlock(question.name)) {
                recordSecurityBlocked(question.name);
            } else {
                recordBlocked(question.name);
            }
        } else if (bypassGuardEnabled
                && !blocker.isAllowed(question.name)
                && DnsBypassGuard.isBypassDomain(question.name, bypassExemptHost)) {
            // SERVFAIL makes apps fall back to the (filtered) system resolver instead
            // of tunneling their lookups through their own encrypted DNS.
            dnsResponse = DnsMessage.servfailResponse(request.dnsPayload, question);
            recordBypassBlocked(question.name);
        } else if (scamThreat != null && scamThreat.blocked) {
            dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
            recordScamBlocked(question.name, scamThreat);
        } else {
            dnsResponse = responseCache.get(question.cacheKey(), question.id);
            if (dnsResponse == null) {
                try {
                    long startMillis = SystemClock.elapsedRealtime();
                    if (dohEnabled) {
                        try {
                            dnsResponse = forwardViaDoh(request.dnsPayload);
                            recordDohQuery();
                        } catch (IOException | RuntimeException dohError) {
                            // Graceful fallback keeps protection working on restrictive networks
                            dnsResponse = forwardToUpstream(request.dnsPayload);
                            recordDohFallback();
                            recordUpstreamLookup(SystemClock.elapsedRealtime() - startMillis);
                        }
                    } else {
                        dnsResponse = forwardToUpstream(request.dnsPayload);
                        recordUpstreamLookup(SystemClock.elapsedRealtime() - startMillis);
                    }
                    int ttlSeconds = prefs.getInt(
                            PreferenceKeys.KEY_CACHE_TTL_SECONDS,
                            PreferenceKeys.DEFAULT_CACHE_TTL_SECONDS);
                    responseCache.put(question.cacheKey(), dnsResponse, ttlSeconds);
                    int latency = (int) (SystemClock.elapsedRealtime() - startMillis);
                    recordAllowedQuery(question.name, dohEnabled ? "DoH resolved" : "Standard resolved", latency);
                } catch (IOException error) {
                    dnsResponse = DnsMessage.servfailResponse(request.dnsPayload, question);
                }
            } else {
                recordCacheHit();
                recordAllowedQuery(question.name, "Cache hit", 0);
            }
            recordAllowed();
        }

        byte[] responsePacket = DnsPacket.buildUdpResponse(request, dnsResponse);
        synchronized (output) {
            output.write(responsePacket);
            output.flush();
        }
    }

    private byte[] forwardToUpstream(byte[] dnsPayload) throws IOException {
        String upstream = prefs.getString(
                PreferenceKeys.KEY_UPSTREAM_DNS,
                PreferenceKeys.DEFAULT_UPSTREAM_DNS);
        InetAddress upstreamAddress = InetAddress.getByName(upstream);

        // One socket per query keeps concurrent lookups independent and avoids
        // matching responses to the wrong in-flight question.
        DatagramSocket socket = new DatagramSocket();
        try {
            protect(socket);
            socket.setSoTimeout(3000);
            DatagramPacket query = new DatagramPacket(
                    dnsPayload,
                    dnsPayload.length,
                    upstreamAddress,
                    53);
            socket.send(query);

            byte[] buffer = new byte[1500];
            DatagramPacket response = new DatagramPacket(buffer, buffer.length);
            socket.receive(response);

            byte[] payload = new byte[response.getLength()];
            System.arraycopy(response.getData(), response.getOffset(), payload, 0, response.getLength());
            return payload;
        } finally {
            socket.close();
        }
    }

    private byte[] forwardViaDoh(byte[] dnsPayload) throws IOException {
        String endpoint = (dohUrl != null && !dohUrl.isEmpty())
                ? dohUrl
                : PreferenceKeys.DEFAULT_DOH_URL;
        return dohResolver.query(endpoint, dnsPayload);
    }

    private void loadDohConfig() {
        dohEnabled = prefs.getBoolean(
                PreferenceKeys.KEY_DOH_ENABLED,
                PreferenceKeys.DEFAULT_DOH_ENABLED);
        dohUrl = prefs.getString(
                PreferenceKeys.KEY_DOH_URL,
                PreferenceKeys.DEFAULT_DOH_URL);
        bypassGuardEnabled = prefs.getBoolean(
                PreferenceKeys.KEY_BYPASS_GUARD_ENABLED,
                PreferenceKeys.DEFAULT_BYPASS_GUARD_ENABLED);
        bypassExemptHost = null;
        if (dohUrl != null && !dohUrl.isEmpty()) {
            try {
                String host = java.net.URI.create(dohUrl).getHost();
                if (host != null) {
                    bypassExemptHost = host.toLowerCase(java.util.Locale.US);
                }
            } catch (IllegalArgumentException ignored) {
                // A malformed custom URL simply gets no exemption.
            }
        }
    }

    private void recordAllowed() {
        synchronized (statsLock) {
            pendingAllowed++;
        }
        flushStats(false);
    }

    private void recordBlocked(String domain) {
        synchronized (statsLock) {
            pendingBlocked++;
        }
        countBlockedDomain(domain);
        addRecentQuery(domain, "Blocklist rule", 0, true, "blocked", -1);
        flushStats(false);
    }

    private void recordSecurityBlocked(String domain) {
        synchronized (statsLock) {
            pendingBlocked++;
            pendingScamBlocked++;
        }
        countBlockedDomain(domain);
        addRecentQuery(domain, "Security blocklist", 75, true, "threat", -1);
        flushStats(false);
    }

    private void countBlockedDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return;
        }
        blockedDomainCounts.merge(domain.toLowerCase(java.util.Locale.US), 1L, Long::sum);
    }

    private ScamDetector.Result scamThreat(String domain) {
        if (!prefs.getBoolean(
                PreferenceKeys.KEY_SCAM_SHIELD_ENABLED,
                PreferenceKeys.DEFAULT_SCAM_SHIELD_ENABLED)) {
            return null;
        }
        if (blocker.isAllowed(domain)) {
            return null;
        }
        return ScamDetector.analyze(domain);
    }

    private void recordScamBlocked(String domain, ScamDetector.Result threat) {
        synchronized (statsLock) {
            pendingBlocked++;
            pendingScamBlocked++;
        }
        countBlockedDomain(domain);
        addRecentQuery(domain, "Scam shield: " + threat.reason, threat.score, true, "threat", -1);
        flushStats(false);
    }

    private void recordBypassBlocked(String domain) {
        synchronized (statsLock) {
            pendingBlocked++;
        }
        countBlockedDomain(domain);
        addRecentQuery(domain, "Bypass guard: private DNS sidestep", 0, true, "bypass", -1);
        flushStats(false);
    }

    private void recordAllowedQuery(String domain, String reason, int latencyMs) {
        addRecentQuery(domain, reason, 0, false, "allowed", latencyMs);
    }

    private void recordCacheHit() {
        synchronized (statsLock) {
            pendingCacheHits++;
        }
        flushStats(false);
    }

    private void recordUpstreamLookup(long elapsedMillis) {
        synchronized (statsLock) {
            long boundedMillis = Math.max(1L, Math.min(elapsedMillis, 10000L));
            long previousSamples = upstreamQueryTotal + pendingUpstreamQueries;
            upstreamAverageLatencyMs =
                    ((upstreamAverageLatencyMs * previousSamples) + boundedMillis) / (previousSamples + 1);
            pendingUpstreamQueries++;
        }
        flushStats(false);
    }

    private void recordDohQuery() {
        synchronized (statsLock) {
            pendingDohQueries++;
        }
        flushStats(false);
    }

    private void recordDohFallback() {
        synchronized (statsLock) {
            pendingDohFallbacks++;
        }
        flushStats(false);
    }

    private void flushStats(boolean force) {
        SharedPreferences.Editor editor;
        long blockedTodaySnapshot;
        synchronized (statsLock) {
            long pending = pendingAllowed + pendingBlocked + pendingCacheHits + pendingUpstreamQueries;
            long now = System.currentTimeMillis();
            if (!force && pending < 25 && now - lastStatsFlushMillis < 15000L) {
                return;
            }
            if (pending == 0 && !force) {
                return;
            }

            resetTodayIfNewDay();

            allowedTotal += pendingAllowed;
            blockedTotal += pendingBlocked;
            blockedToday += pendingBlocked;
            cacheHitTotal += pendingCacheHits;
            scamBlockedTotal += pendingScamBlocked;
            scamBlockedToday += pendingScamBlocked;
            upstreamQueryTotal += pendingUpstreamQueries;
            dohQueryTotal += pendingDohQueries;
            dohFallbackTotal += pendingDohFallbacks;

            pendingAllowed = 0;
            pendingBlocked = 0;
            pendingCacheHits = 0;
            pendingScamBlocked = 0;
            pendingUpstreamQueries = 0;
            pendingDohQueries = 0;
            pendingDohFallbacks = 0;
            lastStatsFlushMillis = now;
            blockedTodaySnapshot = blockedToday;

            editor = prefs.edit()
                .putLong(PreferenceKeys.KEY_ALLOWED_COUNT, allowedTotal)
                .putLong(PreferenceKeys.KEY_BLOCKED_COUNT, blockedTotal)
                .putLong(PreferenceKeys.KEY_BLOCKED_TODAY, blockedToday)
                .putLong(PreferenceKeys.KEY_CACHE_HIT_COUNT, cacheHitTotal)
                .putLong(PreferenceKeys.KEY_SCAM_BLOCKED_COUNT, scamBlockedTotal)
                .putLong(PreferenceKeys.KEY_SCAM_BLOCKED_TODAY, scamBlockedToday)
                .putString(PreferenceKeys.KEY_LAST_BLOCK_DAY, lastBlockDay)
                .putFloat(PreferenceKeys.KEY_UPSTREAM_AVERAGE_LATENCY_MS, upstreamAverageLatencyMs)
                .putLong(PreferenceKeys.KEY_UPSTREAM_QUERY_COUNT, upstreamQueryTotal)
                .putLong(PreferenceKeys.KEY_DOH_QUERY_COUNT, dohQueryTotal)
                .putLong(PreferenceKeys.KEY_DOH_FALLBACK_COUNT, dohFallbackTotal)
                .putString(PreferenceKeys.KEY_TOP_BLOCKED_JSON, topBlockedJson());
        }

        editor.apply();
        sendBroadcast(new Intent(ACTION_STATS_CHANGED).setPackage(getPackageName()));
        if (running.get()) {
            updateNotification(blockedTodaySnapshot + " blocked today");
        }
    }

    private void loadTopBlocked() {
        String json = prefs.getString(PreferenceKeys.KEY_TOP_BLOCKED_JSON, "");
        if (json == null || json.isEmpty()) {
            return;
        }
        try {
            org.json.JSONObject stored = new org.json.JSONObject(json);
            java.util.Iterator<String> keys = stored.keys();
            while (keys.hasNext()) {
                String domain = keys.next();
                long count = stored.optLong(domain, 0L);
                if (count > 0L) {
                    blockedDomainCounts.put(domain, count);
                }
            }
        } catch (org.json.JSONException ignored) {
            // A corrupt blob just means the leaderboard starts fresh.
        }
    }

    /** Serializes the highest counters and trims the in-memory map so it stays bounded. */
    private String topBlockedJson() {
        java.util.List<java.util.Map.Entry<String, Long>> entries =
                new java.util.ArrayList<>(blockedDomainCounts.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        org.json.JSONObject result = new org.json.JSONObject();
        try {
            int limit = Math.min(entries.size(), TOP_DOMAINS_LIMIT);
            for (int i = 0; i < limit; i++) {
                result.put(entries.get(i).getKey(), entries.get(i).getValue());
            }
        } catch (org.json.JSONException ignored) {
        }

        if (entries.size() > 400) {
            for (int i = 200; i < entries.size(); i++) {
                blockedDomainCounts.remove(entries.get(i).getKey());
            }
        }
        return result.toString();
    }

    private void resetTodayIfNewDay() {
        String today = getCurrentDay();
        if (!today.equals(lastBlockDay)) {
            blockedToday = 0;
            scamBlockedToday = 0;
            lastBlockDay = today;
        }
    }

    private String getCurrentDay() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        return sdf.format(new java.util.Date());
    }

    private PendingIntent buildConfigureIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return PendingIntent.getActivity(this, 0, intent, flags);
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(android.R.drawable.ic_secure)
                .setContentTitle("ClearGuard")
                .setContentText(text)
                .setContentIntent(buildConfigureIntent())
                .setOngoing(true)
                .setShowWhen(false)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ClearGuard VPN",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows when local DNS filtering is active.");
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private void closeVpnInterface() {
        if (vpnInterface == null) {
            return;
        }
        try {
            vpnInterface.close();
        } catch (IOException ignored) {
        }
        vpnInterface = null;
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    /** A single recent DNS query. Held in memory only, never written to disk. */
    public static final class BlockedQuery {
        public final String domain;
        public final long timeMillis;
        public final String reason;
        public final int threatScore;
        public final boolean blocked;
        public final String status; // "allowed", "blocked", "threat", "bypass"
        public final int latencyMs; // -1 for cache hits or blocks

        BlockedQuery(String domain, long timeMillis, String reason, int threatScore, boolean blocked, String status, int latencyMs) {
            this.domain = domain;
            this.timeMillis = timeMillis;
            this.reason = reason;
            this.threatScore = threatScore;
            this.blocked = blocked;
            this.status = status;
            this.latencyMs = latencyMs;
        }
    }
}
