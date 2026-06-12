# ClearGuard

ClearGuard is an Android DNS-level ad and tracker blocker inspired by tools like AdGuard and AdAway. It uses Android's `VpnService` to become the device DNS resolver, checks each DNS question against local host lists, and returns a local blocked response for matching domains.

## Features

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
