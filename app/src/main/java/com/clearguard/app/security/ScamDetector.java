package com.clearguard.app.security;

import com.clearguard.app.blocking.HostBlocker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lightweight on-device threat scoring for DNS queries (Scam + DGA/malware C2 detection).
 *
 * Uses heuristics for phishing/brand impersonation + Shannon entropy analysis to catch
 * algorithmically generated domains (common in modern malware). Fully on-device.
 */
public final class ScamDetector {
    private static final int BLOCK_THRESHOLD = 70;

    private static final Set<String> HIGH_RISK_TLDS = new HashSet<>(Arrays.asList(
            "zip", "mov", "cam", "click", "country", "gq", "icu", "kim", "loan", "mom",
            "party", "quest", "rest", "review", "sbs", "stream", "support", "top", "work", "xyz"
    ));

    private static final Set<String> TRUSTED_BRANDS = new HashSet<>(Arrays.asList(
            "amazon", "apple", "binance", "cashapp", "coinbase", "facebook", "google",
            "instagram", "microsoft", "netflix", "paypal", "spotify", "telegram", "whatsapp",
            "yahoo", "youtube"
    ));

    private static final Set<String> RISK_TERMS = new HashSet<>(Arrays.asList(
            "account", "alert", "bank", "billing", "bonus", "claim", "confirm", "crypto",
            "gift", "invoice", "kyc", "login", "password", "payment", "prize", "recover",
            "refund", "secure", "security", "signin", "support", "suspend", "unlock", "verify",
            "wallet", "withdraw"
    ));

    private ScamDetector() {
    }

    public static Result analyze(String rawDomain) {
        String domain = HostBlocker.normalizeDomain(rawDomain);
        if (domain == null) {
            return Result.safe();
        }

        String registrable = registrablePart(domain);
        String sld = secondLevelLabel(registrable);
        String tld = topLevelLabel(registrable);
        String compact = sld.replace("-", "").replace("_", "");

        int score = 0;
        String reason = "";

        // DGA / high-entropy detection (trending malware C2 technique)
        double entropy = shannonEntropy(sld);
        if (sld.length() >= 11 && entropy >= 3.8) {
            int entropyPoints = (int) Math.min(32, (entropy - 3.6) * 18);
            score += entropyPoints;
            if (reason.isEmpty() || entropy >= 4.1) {
                reason = "high-entropy / likely machine-generated domain (DGA)";
            }
        }

        if (domain.startsWith("xn--") || domain.contains(".xn--")) {
            score += 45;
            reason = "IDN look-alike domain";
        }

        if (HIGH_RISK_TLDS.contains(tld)) {
            score += 25;
            if (reason.isEmpty()) {
                reason = "high-risk domain ending";
            }
        }

        int digitCount = countDigits(sld);
        int hyphenCount = countChar(sld, '-');
        if (sld.length() >= 18 && digitCount >= 3) {
            score += 25;
            if (reason.isEmpty()) {
                reason = "randomized domain pattern";
            }
        }
        if (hyphenCount >= 2) {
            score += 15;
            if (reason.isEmpty()) {
                reason = "excessive hyphen pattern";
            }
        }

        boolean hasBrand = false;
        for (String brand : TRUSTED_BRANDS) {
            if (isBrandImpersonation(sld, compact, brand)) {
                hasBrand = true;
                score += 35;
                reason = "brand impersonation pattern";
                break;
            }
            if (levenshteinDistance(compact, brand) == 1) {
                hasBrand = true;
                score += 45;
                reason = "brand look-alike pattern";
                break;
            }
        }

        int riskTerms = 0;
        String[] labels = domain.split("\\.");
        for (String label : labels) {
            for (String term : RISK_TERMS) {
                if (label.contains(term)) {
                    riskTerms++;
                    break;
                }
            }
        }
        if (riskTerms > 0) {
            score += Math.min(30, riskTerms * 12);
            if (reason.isEmpty()) {
                reason = "credential or payment lure";
            }
        }

        if (hasBrand && riskTerms > 0) {
            score += 25;
            reason = "brand plus credential lure";
        }

        if (domain.length() >= 45 && digitCount >= 4) {
            score += 15;
            if (reason.isEmpty()) {
                reason = "long suspicious host";
            }
        }

        int boundedScore = Math.min(score, 100);
        if (boundedScore >= BLOCK_THRESHOLD) {
            return new Result(true, boundedScore, reason.isEmpty() ? "scam-like domain" : reason);
        }
        return new Result(false, boundedScore, reason.isEmpty() ? "no scam signal" : reason);
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

    private static String topLevelLabel(String registrable) {
        int dot = registrable.lastIndexOf('.');
        return dot < 0 ? "" : registrable.substring(dot + 1).toLowerCase(Locale.US);
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

    private static boolean isBrandImpersonation(String sld, String compact, String brand) {
        if (compact.equals(brand)) {
            return false;
        }
        if (compact.startsWith(brand) || compact.endsWith(brand)) {
            return true;
        }
        String[] tokens = sld.split("-");
        for (String token : tokens) {
            if (token.equals(brand)) {
                return true;
            }
        }
        return false;
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

    private static int levenshteinDistance(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 1) {
            return 2;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[b.length()];
    }

    /**
     * Shannon entropy (bits per character) for the label. High values indicate randomness
     * typical of Domain Generation Algorithms used by malware for C2 and evasion.
     */
    private static double shannonEntropy(String s) {
        if (s == null || s.isEmpty()) return 0.0;
        int[] counts = new int[256];
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toLowerCase(s.charAt(i));
            counts[c & 0xFF]++;
        }
        double len = s.length();
        double entropy = 0.0;
        for (int count : counts) {
            if (count > 0) {
                double p = count / len;
                entropy -= p * (Math.log(p) / Math.log(2.0));
            }
        }
        return entropy;
    }

    public static final class Result {
        public final boolean blocked;
        public final int score;
        public final String reason;

        Result(boolean blocked, int score, String reason) {
            this.blocked = blocked;
            this.score = score;
            this.reason = reason;
        }

        static Result safe() {
            return new Result(false, 0, "no scam signal");
        }
    }
}
