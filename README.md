# Sentinel Monitor (`com.sentinel.monitor`) — `v2.1.0`

Flagship Android System Process, Hardware Telemetry, Storage Scavenger, Network/DNS Lab & Zero-Root Firewall Task Manager in the **Sentinel Suite** (`Sentinel Monitor`, `Sentinel Launcher`, `Sentinel Reader`).

## Core Capabilities (`v2.1.0`)
- **Settings, Multi-Mode Theme Studio & JSON Backup:** 4 Theme Modes (`Sentinel Obsidian`, `Pitch-Black AMOLED #000000`, `Crisp Daylight`, `Follow System`), 6 Accent Palettes (`Sentinel Emerald`, `Cyber Cyan`, `Launcher Indigo`, `Reader Gold`, `Tactical Crimson`, `Material You Dynamic`), `1s/2s/5s` telemetry cadence, `°C`/`°F` units, Default Startup Tab, Compact Process Rows, centralized Permissions Hub, and JSON Backup Export/Import.
- **Vitals, Deep-Sleep & Direct 1-Tap Control:** Per-core CPU MHz & governor clusters, physical RAM + ZRAM/Swap + Kernel Cache, signed Battery Wattage (`+W`/`-W`) & `PowerManager` thermal throttling, **Kernel Deep-Sleep %** (`elapsedRealtime - uptimeMillis`), live `↑/↓` network throughput, LTPO Display `Hz`, and 1-tap automated **Stop Non-Essential Apps** & **Clear App Caches** (with optional `<100ms` privileged shell fast-path).
- **Process Culprits, Tracker X-Ray & APK Vault:** 24h foreground CPU & Wi-Fi/Cellular data attribution, Doze-Exempt ("Battery Vampire") & `BOOT_COMPLETED` Auto-Start filters, Kernel/JVM Exit & OOM log, Protected Whitelist, **18-SDK Embedded Ad/Tracker Scanner**, **1-Tap Base APK Backup Vault** (`Downloads/Sentinel Backups`), and `sentinel://monitor?tab=processes&pkg=<pkg>` single-app X-Ray deep-link.
- **Storage Cache Cleaner & Deep Storage Scavenger:** Exact `StorageStatsManager` per-app cache cleaner + **Storage Scavenger** scanning leftover `.apk`/`.apks`/`.xapk` installers (with installed-version check), `>= 50 MB` space hogs, and **exact SHA-256 duplicate files** (preserving oldest originals).
- **Security Audit, Zero-Root App Firewall & Privacy Blackbox:** High-privilege permission & sideload audit, **Zero-CPU Per-App Sinkhole VPN Firewall** (`VpnService.Builder.addAllowedApplication` routing only blocked apps to an inert TUN sinkhole while 100% of unblocked apps bypass VPN), and event-driven **Camera & Microphone Privacy Blackbox** session timeline.
- **Hardware Diagnostics Lab, 3-Hop Network/DNS Race & Watchdog:** **USB-C Cable & Charger Grader** (rail sag `mV` + PD/PPS `W`), **Game/Benchmark Flight Recorder**, **Widevine DRM (`L1`/`L3`) & Hardware `AV1` / `HEVC 10-bit` / `VP9` Codec Audit**, **Fullscreen OLED Uniformity & Multi-Touch Hz Test**, **3-Hop Router vs. WAN vs. DNS Fault Isolator + DNS Speed Race**, **80% Charge Limit Alarm**, **Hidden Android Power-User Shortcut Deck**, **1-Tap Markdown Device Health Dossier**, and **Sentinel Launcher Focus Space Auto-Sweep Bridge**.

## Quick Commands
- **Unit Tests:** `./gradlew testDebugUnitTest`
- **Release APK + Lint (auto-uploads to `artifacts/` & `H:/My Drive/Sentinel/Monitor`):** `./gradlew lintVitalRelease assembleRelease`
