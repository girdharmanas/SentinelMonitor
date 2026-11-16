# Sentinel Monitor — Commercialization, Licensing & Pro Roadmap 🚀

> **Status:** Planned Blueprint  
> **Baseline Version:** `v0.1.0` (`com.sentinel.monitor`)  
> **Purpose:** Source-of-truth specification for shipping Sentinel Monitor as a flagship Android System Process & Resource Task Manager on Google Play & direct distribution — including zero-cost full access for the owner and beta testers.

---

## 1. Core Commercial Philosophy

1. **Generous Daily-Utility Free Tier:** Instant real-time RAM, Storage, Battery/Thermal vitals, installed/active app inspection, and 1-tap safe background memory sweep are completely free with zero ads or dark patterns.
2. **One-Time Lifetime Unlock + Optional Low-Cost Annual Plan:** Offer `sentinel_pro_lifetime` alongside `sentinel_pro_yearly` (+ 7-day trial).
3. **Interactive Preview Paywalls:** Tapping any `PRO` capability previews live telemetry insights rather than showing dead toggles.

---

## 2. Free vs. `Sentinel Pro` Feature Matrix

| Capability | Tier | Rationale |
| :--- | :---: | :--- |
| Live System Overview (RAM, Storage, Battery, Thermal, Uptime, SoC/Device Info) | **FREE** | Core daily diagnostic utility |
| 1-Tap Safe Background Memory Sweep (Protected Critical Packages) | **FREE** | High-value daily action |
| App & Process Inspector (Search, System vs. User Filter, Quick App Info Jump) | **FREE** | Essential process triage |
| Per-App Usage & Foreground/Background Time Breakdown (`PACKAGE_USAGE_STATS`) | **PRO** | Deep battery & CPU culprit attribution |
| Per-App Storage & Cache Analyzer (`StorageStatsManager`) + Batch Cache Triage | **PRO** | High-conversion storage reclamation workflow |
| Historical Telemetry Sparklines & Thermal/RAM Spike Timeline | **PRO** | Power-user diagnostic history |
| Configurable Watchdog Alerts (High Thermal, Low Free RAM, Storage Thresholds) | **PRO** | Proactive background guardian |
| Home Screen Glanceable System Telemetry AppWidgets | **PRO** | Persistent desktop visibility |

---

## 3. Samsung Knox & Android 13–15 Distribution Pipeline

- Modern Samsung One UI (Knox Auto Blocker) and Android 13–15 Restricted Settings penalize sideloaded APKs when granting special access such as `PACKAGE_USAGE_STATS` (Usage Access).
- For development & direct testing, every `assembleDebug` / `assembleRelease` build automatically publishes `com.sentinel.monitor-<hash>.apk` to `artifacts/` and `H:/My Drive/Sentinel Monitor` (purging stale APKs automatically).
- For production & Knox-safe testing, upload signed `.aab` (`bundleRelease`) builds to Google Play's **Internal Testing Track** for instant (~60s) Play-trusted updates.
