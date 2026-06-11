# ClearGuard

ClearGuard is an Android DNS-level ad and tracker blocker inspired by tools like AdGuard and AdAway. It uses Android's `VpnService` to become the device DNS resolver, checks each DNS question against local host lists, and returns a local blocked response for matching domains.

## Features

- Local VPN DNS filtering with no root requirement.
- Hosts-style blocklist parsing for bundled, downloaded, and custom rules.
- Manual blocklist updates from AdAway and StevenBlack sources.
- Custom block and allow overrides.
- Local aggregate counters for blocked and allowed DNS queries.
- Configurable upstream IPv4 DNS resolver.
- Battery-conscious design: DNS-only route, in-memory DNS cache, no wakelocks, no scheduled background updates.
- Transparent privacy posture: no analytics SDKs, no accounts, no remote app logs.

## Limits

ClearGuard is DNS-level protection. It does not do HTTPS interception, cosmetic page filtering, per-app firewalling, root hosts-file editing, or browser extension filtering. Some first-party ads served from the same domain as content cannot be blocked safely at DNS level.

## Build

Open this folder in Android Studio with JDK 17 and Android SDK 35 installed, then run the `app` configuration.

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

- `app/src/main/java/com/clearguard/app/vpn/ClearGuardVpnService.java`: DNS VPN service.
- `app/src/main/java/com/clearguard/app/vpn/DnsPacket.java`: IPv4/UDP packet wrapping.
- `app/src/main/java/com/clearguard/app/vpn/DnsMessage.java`: DNS question parsing and blocked responses.
- `app/src/main/java/com/clearguard/app/blocking/HostBlocker.java`: hosts parsing and allow/block matching.
- `app/src/main/java/com/clearguard/app/blocking/BlocklistUpdater.java`: manual HTTPS blocklist downloads.
- `app/src/main/java/com/clearguard/app/MainActivity.kt`: Compose UI.
