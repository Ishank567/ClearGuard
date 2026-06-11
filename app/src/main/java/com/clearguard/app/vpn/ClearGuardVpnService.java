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
import com.clearguard.app.security.ScamDetector;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ClearGuardVpnService extends VpnService {
    public static final String ACTION_START = "com.clearguard.app.START";
    public static final String ACTION_STOP = "com.clearguard.app.STOP";
    public static final String ACTION_RELOAD = "com.clearguard.app.RELOAD";
    public static final String ACTION_STATS_CHANGED = "com.clearguard.app.STATS_CHANGED";

    private static final String CHANNEL_ID = "clear_guard_vpn";
    private static final int NOTIFICATION_ID = 31;
    private static final String VPN_ADDRESS = "10.64.0.2";
    private static final String VIRTUAL_DNS = "10.64.0.1";

    private static final int RECENT_BLOCKED_MAX = 50;
    private static final java.util.ArrayDeque<BlockedQuery> RECENT_BLOCKED = new java.util.ArrayDeque<>();

    private static volatile boolean runningVisibleToUi;
    public static volatile boolean isRunning;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final DnsResponseCache responseCache = new DnsResponseCache();
    private final Object upstreamSocketLock = new Object();

    private ParcelFileDescriptor vpnInterface;
    private DatagramSocket upstreamSocket;
    private HostBlocker blocker;
    private SharedPreferences prefs;
    private Thread workerThread;

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

    /** A point-in-time snapshot of recently blocked domains (most recent first). */
    public static java.util.List<BlockedQuery> recentBlocked() {
        synchronized (RECENT_BLOCKED) {
            return new java.util.ArrayList<>(RECENT_BLOCKED);
        }
    }

    public static void clearRecentBlocked() {
        synchronized (RECENT_BLOCKED) {
            RECENT_BLOCKED.clear();
        }
    }

    private static void addRecentBlocked(String domain, String reason, int threatScore) {
        synchronized (RECENT_BLOCKED) {
            RECENT_BLOCKED.addFirst(new BlockedQuery(
                    domain,
                    System.currentTimeMillis(),
                    reason,
                    threatScore));
            while (RECENT_BLOCKED.size() > RECENT_BLOCKED_MAX) {
                RECENT_BLOCKED.removeLast();
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

        loadDohConfig();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
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

        startVpn();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopVpn();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
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

            vpnInterface = builder.establish();
            if (vpnInterface == null) {
                throw new IOException("VPN permission was not granted");
            }

            running.set(true);
            runningVisibleToUi = true;
            isRunning = true;
            workerThread = new Thread(this::runPacketLoop, "ClearGuardDnsVpn");
            workerThread.start();
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
        closeUpstreamSocket();
        closeVpnInterface();
        flushStats(true);
        clearRecentBlocked();
        stopForegroundCompat();
    }

    private void runPacketLoop() {
        byte[] packet = new byte[32767];
        try (
                FileInputStream input = new FileInputStream(vpnInterface.getFileDescriptor());
                FileOutputStream output = new FileOutputStream(vpnInterface.getFileDescriptor())
        ) {
            while (running.get()) {
                int length = input.read(packet);
                if (length > 0) {
                    handlePacket(packet, length, output);
                }
            }
        } catch (IOException ignored) {
            // Closing the VPN descriptor during stop also lands here.
        } finally {
            stopVpn();
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
            recordBlocked(question.name);
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
                } catch (IOException error) {
                    dnsResponse = DnsMessage.servfailResponse(request.dnsPayload, question);
                }
            } else {
                recordCacheHit();
            }
            recordAllowed();
        }

        byte[] responsePacket = DnsPacket.buildUdpResponse(request, dnsResponse);
        output.write(responsePacket);
        output.flush();
    }

    private byte[] forwardToUpstream(byte[] dnsPayload) throws IOException {
        String upstream = prefs.getString(
                PreferenceKeys.KEY_UPSTREAM_DNS,
                PreferenceKeys.DEFAULT_UPSTREAM_DNS);
        InetAddress upstreamAddress = InetAddress.getByName(upstream);

        synchronized (upstreamSocketLock) {
            DatagramSocket socket = upstreamSocket();
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
        }
    }

    private byte[] forwardViaDoh(byte[] dnsPayload) throws IOException {
        String endpoint = (dohUrl != null && !dohUrl.isEmpty())
                ? dohUrl
                : PreferenceKeys.DEFAULT_DOH_URL;

        java.net.URL url = new java.net.URL(endpoint);
        javax.net.ssl.HttpsURLConnection conn = (javax.net.ssl.HttpsURLConnection) url.openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(6500);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/dns-message");
        conn.setRequestProperty("Accept", "application/dns-message");
        conn.setRequestProperty("User-Agent", "ClearGuard/0.2 DoH");
        conn.setDoOutput(true);
        conn.setDoInput(true);
        conn.setInstanceFollowRedirects(true);

        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(dnsPayload);
            os.flush();
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new IOException("DoH server returned HTTP " + code);
        }

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream is = conn.getInputStream()) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = is.read(buf)) != -1) {
                baos.write(buf, 0, read);
            }
        } finally {
            conn.disconnect();
        }
        return baos.toByteArray();
    }

    private void loadDohConfig() {
        dohEnabled = prefs.getBoolean(
                PreferenceKeys.KEY_DOH_ENABLED,
                PreferenceKeys.DEFAULT_DOH_ENABLED);
        dohUrl = prefs.getString(
                PreferenceKeys.KEY_DOH_URL,
                PreferenceKeys.DEFAULT_DOH_URL);
    }

    private DatagramSocket upstreamSocket() throws SocketException {
        if (upstreamSocket == null || upstreamSocket.isClosed()) {
            upstreamSocket = new DatagramSocket();
            protect(upstreamSocket);
            upstreamSocket.setSoTimeout(3000);
        }
        return upstreamSocket;
    }

    private void recordAllowed() {
        pendingAllowed++;
        flushStats(false);
    }

    private void recordBlocked(String domain) {
        pendingBlocked++;
        addRecentBlocked(domain, "Blocklist rule", 0);
        flushStats(false);
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
        pendingBlocked++;
        pendingScamBlocked++;
        addRecentBlocked(domain, "Scam shield: " + threat.reason, threat.score);
        flushStats(false);
    }

    private void recordCacheHit() {
        pendingCacheHits++;
        flushStats(false);
    }

    private void recordUpstreamLookup(long elapsedMillis) {
        long boundedMillis = Math.max(1L, Math.min(elapsedMillis, 10000L));
        long previousSamples = upstreamQueryTotal + pendingUpstreamQueries;
        upstreamAverageLatencyMs =
                ((upstreamAverageLatencyMs * previousSamples) + boundedMillis) / (previousSamples + 1);
        pendingUpstreamQueries++;
        flushStats(false);
    }

    private void recordDohQuery() {
        pendingDohQueries++;
        flushStats(false);
    }

    private void recordDohFallback() {
        pendingDohFallbacks++;
        flushStats(false);
    }

    private void flushStats(boolean force) {
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

        prefs.edit()
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
                .apply();

        sendBroadcast(new Intent(ACTION_STATS_CHANGED).setPackage(getPackageName()));
        if (running.get()) {
            updateNotification(blockedToday + " blocked today");
        }
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

    private void closeUpstreamSocket() {
        synchronized (upstreamSocketLock) {
            if (upstreamSocket != null) {
                upstreamSocket.close();
                upstreamSocket = null;
            }
        }
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }

    /** A single recently blocked DNS query. Held in memory only, never written to disk. */
    public static final class BlockedQuery {
        public final String domain;
        public final long timeMillis;
        public final String reason;
        public final int threatScore;

        BlockedQuery(String domain, long timeMillis, String reason, int threatScore) {
            this.domain = domain;
            this.timeMillis = timeMillis;
            this.reason = reason;
            this.threatScore = threatScore;
        }
    }
}
