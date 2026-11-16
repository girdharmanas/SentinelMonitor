# Sentinel Monitor (`com.sentinel.monitor`) — `v1.0.0`

Flagship Android System Process, Hardware Telemetry, Storage Cache & Security Task Manager in the **Sentinel Suite** (`Sentinel Monitor`, `Sentinel Launcher`, `Sentinel Reader`).

## Core Capabilities (`v1.0.0`)
- **Vitals & Live Sparklines:** Per-core CPU MHz & governor clusters, physical RAM + ZRAM/Swap + Kernel Cache, signed Battery Wattage (`+W`/`-W`) & `PowerManager` thermal throttling, live `↑/↓` network throughput & Wi-Fi band/Mbps/RSSI, and LTPO Display `Hz`.
- **Process Culprits & App X-Ray:** 24h foreground CPU attribution, App Standby Buckets, Kernel/JVM Exit & OOM log, Protected Whitelist, Top-5 Hibernate Queue, and **App X-Ray Bottom Sheet** (`minSdk`/`targetSdk`, native ABIs, App/Data/Cache footprint, and 1-tap SHA-1/SHA-256 copy).
- **Storage & Cache Hog Analyzer:** Exact `StorageStatsManager` per-app cache & storage breakdown ranked by reclaimable cache.
- **Sentinel Security Auditor:** Instant audit of Accessibility, Notification Listeners, Install Unknown Apps, Overlays, All Files, Camera/Mic, Sideloaded APKs, and Legacy `targetSdk < 31` apps.
- **Watchdog & Glanceable Surfaces:** Configurable 15m `JobScheduler` alerts, 24h DataStore trend sparklines, Quick Settings `Sentinel Sweep` Tile, draggable Floating Developer Mini-HUD pill, Home Screen `AppWidget`, and `sentinel://monitor?tab=...` deep-linking from **Sentinel Launcher**.

## Quick Commands
- **Unit Tests:** `./gradlew testDebugUnitTest`
- **Release APK + Lint (auto-uploads to `artifacts/` & `H:/My Drive/Sentinel/Monitor`):** `./gradlew lintVitalRelease assembleRelease`
