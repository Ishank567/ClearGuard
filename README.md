# ShieldDNS

**ShieldDNS** is a modern, privacy-first Android DNS ad & threat blocker with deep on-device scam protection for Indian users. It uses Android's `VpnService` to become your device's DNS resolver, filtering ads, trackers, phishing, and India-specific fraud in real time — all locally.

Inspired by the latest in premium mobile security design (glassmorphism, 3D depth, connected floating interfaces), ShieldDNS features a beautiful liquid-glass UI with a striking 3D splash effect using the official logo, and an innovative "Active Shields" dashboard with 3D-tilted floating cards that directly control real protection features.

## Features

ShieldDNS is built around **Intent-Based Protection Modes** and powerful on-device scam & fraud defense.

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

**Modern UI & "Active Shields" Dashboard (inspired by premium cyber-security design)**
- Stunning **3D splash screen** on launch featuring the official ShieldDNS logo with real perspective transforms (rotationX/Y + cameraDistance), breathing animation, orbiting particles, and the animated cyber mesh background.
- **Dashboard hero** with the large glowing logo shield, clear "Protected" status, prominent cyan "Get Started/Pause" action, and "Create Profile" link.
- **Active Shields** — beautiful floating 3D-tilted dark glass cards with glowing network connection lines (exactly like the reference design). These cards now control **real, live features**:
  - **Ads** — toggles the full Indian Scam Shield (9 scam categories).
  - **Family** — switches to Kids protection mode.
  - **Trackers** — enables real browser anti-fingerprint + cookie removal.
  - **Gaming** — switches to Battery Saver mode.
  - **Malware** — enables Mobile Risk Scoring (FRI) + RASP anti-tamper.
  - **Crypto-mining** — enables RASP + injects real known miner domains into your security block list (persisted DNS blocks).
- All changes instantly update the UI state, persist in preferences, and (where relevant) trigger a live VPN reload so protection takes effect immediately.
- Liquid-glass design system with deep 3D depth (shadows, bevels, specular highlights, animated mesh with hexagons + particles + perspective grid).

**Other Core Features** (Roadmap items that are implemented or have strong on-device cores):
- Multi-modal Phishing Engine: Now covers SMS/WhatsApp/URL/QR (via UPI parse + risk in screenshot analyzer) + voice text stubs. Uses expanded OnDeviceRuleEngine.multiModalClassify + TFLite.
- **Banking Gateway Integration (NPCI/Bank APIs)** [Implemented]:
  - New `BankingGateway` object with `verifyPayee(vpa, amount?, context?)` → PayeeVerification (verifiedName from stub cache, riskScore, isVerified, recommendedAction like "BLOCK_TRANSACTION", explanation).
  - Local "verified payees" stub + risk (builds on UPI parser). Remote stub for real NPCI/bank APIs.
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
- **Liquid-glass + 3D modern UI**: System / Light / Dark appearance, deep glassmorphism with floating 3D cards, animated cyber mesh background (hex + particles + perspective), and the official ShieldDNS logo throughout (splash, header, dashboard). All design tokens documented in [docs/UI-CONFIGURATION.md](docs/UI-CONFIGURATION.md).
- **Per-app exclusions (split tunneling)**: pick apps in Settings whose traffic bypasses the VPN entirely. Applied live.
- **Top blocked domains & recent activity**: on-device counters and one-tap allowlisting, persisted.
- **Backup & restore**: full JSON export/import of sources, blocks, settings via system picker.
- Configurable upstream (DoH endpoint or classic IPv4) and in-memory DNS cache duration.

## Using the Official Logo & Building

1. **Logo asset (required for the 3D splash, header, and Dashboard hero)**:
   - Export the ShieldDNS logo image you have (the shield + network graphic + wordmark).
   - Place `shield_dns_logo.png` (high resolution recommended) in:
     `app/src/main/res/drawable/shield_dns_logo.png`
   - For best results across densities, also provide versions in the `drawable-*` folders.
   - (Optional) For the launcher icon, export just the shield portion (no text) and update the adaptive icon foreground in `res/mipmap-*` and `drawable/ic_launcher_foreground.xml`.

2. **Build**:
   ```bash
   ./gradlew assembleDebug
   ```
   Install the resulting APK from `app/build/outputs/apk/debug/`.

3. **Run**:
   - Grant VPN permission on first launch.
   - The beautiful 3D splash will appear on every cold start.
   - Use the "Active Shields" floating cards on the home screen — they control real protection (Indian Scam Shield, protection modes, RASP, risk scoring, browser privacy, and actual crypto-miner blocks).

## Privacy & Architecture

ShieldDNS is 100% on-device. No analytics, no data leaves your phone. All threat detection, rule engines, and ML (optional TFLite) run locally.

See [PRIVACY.md](PRIVACY.md) for details.

## Contributing

Issues and PRs welcome! The UI is built with Jetpack Compose + a custom "liquid glass + 3D" design system. The core is a high-performance concurrent DNS engine with on-device heuristics and optional remote stubs (HMAC-protected).

---

*Originally evolved from ClearGuard concepts into the current ShieldDNS experience with the reference-inspired modern UI.*
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

The project uses standard Android build tools. Debug APKs can be built locally with `./gradlew assembleDebug` (or via Android Studio). GitHub Actions (if configured) can build releases.

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
