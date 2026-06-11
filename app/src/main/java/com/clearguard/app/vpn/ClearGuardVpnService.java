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

import com.clearguard.app.MainActivity;
import com.clearguard.app.PreferenceKeys;
import com.clearguard.app.blocking.HostBlocker;

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
    private long pendingAllowed;
    private long pendingBlocked;
    private long lastStatsFlushMillis;
    private String lastBlockDay;

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
        lastBlockDay = prefs.getString(PreferenceKeys.KEY_LAST_BLOCK_DAY, "");
        lastStatsFlushMillis = System.currentTimeMillis();
        resetTodayIfNewDay();
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
        if (blocker.shouldBlock(question.name)) {
            dnsResponse = DnsMessage.blockedResponse(request.dnsPayload, question);
            recordBlocked();
        } else {
            dnsResponse = responseCache.get(question.cacheKey(), question.id);
            if (dnsResponse == null) {
                try {
                    dnsResponse = forwardToUpstream(request.dnsPayload);
                    int ttlSeconds = prefs.getInt(
                            PreferenceKeys.KEY_CACHE_TTL_SECONDS,
                            PreferenceKeys.DEFAULT_CACHE_TTL_SECONDS);
                    responseCache.put(question.cacheKey(), dnsResponse, ttlSeconds);
                } catch (IOException error) {
                    dnsResponse = DnsMessage.servfailResponse(request.dnsPayload, question);
                }
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

    private void recordBlocked() {
        pendingBlocked++;
        flushStats(false);
    }

    private void flushStats(boolean force) {
        long pending = pendingAllowed + pendingBlocked;
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

        pendingAllowed = 0;
        pendingBlocked = 0;
        lastStatsFlushMillis = now;

        prefs.edit()
                .putLong(PreferenceKeys.KEY_ALLOWED_COUNT, allowedTotal)
                .putLong(PreferenceKeys.KEY_BLOCKED_COUNT, blockedTotal)
                .putLong(PreferenceKeys.KEY_BLOCKED_TODAY, blockedToday)
                .putString(PreferenceKeys.KEY_LAST_BLOCK_DAY, lastBlockDay)
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
}
