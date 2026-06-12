package com.clearguard.app.vpn;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.VpnService;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
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
import java.net.InetSocketAddress;
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

    private static final String CHANNEL_ID = "shield_dns_vpn";
    private static final int NOTIFICATION_ID = 31;
    private static final String VPN_ADDRESS = "10.64.0.2";
    private static final String VIRTUAL_DNS = "10.64.0.1";

    private static final int RECENT_QUERIES_MAX = 100;
    private static final java.util.ArrayDeque<BlockedQuery> RECENT_QUERIES = new java.util.ArrayDeque<>();

    // Map of packageName -> AppStats for privacy analysis
    private static final java.util.concurrent.ConcurrentHashMap<String, AppStats> APP_PRIVACY_STATS = new java.util.concurrent.ConcurrentHashMap<>();

    private static volatile boolean runningVisibleToUi;
    public static volatile boolean isRunning;

    private static final int MAX_QUERY_THREADS = 8;
    private static final int TOP_DOMAINS_LIMIT = 50;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final DnsResponseCache responseCache = new DnsResponseCache();
    private final Object statsLock = new Object();
    private final java.util.concurrent.ConcurrentHashMap<String, Long> blockedDomainCounts =
            new java.util.concurrent.ConcurrentHashMap<>();

    private volatile int vpnGeneration;
    private ParcelFileDescriptor vpnInterface;
    private HostBlocker blocker;
    private SharedPreferences prefs;
    private Thread workerThread;
    private ExecutorService queryExecutor;
    private DohResolver dohResolver;
    private ConnectivityManager.NetworkCallback wifiProtectionCallback;

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

    public static void reloadIfRunning(Context context) {
        if (!runningVisibleToUi) {
            return;
        }
        Intent intent = new Intent(context, ClearGuardVpnService.class).setAction(ACTION_RELOAD);
        context.startService(intent);
    }

    public static void restartIfRunning(Context context) {
        if (!runningVisibleToUi) {
            return;
        }
        Intent intent = new Intent(context, ClearGuardVpnService.class).setAction(ACTION_RESTART);
        context.startService(intent);
    }

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

    public static java.util.List<AppStats> getAppPrivacyStats() {
        return new java.util.ArrayList<>(APP_PRIVACY_STATS.values());
    }

    public static void clearAppPrivacyStats() {
        APP_PRIVACY_STATS.clear();
    }

    private static void addRecentQuery(String domain, String reason, int threatScore, boolean blocked, String status, int latencyMs, String appName, String appPackage) {
        synchronized (RECENT_QUERIES) {
            RECENT_QUERIES.addFirst(new BlockedQuery(
                    domain,
                    System.currentTimeMillis(),
                    reason,
                    threatScore,
                    blocked,
                    status,
                    latencyMs,
                    appName,
                    appPackage));
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
        registerWifiProtection();
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
            unregisterWifiProtection();
            registerWifiProtection();
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
        unregisterWifiProtection();
        super.onDestroy();
    }

    @Override
    public void onRevoke() {
        prefs.edit().putBoolean(PreferenceKeys.KEY_PROTECTION_DESIRED, false).apply();
        stopVpn();
        super.onRevoke();
    }

    private void startVpn() {
        if (running.get()) {
            return;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Starting ShieldDNS firewall"));
        try {
            Builder builder = new Builder()
                    .setSession("ShieldDNS")
                    .setMtu(1500)
                    .addAddress(VPN_ADDRESS, 32)
                    .addDnsServer(VIRTUAL_DNS)
                    .addRoute(VIRTUAL_DNS, 32)
                    .setBlocking(true)
                    .setConfigureIntent(buildConfigureIntent());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                builder.setMetered(false);
            }

            java.util.Set<String> excludedApps = prefs.getStringSet(
                    PreferenceKeys.KEY_EXCLUDED_APPS, java.util.Collections.emptySet());
            if (excludedApps != null) {
                for (String packageName : excludedApps) {
                    try {
                        builder.addDisallowedApplication(packageName);
                    } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
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
            workerThread = new Thread(() -> runPacketLoop(iface, generation), "ShieldDNSVpnThread");
            workerThread.start();
            prefs.edit().putBoolean(PreferenceKeys.KEY_PROTECTION_DESIRED, true).apply();
            updateNotification("ShieldDNS Active");
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
                        }
                    });
                }
            }
        } catch (IOException ignored) {
        } catch (java.util.concurrent.RejectedExecutionException ignored) {
        } finally {
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

        final AppInfo appInfo = getAppInfoForRequest(request);
        byte[] dnsResponse = null;

        // 1. Firewall rules
        // Block internet access for selected apps
        java.util.Set<String> blockedApps = prefs.getStringSet(PreferenceKeys.KEY_FIREWALL_BLOCKED_APPS, java.util.Collections.emptySet());
        if (blockedApps != null && blockedApps.contains(appInfo.packageName)) {
            dnsResponse = DnsMessage.servfailResponse(request.dnsPayload, question);
            recordFirewallBlocked(question.name, "Internet blocked", appInfo.name, appInfo.packageName);
        }

        // Wi-Fi / Mobile data rules
        if (dnsResponse == null) {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities nc = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            }
            boolean isWifi = nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI);
            boolean isMobile = nc != null && nc.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR);

            if (isWifi) {
                java.util.Set<String> blockedWifiApps = prefs.getStringSet(PreferenceKeys.KEY_FIREWALL_BLOCKED_WIFI, java.util.Collections.emptySet());
                if (blockedWifiApps != null && blockedWifiApps.contains(appInfo.packageName)) {
                    dnsResponse = DnsMessage.servfailResponse(request.dnsPayload, question);
                    recordFirewallBlocked(question.name, "Blocked on Wi-Fi", appInfo.name, appInfo.packageName);
                } else if (prefs.getBoolean("firewall_youtube_wifi", false) && question.name.contains("googlevideo.com")) {
                    dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                    recordFirewallBlocked(question.name, "YouTube Ads blocked on Wi-Fi", appInfo.name, appInfo.packageName);
                }
            } else if (isMobile) {
                java.util.Set<String> blockedMobileApps = prefs.getStringSet(PreferenceKeys.KEY_FIREWALL_BLOCKED_MOBILE, java.util.Collections.emptySet());
                if (blockedMobileApps != null && blockedMobileApps.contains(appInfo.packageName)) {
                    dnsResponse = DnsMessage.servfailResponse(request.dnsPayload, question);
                    recordFirewallBlocked(question.name, "Blocked on Mobile Data", appInfo.name, appInfo.packageName);
                }
            }
        }

        // Country blocking
        if (dnsResponse == null) {
            java.util.Set<String> blockedCountries = prefs.getStringSet(PreferenceKeys.KEY_BLOCKED_COUNTRIES, java.util.Collections.emptySet());
            if (blockedCountries != null && !blockedCountries.isEmpty()) {
                String domain = question.name.toLowerCase(java.util.Locale.US);
                for (String country : blockedCountries) {
                    String tld = "." + country.toLowerCase(java.util.Locale.US);
                    if (domain.endsWith(tld)) {
                        dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                        recordFirewallBlocked(question.name, "Country Block (" + country.toUpperCase(java.util.Locale.US) + ")", appInfo.name, appInfo.packageName);
                        break;
                    }
                }
            }
        }

        // India Regional Filter Pack
        if (dnsResponse == null && prefs.getBoolean(PreferenceKeys.KEY_REGIONAL_PACK_INDIA, PreferenceKeys.DEFAULT_REGIONAL_PACK_INDIA)) {
            String domain = question.name.toLowerCase(java.util.Locale.US);
            if (domain.contains("crichd") || domain.contains("thoptv") || domain.contains("pikashow") 
                || domain.contains("hotstar-ads") || domain.contains("voot-ads") || domain.contains("jiocinema-ads")
                || domain.contains("flipkart-ads") || domain.contains("meesho-ads") || domain.contains("myntra-tracker")) {
                dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                recordFirewallBlocked(question.name, "India Regional Pack (Ad/Popup Blocked)", appInfo.name, appInfo.packageName);
            }
        }

        // Time-based rules
        if (dnsResponse == null && prefs.getBoolean(PreferenceKeys.KEY_TIME_RULES_ENABLED, PreferenceKeys.DEFAULT_TIME_RULES_ENABLED)) {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
            if (hour >= 22 || hour < 6) {
                String domain = question.name.toLowerCase(java.util.Locale.US);
                if (domain.contains("facebook.com") || domain.contains("instagram.com") || domain.contains("tiktok.com") || domain.contains("twitter.com") || domain.contains("x.com") || domain.contains("snapchat.com")) {
                    dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                    recordFirewallBlocked(question.name, "Quiet Hours (Social Trackers Blocked)", appInfo.name, appInfo.packageName);
                }
            }
        }

        // Background app data blocker
        if (dnsResponse == null && prefs.getBoolean(PreferenceKeys.KEY_BACKGROUND_BLOCK_ENABLED, PreferenceKeys.DEFAULT_BACKGROUND_BLOCK_ENABLED)) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            boolean isScreenOn = pm != null && pm.isInteractive();
            if (!isScreenOn) {
                if (!appInfo.packageName.equals(getPackageName()) && !isEssentialSystemApp(appInfo.packageName)) {
                    dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                    recordFirewallBlocked(question.name, "Background App (Screen Off)", appInfo.name, appInfo.packageName);
                }
            } else if (hasUsageStatsPermission() && !isAppInForeground(appInfo.packageName)) {
                dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                recordFirewallBlocked(question.name, "Background App (Usage Stats)", appInfo.name, appInfo.packageName);
            }
        }

        // 2. Intent-Based Protection Modes (full vision support)
        String protectionMode = prefs.getString(PreferenceKeys.KEY_PROTECTION_MODE, PreferenceKeys.DEFAULT_PROTECTION_MODE);
        String legacySecurity = prefs.getString(PreferenceKeys.KEY_SECURITY_MODE, PreferenceKeys.DEFAULT_SECURITY_MODE);
        String effectiveMode = (!"default".equals(protectionMode)) ? protectionMode : legacySecurity;

        if (dnsResponse == null) {
            if (("strict".equals(effectiveMode) || "study".equals(effectiveMode) || "work".equals(effectiveMode)) && isStrictBlock(question.name)) {
                dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                recordFirewallBlocked(question.name, effectiveMode + " strict policy", appInfo.name, appInfo.packageName);
            } else if (("family".equals(effectiveMode) || "kids".equals(effectiveMode) || "elder".equals(effectiveMode)) && isFamilyBlock(question.name)) {
                dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                recordFirewallBlocked(question.name, effectiveMode + " protection", appInfo.name, appInfo.packageName);
            } else if ("spiritual".equals(effectiveMode) && (question.name.contains("bet") || question.name.contains("casino") || question.name.contains("rummy") || question.name.contains("dream11") || question.name.contains("gamble") || question.name.contains("adult") || question.name.contains("sex") || question.name.contains("dating"))) {
                dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                recordFirewallBlocked(question.name, "Spiritual / Satvik Clean", appInfo.name, appInfo.packageName);
            } else if ("shopping".equals(effectiveMode) && (question.name.contains("offer") || question.name.contains("deal") || question.name.contains("coupon") || question.name.contains("discount") || question.name.contains("sale"))) {
                // Light extra for shopping mode on obvious ad/tracker patterns
                if (question.name.contains("ad") || question.name.contains("track") || question.name.contains("analytics")) {
                    dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                    recordFirewallBlocked(question.name, "Shopping Mode - Fake offer / tracker", appInfo.name, appInfo.packageName);
                }
            }
        }

        // 3. DNS resolve & standard blocker
        if (dnsResponse == null) {
            String currentMode = prefs.getString(PreferenceKeys.KEY_PROTECTION_MODE, PreferenceKeys.DEFAULT_PROTECTION_MODE);
            ScamDetector.Result scamThreat = scamThreat(question.name, currentMode);
            if (blocker.shouldBlock(question.name)) {
                dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                if (blocker.isSecurityBlock(question.name)) {
                    recordSecurityBlocked(question.name, appInfo.name, appInfo.packageName);
                } else {
                    recordBlocked(question.name, appInfo.name, appInfo.packageName);
                }
            } else if (bypassGuardEnabled
                    && !blocker.isAllowed(question.name)
                    && DnsBypassGuard.isBypassDomain(question.name, bypassExemptHost)) {
                dnsResponse = DnsMessage.servfailResponse(request.dnsPayload, question);
                recordBypassBlocked(question.name, appInfo.name, appInfo.packageName);
            } else if (scamThreat != null && scamThreat.blocked) {
                dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
                recordScamBlocked(question.name, scamThreat, appInfo.name, appInfo.packageName);
            } else {
                dnsResponse = responseCache.get(question.cacheKey(), question.id);
                if (dnsResponse == null) {
                    try {
                        long startMillis = SystemClock.elapsedRealtime();
                        boolean useDoh = dohEnabled;
                        String activeDohUrl = dohUrl;

                        if ("gaming".equals(effectiveMode)) {
                            useDoh = true;
                            activeDohUrl = "https://cloudflare-dns.com/dns-query";
                        } else if ("battery".equals(effectiveMode) || "battery".equals(protectionMode)) {
                            useDoh = false;
                        }

                        if (useDoh) {
                            try {
                                String endpoint = (activeDohUrl != null && !activeDohUrl.isEmpty()) ? activeDohUrl : PreferenceKeys.DEFAULT_DOH_URL;
                                dnsResponse = dohResolver.query(endpoint, request.dnsPayload);
                                recordDohQuery();
                            } catch (IOException | RuntimeException dohError) {
                                dnsResponse = forwardToUpstream(request.dnsPayload);
                                recordDohFallback();
                                recordUpstreamLookup(SystemClock.elapsedRealtime() - startMillis);
                            }
                        } else {
                            dnsResponse = forwardToUpstream(request.dnsPayload);
                            recordUpstreamLookup(SystemClock.elapsedRealtime() - startMillis);
                        }

                        int ttlSeconds = prefs.getInt(PreferenceKeys.KEY_CACHE_TTL_SECONDS, PreferenceKeys.DEFAULT_CACHE_TTL_SECONDS);
                        if ("gaming".equals(effectiveMode)) {
                            ttlSeconds = Math.max(ttlSeconds, 1800);
                        } else if ("battery".equals(effectiveMode) || "battery".equals(protectionMode)) {
                            ttlSeconds = Math.max(ttlSeconds, 3600);
                        }

                        responseCache.put(question.cacheKey(), dnsResponse, ttlSeconds);
                        int latency = (int) (SystemClock.elapsedRealtime() - startMillis);
                        recordAllowedQuery(question.name, useDoh ? "DoH resolved" : "Standard resolved", latency, appInfo.name, appInfo.packageName);
                    } catch (IOException error) {
                        dnsResponse = DnsMessage.servfailResponse(request.dnsPayload, question);
                    }
                } else {
                    recordCacheHit();
                    recordAllowedQuery(question.name, "Cache hit", 0, appInfo.name, appInfo.packageName);
                }
                recordAllowed();
            }
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
            }
        }
    }

    private void recordAllowed() {
        synchronized (statsLock) {
            pendingAllowed++;
        }
        flushStats(false);
    }

    private void recordBlocked(String domain, String appName, String appPackage) {
        synchronized (statsLock) {
            pendingBlocked++;
        }
        countBlockedDomain(domain);
        addRecentQuery(domain, "Blocklist rule", 0, true, "blocked", -1, appName, appPackage);
        trackPrivacyStats(appPackage, appName, domain, true);
        flushStats(false);
    }

    private void recordSecurityBlocked(String domain, String appName, String appPackage) {
        synchronized (statsLock) {
            pendingBlocked++;
            pendingScamBlocked++;
        }
        countBlockedDomain(domain);
        addRecentQuery(domain, "Security blocklist", 75, true, "threat", -1, appName, appPackage);
        trackPrivacyStats(appPackage, appName, domain, true);
        flushStats(false);
    }

    private void recordFirewallBlocked(String domain, String reason, String appName, String appPackage) {
        synchronized (statsLock) {
            pendingBlocked++;
        }
        countBlockedDomain(domain);
        addRecentQuery(domain, "Firewall: " + reason, 50, true, "blocked", -1, appName, appPackage);
        trackPrivacyStats(appPackage, appName, domain, true);
        flushStats(false);
    }

    private void countBlockedDomain(String domain) {
        if (domain == null || domain.isEmpty()) {
            return;
        }
        blockedDomainCounts.merge(domain.toLowerCase(java.util.Locale.US), 1L, Long::sum);
    }

    private ScamDetector.Result scamThreat(String domain, String protectionMode) {
        if (!prefs.getBoolean(
                PreferenceKeys.KEY_SCAM_SHIELD_ENABLED,
                PreferenceKeys.DEFAULT_SCAM_SHIELD_ENABLED)) {
            return null;
        }
        if (blocker.isAllowed(domain)) {
            return null;
        }
        String mode = (protectionMode != null && !protectionMode.isEmpty())
                ? protectionMode
                : prefs.getString(PreferenceKeys.KEY_PROTECTION_MODE, PreferenceKeys.DEFAULT_PROTECTION_MODE);
        boolean religiousClean = "spiritual".equalsIgnoreCase(mode) || prefs.getBoolean("religious_clean_enabled", false);
        boolean indianScam = prefs.getBoolean(
                PreferenceKeys.KEY_INDIAN_SCAM_SHIELD_ENABLED,
                PreferenceKeys.DEFAULT_INDIAN_SCAM_SHIELD_ENABLED
        );
        boolean ruleEngine = prefs.getBoolean(
                PreferenceKeys.KEY_ON_DEVICE_RULE_ENGINE_ENABLED,
                PreferenceKeys.DEFAULT_ON_DEVICE_RULE_ENGINE_ENABLED
        );
        boolean useTFLite = ruleEngine && prefs.getBoolean(
                PreferenceKeys.KEY_PHISHING_TFLITE_ENABLED,
                PreferenceKeys.DEFAULT_PHISHING_TFLITE_ENABLED
        );

        // Pass context for TFLite (if enabled). The rule engine (regex+heuristics) is always used when flag is on.
        android.content.Context ctx = useTFLite ? this : null;
        ScamDetector.Result res = ScamDetector.analyze(domain, religiousClean, mode, indianScam, ctx, useTFLite);
        return res;
    }

    private void recordScamBlocked(String domain, ScamDetector.Result threat, String appName, String appPackage) {
        synchronized (statsLock) {
            pendingBlocked++;
            pendingScamBlocked++;
        }
        countBlockedDomain(domain);
        String displayReason = threat.reason;
        // Dedicated Indian Scam Shield prefix for the 9 categories (reason-based, no extra state needed)
        String r = threat.reason.toLowerCase(java.util.Locale.US);
        if (r.contains("upi kyc") || r.contains("electricity bill") || r.contains("courier delivery") ||
            r.contains("loan approval") || r.contains("job registration") || r.contains("government scheme") ||
            r.contains("investment group") || r.contains("apk download") || r.contains("customer care")) {
            displayReason = "Indian Scam Shield: " + threat.reason;
        }

        // Wire Mobile Number Risk Scoring (FRI) into VPN activity / recent queries
        // If the blocked domain/reason looks high-risk per local FRI DB (e.g. bank/UPI support domains),
        // append risk score to the activity log for "high-risk sender" vetting.
        try {
            com.clearguard.app.security.OnDeviceRuleEngine.ClassificationResult riskRes =
                com.clearguard.app.security.OnDeviceRuleEngine.classify(domain + " " + threat.reason, domain);
            if (riskRes.isPhishing() || riskRes.getScore() >= 60) {
                displayReason = displayReason + " [FRI Risk:" + riskRes.getScore() + " - high-risk sender per local DB]";
            }
        } catch (Exception ignored) {}

        addRecentQuery(domain, "Scam shield: " + displayReason, threat.score, true, "threat", -1, appName, appPackage);
        trackPrivacyStats(appPackage, appName, domain, true);
        flushStats(false);
    }

    private void recordBypassBlocked(String domain, String appName, String appPackage) {
        synchronized (statsLock) {
            pendingBlocked++;
        }
        countBlockedDomain(domain);
        addRecentQuery(domain, "Bypass guard: private DNS sidestep", 0, true, "bypass", -1, appName, appPackage);
        trackPrivacyStats(appPackage, appName, domain, true);
        flushStats(false);
    }

    private void recordAllowedQuery(String domain, String reason, int latencyMs, String appName, String appPackage) {
        addRecentQuery(domain, reason, 0, false, "allowed", latencyMs, appName, appPackage);
        trackPrivacyStats(appPackage, appName, domain, false);
    }

    // Public method for realtime risk events (e.g., from SMS/Call receivers) to log into the app's activity/stats
    public static void logHighRiskPhoneEvent(String phone, int riskScore, String context, String appName, String appPackage) {
        // Use a pseudo-domain for the phone risk event in recent queries
        String pseudoDomain = "phone:" + phone;
        String reason = "High-risk phone (FRI:" + riskScore + ") - " + context;
        addRecentQueryStatic(pseudoDomain, reason, 0, true, "threat", -1, appName != null ? appName : "Phone", appPackage != null ? appPackage : "phone.risk");
        // Also track as scam-like
        // Note: stats would need extension, but for activity it's wired
    }

    // Helper to allow static access for receivers (since addRecentQuery is instance)
    private static void addRecentQueryStatic(String domain, String reason, int threatScore, boolean blocked, String status, int latencyMs, String appName, String appPackage) {
        synchronized (RECENT_QUERIES) {
            RECENT_QUERIES.addFirst(new BlockedQuery(
                    domain,
                    System.currentTimeMillis(),
                    reason,
                    threatScore,
                    blocked,
                    status,
                    latencyMs,
                    appName,
                    appPackage));
            while (RECENT_QUERIES.size() > RECENT_QUERIES_MAX) {
                RECENT_QUERIES.removeLast();
            }
        }
        // Could flush stats, but for now activity log is updated
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
        }
    }

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
                .setContentTitle("ShieldDNS")
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
                "ShieldDNS VPN",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Shows when ShieldDNS local filtering is active.");
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

    // Heuristic helpers
    private AppInfo getAppInfoForRequest(DnsPacket.Request request) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return new AppInfo("System / Unknown", "unknown");
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return new AppInfo("System / Unknown", "unknown");

            java.net.InetAddress srcAddr = java.net.InetAddress.getByAddress(request.sourceAddress);
            java.net.InetAddress dstAddr = java.net.InetAddress.getByAddress(request.destinationAddress);

            java.net.InetSocketAddress local = new java.net.InetSocketAddress(srcAddr, request.sourcePort);
            java.net.InetSocketAddress remote = new java.net.InetSocketAddress(dstAddr, 53);

            int uid = cm.getConnectionOwnerUid(17, local, remote); // 17 is OsConstants.IPPROTO_UDP
            if (uid == android.os.Process.INVALID_UID) {
                return new AppInfo("System / Unknown", "unknown");
            }

            PackageManager pm = getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                String pkg = packages[0];
                String name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
                return new AppInfo(name, pkg);
            }
        } catch (Exception ignored) {
        }
        return new AppInfo("System / Unknown", "unknown");
    }

    private boolean isEssentialSystemApp(String packageName) {
        if (packageName == null || packageName.equals("unknown")) return true;
        return packageName.startsWith("android")
                || packageName.startsWith("com.android")
                || packageName.startsWith("com.google.android.gms")
                || packageName.contains("services")
                || packageName.contains("providers");
    }

    private boolean hasUsageStatsPermission() {
        try {
            android.app.AppOpsManager appOps = (android.app.AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception ignored) {
        }
        return false;
    }

    private boolean isAppInForeground(String packageName) {
        if (packageName == null || packageName.equals("unknown") || isEssentialSystemApp(packageName)) {
            return true;
        }
        try {
            android.app.usage.UsageStatsManager usm = (android.app.usage.UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long now = System.currentTimeMillis();
            android.app.usage.UsageEvents events = usm.queryEvents(now - 15000, now);
            android.app.usage.UsageEvents.Event event = new android.app.usage.UsageEvents.Event();
            String lastForegroundApp = null;
            while (events.hasNextEvent()) {
                events.getNextEvent(event);
                if (event.getEventType() == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) {
                    lastForegroundApp = event.getPackageName();
                }
            }
            if (lastForegroundApp != null) {
                return lastForegroundApp.equals(packageName);
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private boolean isStrictBlock(String domain) {
        String name = domain.toLowerCase(java.util.Locale.US);
        if (name.contains("facebook.net") || name.contains("connect.facebook")
                || name.contains("platform.twitter") || name.contains("syndication.twitter")
                || name.contains("platform.instagram") || name.contains("widget.tiktok")) {
            return true;
        }
        if (name.contains("fingerprintjs") || name.contains("deviceinfo") || name.contains("browser-fingerprint")) {
            return true;
        }
        return false;
    }

    private boolean isFamilyBlock(String domain) {
        String name = domain.toLowerCase(java.util.Locale.US);
        if (name.contains("porn") || name.contains("sex") || name.contains("xxx")
                || name.contains("xvideos") || name.contains("xnxx") || name.contains("onlyfans")
                || name.contains("cam4") || name.contains("chaturbate")) {
            return true;
        }
        if (name.contains("bet") || name.contains("casino") || name.contains("gambling")
                || name.contains("poker") || name.contains("stake.com") || name.contains("draftkings")
                || name.contains("roobet") || name.contains("bet365")) {
            return true;
        }
        if (name.contains("tiktok.com") || name.contains("byteoversea") || name.contains("kwai.com")) {
            return true;
        }
        return false;
    }

    private void trackPrivacyStats(String packageName, String appName, String domain, boolean blocked) {
        if (packageName == null || packageName.equals("unknown")) return;
        AppStats stats = APP_PRIVACY_STATS.computeIfAbsent(packageName, k -> new AppStats(packageName, appName));
        synchronized (stats) {
            stats.totalQueries++;
            if (blocked) {
                stats.blockedQueries++;
                String tracker = getTrackerCompany(domain);
                if (tracker != null) {
                    stats.trackers.merge(domain, 1L, Long::sum);
                }
            }
        }
    }

    private void registerWifiProtection() {
        if (!prefs.getBoolean(PreferenceKeys.KEY_WIFI_PROTECTION_ENABLED, PreferenceKeys.DEFAULT_WIFI_PROTECTION_ENABLED)) {
            return;
        }
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;

            android.net.NetworkRequest request = new android.net.NetworkRequest.Builder()
                    .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    .build();

            wifiProtectionCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network network) {
                    super.onAvailable(network);
                    if (isWifiUnsecured()) {
                        sendWifiAlertNotification();
                    }
                }
            };
            cm.registerNetworkCallback(request, wifiProtectionCallback);
        } catch (Exception ignored) {
        }
    }

    private void unregisterWifiProtection() {
        if (wifiProtectionCallback != null) {
            try {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    cm.unregisterNetworkCallback(wifiProtectionCallback);
                }
            } catch (Exception ignored) {
            }
            wifiProtectionCallback = null;
        }
    }

    private boolean isWifiUnsecured() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return false;
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return false;

            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (capabilities != null) {
                if (capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) {
                    return true;
                }
            }

            String ssid = info.getSSID();
            if (ssid != null) {
                String lower = ssid.toLowerCase(java.util.Locale.US);
                if (lower.contains("public") || lower.contains("guest") || lower.contains("free") || lower.contains("open") || lower.contains("airport") || lower.contains("hotel")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void sendWifiAlertNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent intent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 99, intent, flags);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(getString(com.clearguard.app.R.string.unsafe_wifi_title))
                .setContentText(getString(com.clearguard.app.R.string.unsafe_wifi_text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        manager.notify(102, notification);
    }

    private static class AppInfo {
        final String name;
        final String packageName;

        AppInfo(String name, String packageName) {
            this.name = name;
            this.packageName = packageName;
        }
    }

    // App Stats structure for Privacy Audit
    public static final class AppStats {
        public final String packageName;
        public final String appName;
        public long totalQueries;
        public long blockedQueries;
        public final java.util.concurrent.ConcurrentHashMap<String, Long> trackers = new java.util.concurrent.ConcurrentHashMap<>();

        public AppStats(String packageName, String appName) {
            this.packageName = packageName;
            this.appName = appName;
        }
    }

    public static final class TrackerConnection {
        public final String appName;
        public final String appPackage;
        public final String companyName;
        public final String domain;
        public final long count;

        public TrackerConnection(String appName, String appPackage, String companyName, String domain, long count) {
            this.appName = appName;
            this.appPackage = appPackage;
            this.companyName = companyName;
            this.domain = domain;
            this.count = count;
        }
    }

    public static java.util.List<TrackerConnection> getTrackerConnections() {
        java.util.List<TrackerConnection> list = new java.util.ArrayList<>();
        for (AppStats stats : APP_PRIVACY_STATS.values()) {
            for (java.util.Map.Entry<String, Long> entry : stats.trackers.entrySet()) {
                String domain = entry.getKey();
                String company = getTrackerCompany(domain);
                if (company != null) {
                    list.add(new TrackerConnection(stats.appName, stats.packageName, company, domain, entry.getValue()));
                }
            }
        }
        return list;
    }

    public static String getTrackerCompany(String domain) {
        if (domain == null) return null;
        String lower = domain.toLowerCase(java.util.Locale.US);
        if (lower.contains("google-analytics") || lower.contains("doubleclick") || lower.contains("googlead") || lower.contains("crashlytics") || lower.contains("adservice") || lower.contains("googletag")) {
            return "Google";
        }
        if (lower.contains("facebook") || lower.contains("fbcdn") || lower.contains("instagram") || lower.contains("messenger")) {
            return "Meta";
        }
        if (lower.contains("tiktok") || lower.contains("byteoversea") || lower.contains("bytedance")) {
            return "ByteDance";
        }
        if (lower.contains("amazon-ad") || lower.contains("assoc-amazon")) {
            return "Amazon";
        }
        if (lower.contains("adnxs") || lower.contains("microsoft") || lower.contains("clarity.ms") || lower.contains("app-measurement")) {
            return "Microsoft";
        }
        if (lower.contains("scorecardresearch")) {
            return "Comscore";
        }
        if (lower.contains("hotjar")) {
            return "Hotjar";
        }
        if (lower.contains("amplitude")) {
            return "Amplitude";
        }
        if (lower.contains("mixpanel")) {
            return "Mixpanel";
        }
        if (lower.contains("clevertap") || lower.contains("moengage") || lower.contains("webengage")) {
            return "Indian Martech";
        }
        if (lower.contains("inmobi") || lower.contains("mopub") || lower.contains("applovin")) {
            return "Ad Network";
        }
        if (lower.contains("branch.io") || lower.contains("adjust.") || lower.contains("appsflyer")) {
            return "Attribution";
        }
        // Indian e-com / regional trackers from vision
        if (lower.contains("meesho") || lower.contains("flipkart") || lower.contains("myntra")) {
            return "Indian E-com";
        }
        if (lower.contains("razorpay") || lower.contains("payu") || lower.contains("cashfree")) {
            return "Payments";
        }
        if (lower.contains("flurry") || lower.contains("yahoo")) {
            return "Yahoo";
        }
        return "Other Ad / Analytics";
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
        public final String appName;
        public final String appPackage;

        BlockedQuery(String domain, long timeMillis, String reason, int threatScore, boolean blocked, String status, int latencyMs, String appName, String appPackage) {
            this.domain = domain;
            this.timeMillis = timeMillis;
            this.reason = reason;
            this.threatScore = threatScore;
            this.blocked = blocked;
            this.status = status;
            this.latencyMs = latencyMs;
            this.appName = appName;
            this.appPackage = appPackage;
        }
    }
}
