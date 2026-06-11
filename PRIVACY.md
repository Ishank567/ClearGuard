# ClearGuard Privacy

ClearGuard is designed to be transparent and local-first.

## What Stays On Device

- Aggregate blocked DNS query count.
- Aggregate allowed DNS query count.
- Custom blocked domains.
- Custom allowed domains.
- Downloaded host blocklist file.
- In-memory DNS response cache.

ClearGuard does not store per-domain browsing history.

## Network Use

- DNS queries that are not blocked are forwarded to the upstream resolver you choose in Settings.
- Blocklist URLs are downloaded only when you tap update.
- No analytics, telemetry, crash reporting, account sync, or remote app logging is included.

## Permissions

- `VpnService`: required to receive device DNS traffic.
- `INTERNET`: required to forward allowed DNS queries and download blocklists.
- `ACCESS_NETWORK_STATE`: reserved for network-aware update behavior.
- `FOREGROUND_SERVICE`: required for the always-visible VPN service notification.
- `POST_NOTIFICATIONS`: required on Android 13+ so the VPN foreground notification can be shown.

## Battery Posture

- Routes only the virtual DNS server address through the VPN.
- Does not route all app traffic through userspace.
- Uses a small in-memory DNS cache.
- Does not schedule automatic updates.
- Does not use wakelocks.
