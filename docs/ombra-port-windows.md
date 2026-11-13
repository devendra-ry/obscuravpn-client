# Ombra features on the Windows client

[Ombra](https://github.com/Turtlecute33/Ombra) is an independent Android fork of this client. Its Rust core is
`rustlib/` at `85ef7e2` (v1.182) with patches P1 to P28 (Ombra `rust/UPSTREAM.md`). This branch ports them to
v1.186 for the Windows client. Ombra commit: `c7218ed` (0.2.12).

Status values: **Ported** (in the code and covered by tests on Windows), **Already upstream** (the official client
had it), **Not ported** (with the reason).

Nothing here was tested against the live relays or with a real connection on Windows.

## (a) Rust core, all platforms

| Feature | Ombra patch | Status | Notes |
|---|---|---|---|
| QUIC MTU floor of 1350 bytes, with fallback race and large liveness probes | P4 (B1) | Ported | |
| Fewer ACKs (ACK frequency request, threshold 3, 5 ms) | P4 (B2) | Ported | Off for the process after a QUIC transport error. |
| BBR without ProbeRTT | P27 | Ported | `src/bbr/` is a changed copy of quinn-proto 0.11.19's BBR. |
| Shorter datagram send queue (256 KiB) | P4 (B3) | Ported | |
| UDP buffers: 8 MiB receive, 2 MiB send | P4 (B6), P28 | Ported | Windows accepts 8 MiB (`udp_socket_buffers_are_larger_than_the_default`). |
| Small packets share one (padded) QUIC packet | P4 (E1), P7, P11, P26 | Ported | **Windows fix:** while a QUIC coalescer runs, the service requests a 1 ms timer resolution (`timeBeginPeriod`). With Windows' default of 15.6 ms, the 2 ms batch wait took about 16 ms. |
| Relay race avoids UDP port 53 unless it is 25 ms faster | P6 | Ported | |
| Connect backoff (0, 1, 3, 7, 15, 29 s); a tunnel that breaks within 15 s counts as failed | P9, P19 | Ported | |
| Speed mode: plain WireGuard over UDP through the two-hop relay | P5 | Ported | Core, plus the Windows UI below. Speed mode uses the same outer-socket binding to the active adapter as QUIC. |
| TCP/TLS as a tunnel mode | P1 | Ported | Core, plus the Windows UI. The `tcpTlsTunnel` feature flag still works. |
| WireGuard key max age: 30 days, daily, or at each connect; checked before each new tunnel | P14 | Ported | Core, plus the Windows UI. |
| Fallback API host: the backup domain always comes first, then the user host | P15 | Ported | |
| Entry relay selector and relay list (`getRelayList`, `refreshRelayList`) | P1, P2 | Ported | Core, plus the Windows UI. |
| API DNS cache with a 1 h freshness | P3 | Ported | |
| Log redaction (no tunnel addresses, account numbers, tokens or JSON bodies) | P3, P12 | Ported | Covers the Windows service too. |
| Login, token and account info hardening; no login URL override | P12, P17, P18 | Ported | |
| Network handover (rebind QUIC or Speed mode on a network change) | P4 (D1, D2) | Ported, untested on Windows | Windows reports a change of the active adapter, and `Handover::decide` picks rebind or reconnect. No Windows test covers this. |
| Config hygiene: temp files and backups deleted | P12, P22 | Ported | |
| Smaller fixes: OS config errors, fragment copies, log rate limits | P20, P21, P23 | Ported | |
| Encrypted `config.json` | P13 | Ported, inactive on Windows | The service passes no key, so the file stays plain text, as upstream. See (c). |
| Referral command `apiUseReferralCode` | P16 | In the core, not in any UI | The core sends no referral on its own. |
| `Status.tunnelArgs`, `Status.wgKeyMaxAgeSecs` | new | Added on this branch | These let the UI, the tray and auto-connect keep the protocol, entry relay and MTU. |
| Crate security updates (`rustls`, `webpki-roots`, `tokio` and others) | Cargo.lock | Ported | `obscuravpn-api` stays at v1.186's `f7578b5`. `deny.toml` lists the ignored advisories with reasons. |

Android-only core patches are not ported. `src/android` is kept as upstream so that the official Android app still
links. The skipped patches are P0 (JNI rename, Keystore keys), P0.1 (TUN reuse), P10/P24/P25 (TUN writer thread,
one worker, scheduling), and the Android parts of P4 (`android_setsocknetwork`, CLAT MTU hint). Ombra's Kotlin
contract test is dropped because its fixture lives in the Ombra app.

## (b) Client features that need Windows UI or OS code

| Feature | Status | Notes |
|---|---|---|
| Protocol: Stealth (QUIC), Speed (WireGuard/UDP), TCP/TLS | Ported | Settings → Connection (`ConnectionSettings` in `obscura-ui/src/views/Settings.tsx`). A change reconnects a live tunnel. |
| MTU options | Ported | Speed mode MTU (1280–1420) in the same card. "Force small MTU" for QUIC was already an experimental flag upstream. |
| Entry relay choice: Fastest, country, city, server | Ported | Searchable select fed by the relay list. |
| Key rotation interval | Ported | 30 days, daily, or at each connect. |
| Exit choice: country, city, server, Fastest | Already upstream | Location view and tray. |
| Favourites | Already upstream | Pinned locations. |
| DNS content blocking | Already upstream | |
| Tray and auto-connect keep the chosen protocol and relay | Ported | C# `TunnelArgs.WithExit` (`windows/obscura-client/InvokeCommand.cs`). Before this, the C# client dropped every field except `exit`. |
| Custom DNS servers | Not ported | In Ombra this is Kotlin-side (the Android TUN). Windows needs a new `DnsConfig` variant carried through `TargetState`/`OsNetworkConfig` (about 30 sites) into `tun.set_config`. That path already routes each DNS IP into the tunnel and sets NRPT. Feasible, but it needs a DNS leak test on a real machine. |
| Split tunnelling (per app) | Not ported | Android uses `VpnService` per-app rules. Windows has no per-app routing, so this needs a WFP callout driver or ALE redirect. |
| Local network access | Not ported | The core enables it only on Android and Linux (`LocalNetworkAccess` is `cfg`-gated). Windows needs LAN route exclusions in `iphelper`. |
| Kill switch status | Not ported | Android shows the OS always-on/lockdown state, which Windows doesn't have. The core keeps traffic captured while connecting (P12). Upstream "strict leak prevention" is Apple-only. A Windows kill switch would use WFP block filters. |

## (c) Android-only features and Windows counterparts

| Ombra feature | Windows counterpart |
|---|---|
| Quick Settings tile, shortcuts | The tray menu (already upstream). It now keeps the chosen protocol and relay. |
| WireGuard key encrypted with an Android Keystore key | Already upstream: the service seals the key with a TPM key bound to PCR 7 (NCrypt Platform Crypto Provider), or keeps it only in memory. |
| `config.json` encrypted with a Keystore key (P13) | Not wired up. The core supports it (`Manager::new(.., config_key, ..)`). **DPAPI** (`CryptProtectData`, `CRYPTPROTECT_LOCAL_MACHINE`, the service runs as SYSTEM) is recommended for a sealed key file. The existing TPM key is bound to PCR 7, and Secure Boot DBX updates from Windows Update change PCR 7. A TPM-sealed config key would then fail and log the user out. |
| TUN write thread, one runtime worker, `SCHED_NORMAL` nice -4 (P10, P24, P25) | Not ported. Windows uses Wintun ring buffers with a different write path. A dedicated Wintun send thread would be the counterpart; measure first. |
| Outer sockets bound to an Android `Network` | Already upstream: the Windows service binds outer sockets to the active adapter's IP. |
| Screenshots blocked on account screens, clipboard cleared after 60 s | Not ported. Counterparts: `SetWindowDisplayAffinity(WDA_EXCLUDEFROMCAPTURE)` and a timed clipboard clear in the WinUI host. |

## Verification on this branch

- `cargo test --target x86_64-pc-windows-msvc`: all tests pass (131 lib, 5 bin).
- `cargo clippy --all-targets` (Windows): no warnings. `cargo ndk -t arm64-v8a check --lib`: builds. One deprecation
  warning (`AsyncFd::new`) comes from the `tokio` security update in upstream Android code.
- `cargo build --release --bin obscura` (Windows x64): builds.
- `obscura-ui`: `tsc --noEmit` passes. `vite build` with `OBS_WEB_PLATFORM=windows` builds (with a placeholder
  licenses file).
- Not verified: the C# WinUI client and the MSI (this machine has no .NET 10 SDK or WinUI workload), a real
  connection in any mode, the macOS, iOS and Linux builds, and how the new Settings card looks.
