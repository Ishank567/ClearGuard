package com.clearguard.app.security;

import com.clearguard.app.blocking.HostBlocker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Detects DNS names that apps use to silently switch to their own encrypted DNS
 * (DoH/DoT), which would route lookups around ClearGuard's filtering. Answering
 * these with SERVFAIL makes browsers and apps fall back to the system resolver,
 * keeping their traffic inside the filter. The list also includes Mozilla's
 * canary domain, which tells Firefox not to enable auto-DoH at all.
 *
 * Matching is by suffix, so an entry like "cloudflare-dns.com" also covers
 * mozilla.cloudflare-dns.com and chrome.cloudflare-dns.com.
 */
public final class DnsBypassGuard {

    private static final Set<String> BYPASS_HOSTS = new HashSet<>(Arrays.asList(
            // Auto-DoH canary: SERVFAIL here disables Firefox's automatic DoH rollout.
            "use.application-dns.net",
            // Google Public DNS
            "dns.google",
            "dns.google.com",
            // Cloudflare (covers mozilla./chrome./security./family. subdomains)
            "cloudflare-dns.com",
            "one.one.one.one",
            // Quad9
            "dns.quad9.net",
            // OpenDNS
            "doh.opendns.com",
            "doh.familyshield.opendns.com",
            // AdGuard DNS
            "dns.adguard.com",
            "dns.adguard-dns.com",
            "dns-family.adguard.com",
            // NextDNS (covers per-profile subdomains)
            "dns.nextdns.io",
            // CleanBrowsing
            "doh.cleanbrowsing.org",
            // Mullvad
            "dns.mullvad.net"
    ));

    private DnsBypassGuard() {
    }

    /**
     * @param exemptHost the host of the user's own configured DoH endpoint (or null);
     *                   never flagged so ClearGuard cannot cut off its own resolver.
     */
    public static boolean isBypassDomain(String domain, String exemptHost) {
        String normalized = HostBlocker.normalizeDomain(domain);
        if (normalized == null) {
            return false;
        }
        if (exemptHost != null
                && (normalized.equals(exemptHost) || normalized.endsWith("." + exemptHost))) {
            return false;
        }

        String cursor = normalized;
        while (true) {
            if (BYPASS_HOSTS.contains(cursor)) {
                return true;
            }
            int dot = cursor.indexOf('.');
            if (dot < 0) {
                return false;
            }
            cursor = cursor.substring(dot + 1);
        }
    }
}
