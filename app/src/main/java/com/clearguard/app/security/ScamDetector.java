package com.clearguard.app.security;

import com.clearguard.app.blocking.HostBlocker;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import android.content.Context; // for optional TFLite path

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

    // === Expanded India-specific scam lures (AI Scam Ad Detector + UPI/Banking Shield) ===
    private static final Set<String> WIN_MONEY_LURES = new HashSet<>(Arrays.asList(
            "won", "win", "₹", "rupee", "lakh", "crore", "50,000", "50000", "1lakh", "prize", "lottery", "jackpot"
    ));
    private static final Set<String> KYC_UPDATE_LURES = new HashSet<>(Arrays.asList(
            "kyc", "updatekyc", "ekyc", "verifykyc", "completekyc", "kycnow"
    ));
    private static final Set<String> INSTANT_LOAN_LURES = new HashSet<>(Arrays.asList(
            "instantloan", "quickloan", "fastloan", "loanapproved", "loantoday", "getloan", "easyloan", "personalloan"
    ));
    private static final Set<String> APK_CLAIM_LURES = new HashSet<>(Arrays.asList(
            "installapk", "downloadapk", "claimreward", "getreward", "apk", "claimnow"
    ));
    private static final Set<String> COURIER_FAKE = new HashSet<>(Arrays.asList(
            "courier", "delivery", "trackparcel", "shipment", "postoffice", "delhivery", "ekart"
    ));
    private static final Set<String> GOV_SUBSIDY_FAKE = new HashSet<>(Arrays.asList(
            "subsidy", "pmkisan", "sarkari", "government", "yojana", "scheme", "free", "ration", "aayushman"
    ));
    private static final Set<String> INVESTMENT_SCAM = new HashSet<>(Arrays.asList(
            "investment", "doublemoney", "guaranteedreturn", "trading", "crypto", "bitcoin", "forex", "mutualfund"
    ));
    private static final Set<String> FAKE_JOB = new HashSet<>(Arrays.asList(
            "job", "parttime", "workfromhome", "earnfromhome", "recruitment", "hiring", "salary"
    ));

    // Official-ish Indian banking / UPI / Gov domains for similarity detection (UPI & Banking Safety Shield + Indian Scam Shield)
    private static final Set<String> OFFICIAL_BANK_DOMAINS = new HashSet<>(Arrays.asList(
            "sbi.co.in", "onlinesbi.sbi", "hdfcbank.com", "icicibank.com", "axisbank.com",
            "paytm.com", "phonepe.com", "gpay.app", "bhimupi.in", "upi.gov.in",
            "canarabank.in", "pnbindia.in", "bankofbaroda.in", "kotak.com", "rblbank.com",
            "yesbank.in", "indusind.com", "federalbank.co.in",
            // Government & critical services (for fake scheme / subsidy impersonation)
            "uidai.gov.in", "incometax.gov.in", "irctc.co.in", "epfindia.gov.in", "pmkisan.gov.in",
            "mygov.in", "india.gov.in", "aadhaar.gov.in"
    ));

    // === Indian Scam Shield - dedicated patterns for the 9 common India-specific scams ===
    private static final Set<String> ELECTRICITY_BILL_SCAM = new HashSet<>(Arrays.asList(
            "electricity", "ebill", "billpay", "currentbill", "powerbill", "mseb", "bescom",
            "tneb", "electricityboard", "paymybill", "ebillpay", "electricitypayment", "billpayment"
    ));
    private static final Set<String> CUSTOMER_CARE_FAKE = new HashSet<>(Arrays.asList(
            "customercare", "helpline", "tollfree", "customersupport", "supportnumber",
            "callnow", "1800", "toll", "refundhelpline", "care"
    ));
    private static final Set<String> JOB_REG_FEE = new HashSet<>(Arrays.asList(
            "registrationfee", "regfee", "jobfee", "registration", "applyfee", "joiningfee"
    ));
    private static final Set<String> GOV_SCHEME_FAKE = new HashSet<>(Arrays.asList(
            "pmkisan", "pmjay", "aayushman", "sarkariyojana", "govtscheme", "freelaptop", "freescheme"
    ));

    private ScamDetector() {
    }

    public static Result analyze(String rawDomain, boolean religiousCleanEnabled) {
        return analyze(rawDomain, religiousCleanEnabled, null, true, null, true);
    }

    public static Result analyze(String rawDomain, boolean religiousCleanEnabled, String protectionMode) {
        return analyze(rawDomain, religiousCleanEnabled, protectionMode, true, null, true);
    }

    /**
     * Enhanced analysis with on-device rule engine (fast regex + heuristics) + optional small TFLite phishing model.
     * indianScamShieldEnabled enables the 9 dedicated India-specific scam patterns.
     */
    public static Result analyze(String rawDomain, boolean religiousCleanEnabled, String protectionMode, boolean indianScamShieldEnabled,
                                 android.content.Context contextForTFLite, boolean useTFLiteIfAvailable) {
        String domain = HostBlocker.normalizeDomain(rawDomain);
        if (domain == null) {
            return Result.safe();
        }

        String registrable = registrablePart(domain);
        String sld = secondLevelLabel(registrable);
        String tld = topLevelLabel(registrable);
        String compact = sld.replace("-", "").replace("_", "").toLowerCase(Locale.US);

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

        // --- UPI Scam Check ---
        boolean containsUpiBrand = compact.contains("upi") || compact.contains("paytm") || compact.contains("phonepe") || compact.contains("gpay") || compact.contains("bhim");
        boolean containsScamTerm = compact.contains("claim") || compact.contains("cashback") || compact.contains("refund") || compact.contains("bonus") || compact.contains("reward") || compact.contains("gift") || compact.contains("luck");
        if (containsUpiBrand && containsScamTerm) {
            score += 70;
            reason = "UPI scam / payment fraud phishing lure";
        }

        // --- Fake Job Ad Check ---
        boolean containsJobTerm = compact.contains("job") || compact.contains("parttime") || compact.contains("workfromhome") || compact.contains("recruitment") || compact.contains("taskearn");
        boolean containsEarnTerm = compact.contains("earn") || compact.contains("salary") || compact.contains("income") || compact.contains("commission");
        if (containsJobTerm && containsEarnTerm) {
            score += 70;
            reason = "Fake job recruitment scam";
        }

        // --- Predatory Loan App Check ---
        boolean containsLoanTerm = compact.contains("loan") || compact.contains("kredit") || compact.contains("rupee") || compact.contains("paisae") || compact.contains("dhan") || compact.contains("cash") || compact.contains("credit");
        boolean containsPredatoryTerm = compact.contains("quick") || compact.contains("instant") || compact.contains("fast") || compact.contains("easy") || compact.contains("approve");
        if (containsLoanTerm && containsPredatoryTerm) {
            score += 65;
            reason = "Predatory instant-loan phishing lure";
        }

        // --- Betting & Gambling Check ---
        boolean containsBettingTerm = compact.contains("bet") || compact.contains("casino") || compact.contains("gambling") || compact.contains("poker") || compact.contains("lottery") || compact.contains("dream11") || compact.contains("1xbet") || compact.contains("dafabet") || compact.contains("betway") || compact.contains("rummy");
        if (containsBettingTerm) {
            score += 70;
            reason = "Betting / online gambling link";
        }

        // --- Telegram Scam Check ---
        if (compact.contains("tme") || compact.contains("telegram")) {
            if (compact.contains("trading") || compact.contains("crypto") || compact.contains("signals") || compact.contains("double") || compact.contains("scam") || compact.contains("earn")) {
                score += 70;
                reason = "Suspicious Telegram scam channel link";
            }
        }

        // --- Religious Content Clean Mode ---
        if (religiousCleanEnabled) {
            if (compact.contains("missionary") || compact.contains("proselytize") || compact.contains("sect") || compact.contains("cult") || compact.contains("atheist") || compact.contains("convert") || compact.contains("heresy")) {
                score += 80;
                reason = "Religious clean mode filter";
            }
        }

        // === Expanded AI Scam Ad Detector (user vision) ===
        String lowerDomain = domain.toLowerCase(Locale.US);
        String compactNoSep = compact.replaceAll("[^a-z0-9]", "");

        // "You won ₹50,000" / lottery / prize money lures
        if (containsAny(compactNoSep, WIN_MONEY_LURES) && (compact.contains("win") || compact.contains("won") || compact.contains("prize") || compact.contains("claim"))) {
            score += 75;
            reason = "Fake prize / \"You won money\" scam lure";
        }

        // "Update KYC now" phishing
        if (containsAny(compact, KYC_UPDATE_LURES) || (lowerDomain.contains("kyc") && (lowerDomain.contains("update") || lowerDomain.contains("verify")))) {
            score += 72;
            reason = "Fake KYC / eKYC update phishing";
        }

        // Instant loan approved lures
        if (containsAny(compactNoSep, INSTANT_LOAN_LURES)) {
            score += 68;
            reason = "Predatory instant loan scam";
        }

        // "Install APK to claim reward"
        if (containsAny(compactNoSep, APK_CLAIM_LURES) || (compact.contains("apk") && (compact.contains("claim") || compact.contains("reward") || compact.contains("offer")))) {
            score += 78;
            reason = "Malicious APK download / reward claim scam";
        }

        // Fake courier / parcel tracking
        if (containsAny(compact, COURIER_FAKE) && (compact.contains("track") || compact.contains("delivery") || compact.contains("claim") || compact.contains("fee"))) {
            score += 65;
            reason = "Fake courier / parcel delivery scam";
        }

        // Fake government subsidy / yojana / sarkari
        if (containsAny(compact, GOV_SUBSIDY_FAKE) && (compact.contains("free") || compact.contains("claim") || compact.contains("apply") || compact.contains("get"))) {
            score += 70;
            reason = "Fake government subsidy / sarkari yojana scam";
        }

        // Fake investment / double your money schemes
        if (containsAny(compactNoSep, INVESTMENT_SCAM) && (compact.contains("double") || compact.contains("guarantee") || compact.contains("return") || compact.contains("profit"))) {
            score += 72;
            reason = "Fake investment / crypto / trading scam";
        }

        // Strengthen fake job detection
        if (containsAny(compact, FAKE_JOB) && containsAny(compactNoSep, new HashSet<>(Arrays.asList("earn", "salary", "income", "home", "parttime", "task")))) {
            score += 70;
            reason = "Fake job / work-from-home recruitment scam";
        }

        // UPI / Banking domain similarity (UPI & Banking Safety Shield)
        for (String official : OFFICIAL_BANK_DOMAINS) {
            String officialCompact = official.replace(".", "").replace("-", "");
            if (levenshteinDistance(compactNoSep, officialCompact) <= 2 && !compactNoSep.equals(officialCompact)) {
                score += 55;
                reason = "Bank/UPI look-alike domain (possible phishing)";
                break;
            }
        }

        // === On-device rule engine (fast regex + deterministic heuristics) ===
        // This is always available and extremely fast. Complements the TFLite model.
        try {
            String combinedForRules = domain + " " + (reason.isEmpty() ? "" : reason);
            com.clearguard.app.security.OnDeviceRuleEngine.ClassificationResult ruleRes =
                    com.clearguard.app.security.OnDeviceRuleEngine.INSTANCE.classify(combinedForRules, domain);

            if (ruleRes.isPhishing() || ruleRes.getScore() >= 55) {
                score += (int) (ruleRes.getScore() * 0.35);  // blend with existing heuristics
                if (reason.isEmpty() || ruleRes.getScore() > 70) {
                    reason = ruleRes.getLabel();
                }
            }
        } catch (Exception ignored) {}  // rule engine must never break the detector

        // Optional small TFLite phishing classifier (if enabled and model present in assets)
        if (contextForTFLite != null && useTFLiteIfAvailable) {
            try {
                com.clearguard.app.security.PhishingClassifier.PhishingResult mlRes =
                        com.clearguard.app.security.PhishingClassifier.INSTANCE.classify(
                                contextForTFLite, domain + " " + reason, domain, true, null, null);
                if (mlRes.getUsedML()) {
                    int mlPoints = (int) (mlRes.getPhishingProbability() * 42);
                    score += mlPoints;
                    if (mlRes.getPhishingProbability() > 0.65 && reason.length() < 60) {
                        reason = mlRes.getLabel();
                    }
                }
            } catch (Exception ignored) {}
        }

        // === Dedicated Indian Scam Shield checks (exact categories requested) ===
        if (indianScamShieldEnabled) {
            // 1. UPI KYC scam
            if (containsAny(compact, KYC_UPDATE_LURES) || (lowerDomain.contains("kyc") && (compact.contains("upi") || compact.contains("pay") || compact.contains("refund") || compact.contains("verify")))) {
                if (containsUpiBrand || containsAny(compactNoSep, new HashSet<>(Arrays.asList("paytm", "phonepe", "gpay", "bhim", "sbi", "hdfc", "icici")))) {
                    score += 82;
                    reason = "UPI KYC scam";
                }
            }

            // 2. Fake electricity bill payment
            if (containsAny(compactNoSep, ELECTRICITY_BILL_SCAM) && (compact.contains("pay") || compact.contains("bill") || compact.contains("due") || compact.contains("pending") || compact.contains("fee"))) {
                score += 78;
                reason = "Fake electricity bill payment";
            }

            // 3. Fake courier delivery fee
            if (containsAny(compact, COURIER_FAKE) && (compact.contains("fee") || compact.contains("delivery") || compact.contains("pay") || compact.contains("claim") || compact.contains("track"))) {
                score += 76;
                reason = "Fake courier delivery fee";
            }

            // 4. Fake loan approval (refined)
            if (containsAny(compactNoSep, INSTANT_LOAN_LURES) || (containsAny(compact, new HashSet<>(Arrays.asList("loan", "credit", "rupee"))) && containsAny(compactNoSep, new HashSet<>(Arrays.asList("approved", "sanction", "disburse", "instant", "quick"))))) {
                score += 75;
                reason = "Fake loan approval";
            }

            // 5. Fake job registration fee
            if (containsAny(compact, FAKE_JOB) && (containsAny(compactNoSep, JOB_REG_FEE) || containsAny(compact, new HashSet<>(Arrays.asList("fee", "pay", "registration", "apply"))))) {
                score += 77;
                reason = "Fake job registration fee";
            }

            // 6. Fake government scheme
            if (containsAny(compact, GOV_SUBSIDY_FAKE) || containsAny(compactNoSep, GOV_SCHEME_FAKE)) {
                if (compact.contains("free") || compact.contains("claim") || compact.contains("apply") || compact.contains("get") || compact.contains("register")) {
                    score += 74;
                    reason = "Fake government scheme";
                }
            }

            // 7. Fake investment group
            if (containsAny(compactNoSep, INVESTMENT_SCAM) && (compact.contains("group") || compact.contains("telegram") || compact.contains("whatsapp") || compact.contains("double") || compact.contains("guaranteed") || compact.contains("profit"))) {
                score += 76;
                reason = "Fake investment group";
            }

            // 8. Fake APK download (already strong, refine label)
            if (containsAny(compactNoSep, APK_CLAIM_LURES) || (compact.contains("apk") && (compact.contains("claim") || compact.contains("reward") || compact.contains("install")))) {
                score += 80;
                reason = "Fake APK download";
            }

            // 9. Fake customer care number
            boolean hasCare = containsAny(compactNoSep, CUSTOMER_CARE_FAKE);
            boolean nearBankOrPayment = containsAny(compact, new HashSet<>(Arrays.asList("bank", "sbi", "hdfc", "icici", "paytm", "phonepe", "refund", "kyc", "loan")));
            if (hasCare && (nearBankOrPayment || compact.contains("1800") || compact.contains("toll") || compact.contains("helpline"))) {
                score += 73;
                reason = "Fake customer care number";
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

        // Mode-specific strictness boosts (Intent-Based Blocking)
        int modeBoost = 0;
        String modeReason = "";
        if (protectionMode != null) {
            String m = protectionMode.toLowerCase(Locale.US);
            if ("elder".equals(m) || "kids".equals(m)) {
                modeBoost = 18;
                modeReason = " (elevated by " + ( "elder".equals(m) ? "Elder Safe Search" : "Kids Mode" ) + ")";
            } else if ("spiritual".equals(m)) {
                modeBoost = 12;
                modeReason = " (Spiritual/Satvik strictness)";
            } else if ("shopping".equals(m)) {
                if (compact.contains("offer") || compact.contains("deal") || compact.contains("discount") || compact.contains("sale")) {
                    modeBoost = 10;
                    modeReason = " (Shopping Mode fake offer filter)";
                }
            } else if ("study".equals(m) || "work".equals(m)) {
                if (compact.contains("shorts") || compact.contains("reel") || compact.contains("recommend")) {
                    modeBoost = 8;
                }
            }
        }
        score += modeBoost;
        if (!modeReason.isEmpty() && reason.isEmpty()) {
            reason = "mode policy" + modeReason;
        } else if (!modeReason.isEmpty()) {
            reason = reason + modeReason;
        }

        int boundedScore = Math.min(score, 100);
        int effectiveThreshold = BLOCK_THRESHOLD;
        if ("elder".equals(protectionMode) || "kids".equals(protectionMode)) {
            effectiveThreshold = 55; // stricter for vulnerable users
        }
        if (boundedScore >= effectiveThreshold) {
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

    private static boolean containsAny(String text, Set<String> terms) {
        if (text == null || terms == null) return false;
        String t = text.toLowerCase(Locale.US);
        for (String term : terms) {
            if (t.contains(term.toLowerCase(Locale.US))) return true;
        }
        return false;
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
