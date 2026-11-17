package com.sentinel.monitor.data.model

data class MonitorReleaseHighlight(
    val title: String,
    val description: String,
    val category: String
)

data class MonitorReleaseVersion(
    val version: String,
    val releaseDate: String,
    val tagline: String,
    val isLatest: Boolean = false,
    val highlights: List<MonitorReleaseHighlight>
)

object SentinelReleaseNotes {

    const val CURRENT_VERSION = "v2.0.1"

    val RELEASES: List<MonitorReleaseVersion> = listOf(
        MonitorReleaseVersion(
            version = "v2.0.1",
            releaseDate = "November 17, 2026",
            tagline = "Zero-Root Firewall, Storage Scavenger, Hardware/DNS Lab, Tracker X-Ray & Release Notes Hub",
            isLatest = true,
            highlights = listOf(
                MonitorReleaseHighlight(
                    title = "In-App What's New & Version History Hub",
                    description = "Added unified Sentinel Suite release notes with automatic first-launch upgrade highlights, TopAppBar sparkles shortcut, and interactive version history.",
                    category = "Sentinel Suite"
                ),
                MonitorReleaseHighlight(
                    title = "Zero-Root Sinkhole App Firewall & Camera/Mic Blackbox",
                    description = "Block Wi-Fi and Cellular access per app with 0% CPU overhead via kernel-routed VpnService.Builder.addAllowedApplication, plus an event-driven Camera & Microphone hardware session timeline.",
                    category = "Security & Privacy"
                ),
                MonitorReleaseHighlight(
                    title = "Deep Storage Scavenger & 1-Tap APK Backup Vault",
                    description = "Find and delete leftover .apk/.xapk/.aab installers, >= 50 MB space hogs, and exact SHA-256 duplicate files (preserving originals), or extract any installed app's base APK to Downloads/Sentinel Backups.",
                    category = "Storage"
                ),
                MonitorReleaseHighlight(
                    title = "18-SDK Embedded Tracker Scanner, Doze & Boot Audits",
                    description = "App X-Ray now scans manifest components against 18 Ad/Analytics SDK signatures (AdMob, Firebase, Meta, AppLovin, Adjust, Sentry, etc.) and flags Doze-Exempt battery vampires, Boot Auto-Start apps, and 24h Wi-Fi/Cellular data usage.",
                    category = "App X-Ray"
                ),
                MonitorReleaseHighlight(
                    title = "3-Hop Network/DNS Race, Cable Grader, Flight Recorder & Silicon Lab",
                    description = "Isolate Router vs. WAN vs. DNS faults, race 5 UDP DNS resolvers in parallel, grade USB-C cables & PD bricks, record gaming thermal/FPS/Wattage sessions, verify Widevine L1 & HW AV1/HEVC 10-bit decoders, and test OLED uniformity + Touch Hz.",
                    category = "Hardware Lab"
                ),
                MonitorReleaseHighlight(
                    title = "Kernel Deep-Sleep %, 80% Charge Alarm & Launcher X-Ray Bridge",
                    description = "Track kernel suspend efficiency on Vitals, set an 80% battery longevity alarm in Watchdog, auto-sweep on Sentinel Launcher Focus Space switches, and jump straight into single-app X-Ray from Launcher long-press menus.",
                    category = "Power & Ecosystem"
                )
            )
        ),
        MonitorReleaseVersion(
            version = "v1.1.0",
            releaseDate = "November 17, 2026",
            tagline = "Direct 1-Tap Non-Essential Process Stopper & Automated App Cache Cleaner",
            isLatest = false,
            highlights = listOf(
                MonitorReleaseHighlight(
                    title = "1-Tap Stop Non-Essential Processes",
                    description = "Force-stop active background user apps directly from Vitals, Processes, or App X-Ray while strictly protecting essential system services, IMEs, Sentinel Suite apps, and your Protected Whitelist.",
                    category = "Process Control"
                ),
                MonitorReleaseHighlight(
                    title = "Direct In-App Cache Cleaner & OS Clear-All Fallback",
                    description = "Automatically clear top app caches across Android 14–15 Compose Settings and classic OEM Settings with hard safety blacklists that never touch user data or logins.",
                    category = "Storage"
                )
            )
        ),
        MonitorReleaseVersion(
            version = "v1.0.0",
            releaseDate = "November 16, 2026",
            tagline = "5-Tab Flagship Telemetry Dashboard, Floating Mini-HUD, Watchdog & QS Tile",
            isLatest = false,
            highlights = listOf(
                MonitorReleaseHighlight(
                    title = "Live Per-Core CPU Clocks, ZRAM/Swap & Signed Battery Wattage",
                    description = "Real-time 2s lifecycle-gated telemetry for CPU cluster scaling, physical RAM + ZRAM + Page Cache sparklines, signed +W/-W power flow, thermal throttling, and Wi-Fi band/Mbps/RSSI.",
                    category = "Vitals"
                ),
                MonitorReleaseHighlight(
                    title = "App X-Ray Certificates, Security Auditor & Glanceable Surfaces",
                    description = "SHA-1/SHA-256 signing certificate copy, native ABI inspection, high-privilege permission & sideload audit, 15m Watchdog alerts, Quick Settings Sweep Tile, Floating Mini-HUD pill, and Home Widget.",
                    category = "Diagnostics"
                )
            )
        ),
        MonitorReleaseVersion(
            version = "v0.1.0",
            releaseDate = "November 16, 2026",
            tagline = "Initial Sentinel Monitor Foundation, 3D-Faceted Crest & Google Drive Hub",
            isLatest = false,
            highlights = listOf(
                MonitorReleaseHighlight(
                    title = "Zero-Overhead Architecture & Safe Memory Sweep",
                    description = "Built from day one with strict STARTED-lifecycle polling (0% background CPU when minimized), safe cached-process reclamation, unified 3D-faceted Sentinel Suite iconography, and automated Google Drive APK publishing.",
                    category = "Foundation"
                )
            )
        )
    )
}
