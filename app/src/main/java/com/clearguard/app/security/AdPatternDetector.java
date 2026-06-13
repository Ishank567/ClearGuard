package com.clearguard.app.security;

import com.clearguard.app.blocking.HostBlocker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Conservative on-device ad/tracker pattern classifier for DNS hosts that are
 * not already covered by blocklists. It behaves like a tiny transparent model:
 * weighted feature signals in, score + explanation out.
 */
public final class AdPatternDetector {
    private static final int DEFAULT_THRESHOLD = 70;

    private static final Set<String> KNOWN_AD_SUFFIXES = new HashSet<>(Arrays.asList(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "google-analytics.com", "adsafeprotected.com", "moatads.com",
            "amazon-adsystem.com", "criteo.com", "criteo.net", "taboola.com",
            "outbrain.com", "mgid.com", "adcolony.com", "applovin.com",
            "inmobi.com", "smaato.net", "pubmatic.com", "rubiconproject.com",
            "openx.net", "innovid.com", "springserve.com", "spotxchange.com",
            "teads.tv", "unityads.unity3d.com", "vungle.com", "ironsrc.com",
            "chartboost.com", "adform.net", "adroll.com", "yieldmo.com",
            "scorecardresearch.com", "branch.io", "appsflyer.com"
    ));

    private static final Set<String> STRONG_PATTERNS = new HashSet<>(Arrays.asList(
            "adserver", "adservice", "adservices", "adsystem", "adnetwork",
            "adclick", "adtrack", "adtracking", "adtag", "adtech", "adunit",
            "nativeads", "sponsored", "sponsorads", "promoted", "interstitial",
            "rewardedvideo", "videoads", "bannerads", "prebid", "bidder",
            "rtbauction", "programmatic", "remarketing", "retargeting"
    ));

    private static final Set<String> AD_LABELS = new HashSet<>(Arrays.asList(
            "ad", "ads", "adv", "advert", "advertising", "adserver", "adservice",
            "adx", "ssp", "dsp", "rtb", "bid", "bidder", "prebid", "banner",
            "sponsor", "sponsored", "interstitial", "rewarded", "promo", "promoted"
    ));

    private static final Set<String> TRACKER_LABELS = new HashSet<>(Arrays.asList(
            "track", "tracker", "tracking", "analytics", "metric", "metrics",
            "measure", "measurement", "telemetry", "pixel", "beacon", "collect",
            "collector", "events", "event", "attribution", "affiliate", "click",
            "impression", "viewability", "conversion", "tag"
    ));

    private static final Set<String> INDIA_AD_NETWORKS = new HashSet<>(Arrays.asList(
            "adgebra", "vserv", "tyroo", "affle", "mfilterit", "vizury",
            "greedygame", "adcounty", "adsolut", "collectcent", "svgmedia"
    ));

    private static final Set<String> BENIGN_AD_WORDS = new HashSet<>(Arrays.asList(
            "adobe", "adp", "admin", "address", "admission", "advisor", "advice",
            "adidas", "adapter", "adaptor", "advance", "advanced", "advent",
            "adulting", "addition", "adequate"
    ));

    private AdPatternDetector() {
    }

    public static Result analyze(String rawDomain, String protectionMode, boolean regionalIndia) {
        String domain = HostBlocker.normalizeDomain(rawDomain);
        if (domain == null) {
            return Result.safe();
        }

        String lower = domain.toLowerCase(Locale.US);
        String registrable = registrablePart(lower);
        String sld = secondLevelLabel(registrable);
        String compact = sld.replace("-", "").replace("_", "");
        String[] labels = lower.split("\\.");

        int score = 0;
        String reason = "";

        for (String suffix : KNOWN_AD_SUFFIXES) {
            if (lower.equals(suffix) || lower.endsWith("." + suffix)) {
                score += 76;
                reason = "known ad-tech network";
                break;
            }
        }

        for (String strong : STRONG_PATTERNS) {
            if (compact.contains(strong) || lower.contains("." + strong + ".")) {
                score += 34;
                if (reason.isEmpty()) {
                    reason = "ad-serving host pattern";
                }
                break;
            }
        }

        int adSignals = 0;
        int trackerSignals = 0;
        for (String label : labels) {
            String clean = label.replace("-", "").replace("_", "");
            if (AD_LABELS.contains(clean) || clean.matches("ads?\\d{0,3}") || clean.matches("ad[sx]?[-_]?\\d{1,3}")) {
                adSignals++;
                score += 24;
            }
            if (TRACKER_LABELS.contains(clean) || clean.matches("(track|pixel|event|collect)\\d{0,3}")) {
                trackerSignals++;
                score += 18;
            }
            if ((clean.contains("click") || clean.contains("impression") || clean.contains("conversion")) &&
                    (compact.contains("ad") || compact.contains("promo") || compact.contains("campaign"))) {
                score += 18;
                if (reason.isEmpty()) {
                    reason = "click/impression attribution pattern";
                }
            }
        }

        if (adSignals >= 2) {
            score += 16;
            reason = "repeated ad endpoint labels";
        }
        if (trackerSignals >= 2) {
            score += 14;
            if (reason.isEmpty()) {
                reason = "tracker telemetry pattern";
            }
        }
        if (adSignals > 0 && trackerSignals > 0) {
            score += 20;
            reason = "ad tracking endpoint pattern";
        }

        if (containsAny(compact, INDIA_AD_NETWORKS)) {
            score += regionalIndia ? 42 : 32;
            reason = "regional ad-network pattern";
        }

        if (compact.contains("auction") || compact.contains("bidrequest") || compact.contains("openrtb")) {
            score += 36;
            reason = "real-time bidding endpoint";
        }
        if ((compact.contains("sdk") || compact.contains("mraid")) &&
                (compact.contains("ad") || compact.contains("video") || compact.contains("reward"))) {
            score += 26;
            if (reason.isEmpty()) {
                reason = "mobile ad SDK endpoint";
            }
        }

        int digitCount = countDigits(sld);
        int hyphenCount = countChar(sld, '-');
        if ((adSignals > 0 || trackerSignals > 0) && sld.length() >= 16 && (digitCount >= 2 || hyphenCount >= 2)) {
            score += 12;
            if (reason.isEmpty()) {
                reason = "machine-generated ad endpoint";
            }
        }

        int threshold = DEFAULT_THRESHOLD;
        if (protectionMode != null) {
            String mode = protectionMode.toLowerCase(Locale.US);
            if ("kids".equals(mode) || "elder".equals(mode) || "shopping".equals(mode) || "strict".equals(mode)) {
                threshold = 62;
            } else if ("battery".equals(mode)) {
                threshold = 74;
            }
        }

        if (containsAny(compact, BENIGN_AD_WORDS) && score < 86) {
            score -= 28;
            if (score < threshold) {
                reason = "benign ad-like word";
            }
        }

        int bounded = Math.max(0, Math.min(score, 100));
        if (bounded >= threshold) {
            return new Result(true, bounded, reason.isEmpty() ? "AI ad/tracker pattern" : reason);
        }
        return new Result(false, bounded, reason.isEmpty() ? "no ad pattern" : reason);
    }

    private static boolean containsAny(String text, Set<String> terms) {
        if (text == null) {
            return false;
        }
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private static String registrablePart(String domain) {
        String[] labels = domain.split("\\.");
        if (labels.length <= 2) {
            return domain;
        }
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    private static String secondLevelLabel(String registrable) {
        int dot = registrable.indexOf('.');
        return dot < 0 ? registrable : registrable.substring(0, dot);
    }

    private static int countDigits(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (Character.isDigit(value.charAt(i))) {
                count++;
            }
        }
        return count;
    }

    private static int countChar(String value, char needle) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == needle) {
                count++;
            }
        }
        return count;
    }

    public static final class Result {
        public final boolean blocked;
        public final int score;
        public final String reason;

        private Result(boolean blocked, int score, String reason) {
            this.blocked = blocked;
            this.score = score;
            this.reason = reason;
        }

        private static Result safe() {
            return new Result(false, 0, "no ad pattern");
        }
    }
}
