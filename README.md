# ClearGuard

ClearGuard is an Android DNS-level ad and tracker blocker inspired by tools like AdGuard and AdAway. It uses Android's `VpnService` to become the device DNS resolver, checks each DNS question against local host lists, and returns a local blocked response for matching domains.

## Features

**ClearGuard** is built around the "Indian-first privacy + fraud protection" vision with **Intent-Based Blocking Modes** and deep on-device scam detection.

### Core + Vision Features Implemented
- **Intent-Based Protection Modes** (Study / Work / Kids / Elder Safe Search / Shopping / Spiritual (Satvik/Dharma Clean) / Battery Saver + Default). Each mode changes DNS rules, scam thresholds, distraction blocking, and DoH/cache behavior.
- **Indian Scam Shield (Dedicated)** — first-class on-device protection specifically against the 9 most common India-specific scams:
  - UPI KYC scam
  - Fake electricity bill payment
  - Fake courier delivery fee
  - Fake loan approval
  - Fake job registration fee
  - Fake government scheme
  - Fake investment group
  - Fake APK download
  - Fake customer care number
  Toggleable independently in Settings. When active, blocks are labeled "Indian Scam Shield: ..." in Activity and recent threats.
  - **Scam Screenshot Scanner** (Privacy tab → Scanner): Upload any screenshot of a suspicious ad, SMS, WhatsApp forward, or website. On-device ML Kit OCR + the same Indian Scam Shield keyword heuristics instantly detect Fake reward, Fake KYC, Fake payment, Fake investment, Fake job, Fake customer support, and Fake APK link. Shows matched snippets, confidence, and lets you one-tap block any domains found in the image. Completely private (no cloud, no upload).
- **Fake Customer Care Number Blocker**: In the built-in Browser, "Phone Shield" automatically scans pages for phone numbers shown next to bank/UPI/airline/courier/electricity "support" language and shows prominent warnings ("This number may not be official. Verify before calling.").
- **Dark Pattern Blocker**: Toggle + automatic + "Clean this page" button hides forced popups, confirm-shaming buttons, auto-checked subscriptions, tricky cookie "accept all", and subscription cancellation dark patterns.
- **"Clean My Website" Button**: Prominent button in the browser toolbar instantly strips ads, floating videos, sticky headers, sidebars, newsletter popups, cookie banners, recommended articles, and empty ad spaces. Combines with the existing per-site memory cleaner.

- **Anti-Adblock Bypass Tools** (Browser shields row, advanced/opt-in):
  - Scriptlet injection (lightweight uBO-style scriptlets to defeat detection)
  - Anti-adblock defuser (neutralizes common "adblock detected" JS)
  - Popup trap blocker (blocks forced popups and window.open traps)
  - Redirect-chain cleaner (detects and warns on ad-wall redirect chains)
  - Anti-paywall warning (**warning only** — does not attempt to bypass paywalls)
  - Sponsored-widget remover (hides "sponsored", "promoted", and recommendation widgets)
  - Fake countdown remover (removes fake "wait X seconds" timers and gates)
  - Overlay remover (strips full-page blocking overlays used by anti-adblock scripts)
  These are intentionally off by default and aimed at power users who run into detection on certain sites. Best used together with the "Clean this page" button and Dark Pattern Blocker.
- **Personal Block AI**: In Blocklists screen, type natural language like "Block all betting ads and loan ads" and it intelligently expands and adds matching custom block rules (betting sites, loan domains, etc.).
- **Filter Conflict Detector** (Blocklists screen): Dedicated tool to detect rule conflicts and redundancies. Run the detector to find:
  - Custom block vs allowlist (allow wins, but conflicting user intent).
  - Security (threat) block vs allowlist (potentially dangerous override).
  - Redundant custom block already covered by downloaded filter lists.
  - Overlap between custom blocks and security blocks (wasted duplicate rule).
  Each conflict shows clear description + resolution buttons (e.g. "Remove from allowlist"). Helps keep your filter configuration clean and predictable. Runs on-demand and uses your active user rules + downloaded hosts.
- **On-device Rule Engine + Small TFLite Phishing Classifier** (advanced, opt-in):
  - Fast deterministic rule engine (pre-compiled regex + heuristics) for phishing/scam text and URLs. Always runs locally and extremely quickly.
  - Optional tiny TensorFlow Lite model (`assets/phishing_model.tflite` — user-provided) for additional ML-based phishing probability.
  - Integrated into ScamDetector (DNS queries), Scam Screenshot Scanner (post-OCR), and the in-app Browser (page title + URL classification with risk banner when probability is high).
  - Toggleable independently in Settings. When both are enabled, results use an ensemble (rule engine 65% + TFLite 35%).
  - Fully on-device. The rule engine alone is already highly effective for most Indian and general phishing patterns.

**Roadmap Features (partially implemented on-device core + stubs):**
- **Mobile Number Risk Scoring API (FRI + operator signals)** [STARTED - full API + demo]:
  - `MobileRiskScoringApi` (in OnDeviceRuleEngine):
    - `queryRisk(ctx, phone, context?)` + batch `queryBatchRisk` for "at scale".
    - Local-first heuristic (enhanced for all table categories) + extensible `LOCAL_FRI_BAD_PATTERNS` + `addToLocalRiskDB`.
    - Optional remote operator/FRI signals (OkHttp stub; gated by `KEY_MOBILE_RISK_REMOTE_SIGNALS`).
    - Returns rich `RiskResult` (score, isHighRisk, signals, recommendedAction="WARN_OR_BLOCK", explanation).
  - "Block/vet high-risk numbers at scale": Drives scanner detections, browser warnings, security block suggestions.
  - Live interactive demo in EnterpriseScreen (input phone → calls the API (local + simulated remote) → full result). Includes "Report to DB" and "Vet & Block" actions.
  - Toggles in Settings. Real small assets/fri_risk_db.txt loader + auto-seed on app start (loadLocalFRIDB).
  - Wired into custom block auto-suggest (BlocklistsScreen shows suggested high-risk phones from DB with one-tap add to security blocks).
  - Integrated with existing phone heuristics in scanner/browser/multi-modal. SMS/call "incoming" currently via scanner (real receiver is future work).
  - Risk API used for "block/vet at scale" recommendations.
  - Realtime fully implemented and continued: SMS receiver (goAsync + full queryRisk w/ enhanced remote: real HMAC-SHA256 hash, configurable endpoint from prefs, better error handling/logging/fallback). CallScreeningService (full user flow: screen + notif + actions). Auto-report to federated intel + Edge fabric (adds to shared local signal cache feeding risk API in phoneRiskScore). Integrated BankingGateway for UPI in SMS body (realtime payee verify + delay rec in RiskResult). SMS tweaks: abortBroadcast() for high-risk. RASP hardened (extra Frida/Xposed/Substrate detection). Edge Threat fabric as local signal cache (feeds risk). Deeper BankingGateway (realtime verify in calls/SMS context, transaction delay rec "DELAY_TRANSACTION_30S"). Wired risk deeper into VPN activity logs and custom block auto-suggest.
  - Full SMS receiver (SmsRiskReceiver) added for true incoming SMS tagging: on high-risk phone (using the FRI API + local DB), logs, auto-seeds DB, and can trigger warnings/notifications (requires RECEIVE_SMS/READ_SMS perms; registered in manifest with high priority). This enables real "tag before transaction" for SMS without relying only on screenshots.
- Multi-modal Phishing Engine: Now covers SMS/WhatsApp/URL/QR (via UPI parse + risk in screenshot analyzer) + voice text stubs. Uses expanded OnDeviceRuleEngine.multiModalClassify + TFLite.
- **Banking Gateway Integration (NPCI/Bank APIs)** [STARTED]:
  - New `BankingGateway` object with `verifyPayee(vpa, amount?, context?)` → PayeeVerification (verifiedName from stub cache, riskScore, isVerified, recommendedAction like "BLOCK_TRANSACTION", explanation).
  - Local "verified payees" stub + risk (builds on UPI parser). Remote stub for real NPCI/bank APIs.
  - Live demo in EnterpriseScreen: input VPA/amount → verify + block rec.
  - Integrated in browser (enhanced UPI banner now uses full gateway verification) and scanner (UPI links in screenshots get Banking Gateway Detections with action).
  - "Real-time payee verification and transaction blocking": High risk/action recommends block/delay before payment. On-device first, with API stub for scale.
- Voice-phishing (vishing) detection: Stub in rule engine for transcribed prompts ("press 1", "share OTP"). Full ASR needs on-device model.
- Federated Threat Intelligence: Design stub - local crowdsource hashes, anonymized sharing (privacy-first, no backend yet).
- Explainable regional alerts: Added getRegionalExplanation() returning Hindi/Bundeli microcopy + score in scanner results and browser banners.
- RASP + Anti-tamper: Basic stub (RaspGuard.kt) with root/emulator/hook/debug checks + integrity report. Call on start if enabled. Full production RASP is native/high complexity.
- Runtime checks integrated in MainActivity when KEY_RASP_ENABLED.
- **AI Scam Ad Detector + UPI & Banking Safety Shield** (general layer) — on-device heuristics for "You won ₹X", fake KYC, instant loan lures, courier tracking, government subsidy fakes, "install APK" rewards, investment scams, job fraud, and bank/UPI domain lookalikes (SBI, HDFC, Paytm, PhonePe, GPay, etc.). Indian Scam Shield adds precision and dedicated categories on top.
- **Spiritual / Satvik / Dharma Clean Mode** — special strictness on gambling, vulgar, political, dating, and proselytizing content, especially valuable on scripture and bhakti sites.
- **Regional Indian Filter Packs** — cricket streaming, Hinglish/Hindi site ads, sarkari, loan apps, e-commerce trackers (built-in + toggleable).
- **App Privacy Score + Tracker Company Map** ("These companies tracked you today": Google, Meta, Amazon, Indian Martech, Payments, etc.) surfaced in Privacy → App Audit.
- **Explain This Blocked Domain** — tap any recent block in Activity / Live Monitor for plain-English category + reason (Advertising, Phishing, Scam, Brand Impersonation, etc.).
- **Fake Download Button Blocker + AI Website Cleaner + Clean Reading** hooks in the built-in hardened Browser (WebView + JS element picker + per-site memory + Hindi/cricket specific cleaners + cookie/anti-fingerprint shields).
- Existing strong foundation: DNS VPN (no root), DoH, massive blocklist support, per-app exclusions, Scam + DGA, Quick Settings tile, beautiful liquid-glass Compose UI.

- Local VPN DNS filtering with no root requirement.
- Hosts-style, dnsmasq, and DNS-safe AdGuard/uBlock Origin/Brave filter parsing for bundled, downloaded, and custom rules.
- Manual blocklist updates from AdAway, StevenBlack, AdGuard DNS, uBlock Origin, Brave, OISD, HaGeZi, URLhaus, Phishing Army, NoCoin, Dandelion Sprout, PhishTank/OpenPhish, and Peter Lowe sources, plus optional daily auto-updates via WorkManager (network- and battery-aware, off switch in Settings).
- Add, remove, and pause individual filter source URLs in the app with named list rows and slider toggles.
- Custom block and allow overrides managed in the app, applied live without restarting the VPN.
- Recent Activity view of domains blocked this session, with one-tap allowlisting (in-memory only).
- Local aggregate counters for blocked and allowed DNS queries.
- **Encrypted DNS (DoH)** with automatic fallback — quick-select from popular trusted providers (Quad9, Cloudflare, Mullvad, AdGuard) or enter a custom endpoint. Runs on a pooled OkHttp HTTP/2 client with built-in bootstrap IPs for known providers, so repeated queries reuse one TLS connection instead of paying a handshake each time. Classic UDP fallback for maximum compatibility.
- **Concurrent DNS engine** — queries are handled on a bounded worker pool, so one slow upstream lookup never stalls the rest of the device's DNS traffic.
- **Threat Shield** (on-device): Scam/phishing heuristics + DGA entropy detection for algorithmically generated malware command-and-control domains.
- **Bypass Guard**: answers known app-private DoH endpoints (Google DNS, Cloudflare, NextDNS, OpenDNS, ...) and Mozilla's auto-DoH canary with SERVFAIL, so browsers and apps cannot silently route around the filter; the user's own selected DoH provider is exempted.
- **Quick Settings tile**: toggle protection straight from the notification shade.
- **Resume after reboot**: protection restarts automatically on boot while VPN consent is still granted (toggleable).
- HTTPS-only blocklist downloads, enforced in both the UI and the downloader.
- **Liquid-glass UI with full dark mode**: System / Light / Dark appearance picker, theme-aware glass palettes, and edge-to-edge layout on Android 15+. All design tokens are centralized and documented in [docs/UI-CONFIGURATION.md](docs/UI-CONFIGURATION.md).
- **Per-app exclusions (split tunneling)**: pick apps in Settings whose traffic bypasses the VPN entirely — useful for banking apps or captive-portal logins. Applied with an in-place VPN restart.
- **Top blocked domains**: on-device per-domain block counters surfaced in Statistics, persisted across restarts.
- **Backup & restore**: export all sources, rules, exclusions, and settings to a JSON file and import them on any device, via the system file picker. Imported entries are re-validated and re-normalized.
- Configurable upstream (DoH endpoint or classic IPv4) and in-memory DNS cache duration.
- Battery-conscious design: DNS-only route, in-memory DNS cache, pooled DoH connections, no wakelocks; the only scheduled work is the optional once-a-day blocklist refresh.
- Transparent privacy posture: no analytics SDKs, no accounts, no remote app logs.

## Limits

ClearGuard is DNS-level protection. It does not do HTTPS interception, cosmetic page filtering, per-app firewalling, root hosts-file editing, or browser extension filtering. Some first-party ads served from the same domain as content cannot be blocked safely at DNS level.

## Build

Open this folder in Android Studio with JDK 17 and Android SDK 36 installed, then run the `app` configuration. The project uses Kotlin 2.3 with the Compose compiler plugin, AGP 8.13, and a Gradle version catalog (`gradle/libs.versions.toml`); build with Gradle 8.13 or newer.

Command line build, if Gradle is installed:

```powershell
gradle :app:assembleDebug
```

This workspace does not include a Gradle wrapper because one could not be generated in this environment.

## Download APK From GitHub

The GitHub Actions workflow at `.github/workflows/android-apk.yml` builds `ClearGuard-debug.apk` on every push to `main` and publishes it to the `latest` GitHub Release.

After pushing to GitHub, download the APK from:

```text
https://github.com/<owner>/<repo>/releases/tag/latest
```

## Main Files

- `app/src/main/java/com/clearguard/app/vpn/ClearGuardVpnService.java`: DNS VPN service with a concurrent query pool.
- `app/src/main/java/com/clearguard/app/vpn/DohResolver.kt`: pooled HTTP/2 DNS-over-HTTPS client with provider bootstrap IPs.
- `app/src/main/java/com/clearguard/app/vpn/DnsPacket.java`: IPv4/UDP packet wrapping.
- `app/src/main/java/com/clearguard/app/vpn/DnsMessage.java`: DNS question parsing and blocked responses.
- `app/src/main/java/com/clearguard/app/blocking/HostBlocker.java`: hosts parsing and allow/block matching.
- `app/src/main/java/com/clearguard/app/blocking/BlocklistUpdater.java`: HTTPS blocklist downloads.
- `app/src/main/java/com/clearguard/app/blocking/BlocklistUpdateWorker.kt`: daily WorkManager auto-update job.
- `app/src/main/java/com/clearguard/app/security/ScamDetector.java`: on-device threat scoring (scam heuristics + DGA entropy detection).
- `app/src/main/java/com/clearguard/app/security/DnsBypassGuard.java`: known encrypted-DNS bypass endpoints answered with SERVFAIL.
- `app/src/main/java/com/clearguard/app/ClearGuardTileService.kt`: Quick Settings toggle tile.
- `app/src/main/java/com/clearguard/app/BootReceiver.kt`: resumes protection after reboot.
- `app/src/main/java/com/clearguard/app/MainActivity.kt`: Compose UI shell and navigation.
- `app/src/main/java/com/clearguard/app/ui/screens/ActivityScreen.kt`: recent blocked domains with one-tap allow.
- `app/src/main/java/com/clearguard/app/ui/screens/BlocklistsScreen.kt`: filter sources and custom block/allow rules.
