package com.clearguard.app.security

import com.clearguard.app.vpn.ClearGuardVpnService

/**
 * Turns a raw DNS/call event into a plain-language explanation a non-technical user can act on:
 * what kind of thing was blocked, why it matters, and what to do about it.
 *
 * Most competitors surface only a bare "spam"/"blocked" label or a raw query log. Pairing an
 * honest, human explanation with one-tap recovery (allow-once / allow-always / report) at the
 * moment of the block is the differentiator — and it's what makes aggressive filtering such as
 * Elder Mode safe to ship, because a false positive is always one tap from being undone.
 *
 * Pure and on-device. Keyed off the reason strings the VPN service already builds, so it needs
 * no extra state and never touches the network.
 */
object BlockExplainer {

    enum class Severity { HIGH, MEDIUM, LOW, INFO }

    data class Explanation(
        val category: String,
        val severity: Severity,
        val headline: String,
        val detail: String,
        val advice: String,
        /** Surface India's official 1930 / cybercrime.gov.in reporting channel. */
        val showHelpline: Boolean,
        /** Heuristic blocks where a false positive is plausible — nudge toward allow-once. */
        val mayBeFalsePositive: Boolean
    )

    fun explain(query: ClearGuardVpnService.BlockedQuery): Explanation {
        val reason = query.reason
        val lower = reason.lowercase()
        val score = query.threatScore

        // Allowed / informational — the inspector also opens for clean resolves.
        if (!query.blocked) {
            return Explanation(
                category = "Allowed",
                severity = Severity.INFO,
                headline = "This request was allowed through.",
                detail = "ShieldDNS resolved this domain normally — no ad, tracker, or scam rule matched it.",
                advice = "If you consider this domain unwanted, you can block it.",
                showHelpline = false,
                mayBeFalsePositive = false
            )
        }

        // High-risk phone / call events (logged with a "phone:" pseudo-domain).
        if (query.domain.startsWith("phone:") || lower.contains("high-risk phone")) {
            return Explanation(
                category = "Scam call",
                severity = Severity.HIGH,
                headline = "A high-risk caller was screened.",
                detail = "This number scored high on the on-device fraud-risk check. Scam and " +
                    "\"digital arrest\" calls in India almost always come from numbers like this.",
                advice = "Never share OTPs, pay a \"fine\", or stay on a video call with anyone " +
                    "claiming to be police / CBI / customs. No agency arrests you over a call.",
                showHelpline = true,
                mayBeFalsePositive = false
            )
        }

        // Scam / phishing (Indian Scam Shield + generic scam shield).
        if (query.status == "threat" && lower.contains("scam")) {
            val isIndian = lower.contains("indian scam shield")
            val (category, detail) = scamCategoryAndDetail(lower)
            return Explanation(
                category = if (isIndian) "Indian Scam Shield" else category,
                severity = Severity.HIGH,
                headline = "Blocked a $category.",
                detail = detail,
                advice = "Do not enter passwords, OTPs, card or UPI details here. Verify only " +
                    "through the official app or a number you already trust — never a link.",
                showHelpline = true,
                // Mid-confidence scam scores (below the high-certainty band) can over-trigger.
                mayBeFalsePositive = score in 1..74
            )
        }

        // Known malicious domain from a curated threat-intelligence list.
        if (query.status == "threat" && lower.contains("security blocklist")) {
            return Explanation(
                category = "Known threat",
                severity = Severity.HIGH,
                headline = "Blocked a known malicious domain.",
                detail = "This domain is on a curated threat-intelligence list (malware, phishing, " +
                    "or command-and-control infrastructure).",
                advice = "Best left blocked. Allow it only if you are certain it is safe.",
                showHelpline = false,
                mayBeFalsePositive = false
            )
        }

        // Encrypted-DNS bypass attempt refused by the bypass guard.
        if (query.status == "bypass" || lower.contains("bypass guard")) {
            return Explanation(
                category = "DNS bypass blocked",
                severity = Severity.MEDIUM,
                headline = "An app tried to bypass filtering.",
                detail = "An app or browser tried to use its own encrypted DNS to sidestep " +
                    "ShieldDNS. The attempt was refused so filtering keeps working.",
                advice = "This is normal and keeps protection in effect. Allow it only if a " +
                    "specific app breaks because of it.",
                showHelpline = false,
                mayBeFalsePositive = true
            )
        }

        // Firewall / mode-policy blocks.
        if (lower.startsWith("firewall:")) {
            return firewallExplanation(lower)
        }

        // On-device ad-pattern detector.
        if (lower.contains("ai ad pattern")) {
            return Explanation(
                category = "Ad / tracker pattern",
                severity = Severity.LOW,
                headline = "Blocked a likely ad or tracker.",
                detail = "The on-device pattern detector flagged this host as an advertising or " +
                    "analytics endpoint.",
                advice = "Safe to keep blocked. If a site misbehaves, allow it for an hour.",
                showHelpline = false,
                mayBeFalsePositive = true
            )
        }

        // Default: plain blocklist rule (ads / trackers).
        return Explanation(
            category = "Ads & trackers",
            severity = Severity.LOW,
            headline = "Blocked by a filter list.",
            detail = "This domain is on one of your active ad / tracker blocklists.",
            advice = "Safe to keep blocked. If a page is broken, allow it for an hour or always.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
    }

    /** Maps the scam reason text to a friendly category label + a one-line explanation. */
    private fun scamCategoryAndDetail(lowerReason: String): Pair<String, String> = when {
        lowerReason.contains("digital arrest") || lowerReason.contains("fake authority") ->
            "digital-arrest scam" to
                "Impersonates CBI / police / customs and threatens arrest over a fake parcel or " +
                "case to keep you paying on a video call. This is a fraud — no agency does this."
        lowerReason.contains("upi kyc") ->
            "fake UPI-KYC scam" to
                "Claims your UPI / bank KYC is incomplete to capture your UPI PIN or OTP. Banks " +
                "never ask you to \"complete KYC\" through a link."
        lowerReason.contains("electricity bill") ->
            "fake electricity-bill scam" to
                "Threatens to cut your power unless you pay through a link. Real electricity " +
                "boards do not send pay-or-disconnect links."
        lowerReason.contains("courier") ->
            "fake courier-fee scam" to
                "Asks for a small \"delivery\" or \"customs\" fee for a parcel you didn't order, " +
                "to capture your card details."
        lowerReason.contains("loan") ->
            "predatory loan scam" to
                "Promises an instant, pre-approved loan to harvest your documents and an upfront fee."
        lowerReason.contains("job") ->
            "fake-job scam" to
                "Offers a work-from-home job but demands a registration or joining fee first."
        lowerReason.contains("government scheme") || lowerReason.contains("subsidy") || lowerReason.contains("yojana") ->
            "fake government-scheme scam" to
                "Impersonates a sarkari yojana or subsidy to collect Aadhaar and bank details."
        lowerReason.contains("investment") || lowerReason.contains("trading") ->
            "fake investment scam" to
                "Promises guaranteed or doubled returns, usually via a Telegram / WhatsApp \"tips\" group."
        lowerReason.contains("apk") ->
            "malicious-app (APK) scam" to
                "Pushes you to install an app from outside the Play Store that can read your OTPs " +
                "and drain your bank account."
        lowerReason.contains("customer care") ->
            "fake customer-care scam" to
                "A fake \"helpline\" / toll-free number that takes control of your phone or " +
                "payments once you call it."
        lowerReason.contains("festival") || lowerReason.contains("seasonal offer") ->
            "festival-offer scam" to
                "A fake Diwali / sale \"lucky draw\", gift, or free-recharge lure that spikes around festivals."
        lowerReason.contains("look-alike") || lowerReason.contains("impersonation") || lowerReason.contains("brand") ->
            "bank / brand look-alike site" to
                "The domain mimics a real bank, UPI app, or brand to trick you into logging in."
        lowerReason.contains("betting") || lowerReason.contains("gambling") ->
            "betting / gambling link" to
                "An online betting or gambling site."
        lowerReason.contains("dga") || lowerReason.contains("machine-generated") ->
            "suspicious auto-generated domain" to
                "The domain name looks machine-generated, a pattern common in malware infrastructure."
        else ->
            "scam / phishing attempt" to
                "This domain matched on-device scam and phishing patterns."
    }

    /** Explanations for the various "Firewall: ..." policy blocks. */
    private fun firewallExplanation(lowerReason: String): Explanation = when {
        lowerReason.contains("elder") -> Explanation(
            category = "Elder Safe filter",
            severity = Severity.LOW,
            headline = "Blocked by Elder Mode.",
            detail = "Elder Mode blocks adult, gambling, and scam-adjacent sites on top of the " +
                "full Indian Scam Shield to protect against fraud aimed at older users.",
            advice = "If this site is genuinely needed, allow it for an hour or always.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("kids") || lowerReason.contains("family") -> Explanation(
            category = "Family filter",
            severity = Severity.LOW,
            headline = "Blocked by Family / Kids Mode.",
            detail = "This site matched the adult, gambling, or age-inappropriate category.",
            advice = "Allow it for an hour or always if you intend it to be reachable.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("strict") || lowerReason.contains("study") || lowerReason.contains("work") -> Explanation(
            category = "Focus filter",
            severity = Severity.LOW,
            headline = "Blocked for focus.",
            detail = "Study / Work Mode hides social widgets, fingerprinting, and distraction trackers.",
            advice = "Allow it for an hour if you need this site while focusing.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("spiritual") || lowerReason.contains("satvik") -> Explanation(
            category = "Satvik clean filter",
            severity = Severity.LOW,
            headline = "Blocked by Spiritual / Satvik Mode.",
            detail = "Removes gambling, vulgar, dating, and similar content.",
            advice = "Allow it for an hour or always to override.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("data saver") -> Explanation(
            category = "Data saver",
            severity = Severity.INFO,
            headline = "Blocked to save data.",
            detail = "This is a heavy ad / video-ad network. Blocking it cuts mobile data usage.",
            advice = "Allow it only if some content fails to load.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("country block") -> Explanation(
            category = "Country filter",
            severity = Severity.INFO,
            headline = "Blocked by your country rule.",
            detail = "This domain's country code is on your blocked-countries list.",
            advice = "Allow it for an hour or always if it's a site you trust.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("india regional") -> Explanation(
            category = "India regional pack",
            severity = Severity.LOW,
            headline = "Blocked by the India regional ad pack.",
            detail = "Matched a cricket-stream, regional e-com, or popup ad network in the India pack.",
            advice = "Allow it for an hour if a stream or page breaks.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("meta") || lowerReason.contains("instagram") -> Explanation(
            category = "Meta tracker pack",
            severity = Severity.LOW,
            headline = "Blocked a Meta / Instagram ad or tracker host.",
            detail = "A Facebook/Instagram ad-network, analytics, or tracking-pixel endpoint. (Instagram's " +
                "own in-app video ads can't be blocked here — they share servers with real posts.)",
            advice = "Safe to keep blocked. If a website's \"Log in with Facebook\" button stops working, allow it for an hour.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("quiet hours") -> Explanation(
            category = "Quiet hours",
            severity = Severity.INFO,
            headline = "Blocked during quiet hours.",
            detail = "Social trackers are blocked late at night by your time rule.",
            advice = "Allow it for an hour to override tonight.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("background app") -> Explanation(
            category = "Background-data block",
            severity = Severity.INFO,
            headline = "Blocked background data.",
            detail = "An app tried to reach the network while it wasn't in the foreground.",
            advice = "Allow it if a background sync you rely on stops working.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        lowerReason.contains("youtube") -> Explanation(
            category = "YouTube ad block",
            severity = Severity.LOW,
            headline = "Blocked a YouTube ad host.",
            detail = "A known YouTube ad-serving host was blocked on Wi-Fi.",
            advice = "Allow it for an hour if playback breaks.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
        else -> Explanation(
            category = "Per-app firewall",
            severity = Severity.INFO,
            headline = "Blocked by your firewall rule.",
            detail = "You blocked this app's internet access (all networks, Wi-Fi, or mobile data).",
            advice = "Allow it for an hour or always to let this app connect.",
            showHelpline = false,
            mayBeFalsePositive = true
        )
    }
}
