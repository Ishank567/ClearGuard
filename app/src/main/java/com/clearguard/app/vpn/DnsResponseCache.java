package com.clearguard.app.vpn;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

final class DnsResponseCache {
    private static final int MAX_ENTRIES = 512;

    private final LinkedHashMap<String, Entry> cache =
            new LinkedHashMap<String, Entry>(MAX_ENTRIES, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                    return size() > MAX_ENTRIES;
                }
            };

    synchronized byte[] get(String key, int transactionId) {
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAtMillis) {
            cache.remove(key);
            return null;
        }
        byte[] copy = Arrays.copyOf(entry.payload, entry.payload.length);
        copy[0] = (byte) ((transactionId >>> 8) & 0xFF);
        copy[1] = (byte) (transactionId & 0xFF);
        return copy;
    }

    synchronized void put(String key, byte[] payload, int ttlSeconds) {
        if (payload.length < 2 || ttlSeconds <= 0) {
            return;
        }
        long expiresAt = System.currentTimeMillis() + Math.min(ttlSeconds, 900) * 1000L;
        cache.put(key, new Entry(Arrays.copyOf(payload, payload.length), expiresAt));
    }

    synchronized void clear() {
        cache.clear();
    }

    private static final class Entry {
        final byte[] payload;
        final long expiresAtMillis;

        Entry(byte[] payload, long expiresAtMillis) {
            this.payload = payload;
            this.expiresAtMillis = expiresAtMillis;
        }
    }
}
