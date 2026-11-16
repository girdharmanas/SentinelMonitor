# Agent Operating Instructions & Repository Rules

> **Applies to:** Sentinel Monitor Codebase (`SentinelMonitor`)  
> **Workspace Root:** `G:/Projects/Personal/SentinelMonitor`

---

## 1. Mandatory Task Logging Protocol (`TASK_LOG.md`)

Whenever the user makes a request (feature addition, refactoring, bug fix, styling update, or architectural change), the agent **MUST** follow this protocol:

1. **Check State First:**
   - Review [TASK_LOG.md](file:///G:/Projects/Personal/SentinelMonitor/TASK_LOG.md) to understand current progress and any interrupted work.
   - When working on commercialization, `Sentinel Pro` feature gating, Play Billing, tester keys, onboarding, or Google Play compliance, consult [COMMERCIAL_ROADMAP.md](file:///G:/Projects/Personal/SentinelMonitor/COMMERCIAL_ROADMAP.md).

2. **Register the Request in `TASK_LOG.md`:**
   - Before executing code changes, log the task under the **Active Task** section of [TASK_LOG.md](file:///G:/Projects/Personal/SentinelMonitor/TASK_LOG.md).
   - Include:
     - Task ID (e.g. `TASK-XXX-YYY`)
     - Title and timestamp
     - Clear objective / user intent
     - Concrete checklist of implementation milestones

3. **Update Progress During Execution:**
   - As milestones are achieved, update the checklist in [TASK_LOG.md](file:///G:/Projects/Personal/SentinelMonitor/TASK_LOG.md).

4. **Finalize Upon Completion:**
   - Mark the task status as `COMPLETED`.
   - Move it to **Task History & Completed Work** with a summary of touched files, decisions made, and verification steps (e.g., unit test outcomes, R8 release build, UI/emulator verification).

---

## 2. Core Architectural Principles & Invariants

1. **Zero-Overhead Monitoring Invariant:**
   - Sentinel Monitor must never become the CPU, RAM, or battery hog it diagnoses.
   - All telemetry polling loops must be lifecycle-gated (`Lifecycle.State.STARTED` / `collectAsStateWithLifecycle`) so background CPU wakeups drop to zero unless an explicit user-configured background watchdog alert service is enabled.
   - All `/proc`, `ActivityManager`, `StatFs`, `StorageStatsManager`, `UsageStatsManager`, and `PackageManager` queries **MUST** run off the main thread (`Dispatchers.IO`).

2. **Safe Process & Storage Reclamation Invariant:**
   - Memory sweeps (`KILL_BACKGROUND_PROCESSES`) and cache/orphan reclaimers **MUST** strictly protect critical system and foreground packages (`com.sentinel.monitor`, `android`, `com.android.systemui`, active default Home launcher, active IME, active live wallpaper, telecom/dialer, clock/alarms, and Google Play Services) and must never touch user documents or persistent app databases.

3. **Modern Android & Jetpack Compose Best Practices:**
   - Use Material 3 dark-glass telemetry styling, Red/Amber/Green (`TelemetryRag`) health status indicators, Kotlin Coroutines, `StateFlow`, and Jetpack DataStore (`datastore-preferences`).
   - Respect Android 10–15 `/proc` SELinux sandboxing gracefully: when kernel nodes (such as restricted `/proc/<pid>` or `/proc/stat` on newer API levels) are restricted for non-root apps, degrade seamlessly to official SDK APIs (`ActivityManager`, `UsageStatsManager`, `HardwarePropertiesManager`, `BatteryManager`, `TrafficStats`) without crashing or showing broken `0%` readouts.

4. **APK Output Naming & Google Drive Upload Standard:**
   - All generated APKs (debug and release) automatically follow the naming format:
     `${applicationId}-${gitCommitHash}.apk` (e.g., `com.sentinel.monitor-a1b2c3d.apk`).
   - Every `assemble*` build automatically purges stale files and publishes the latest APK to both `artifacts/` and `H:/My Drive/Sentinel/Monitor` (overridable via `-Pgdrive.apk.dir` or `SENTINEL_GDRIVE_DIR`).

5. **Detailed Explanation, Automated Git Commit/Push & Verification:**
   - Whenever code changes or bug fixes are completed and verified:
     - The agent **MUST** present a thorough, detailed explanation in the response covering:
       1. The exact root cause / design motivation and underlying mechanisms.
       2. The concrete code changes made across all touched files, explaining what was changed, why it works, and how it solves the problem.
       3. The build results (`testDebugUnitTest`, `lintVitalRelease`, R8 `assembleRelease`), emulator verification, and visual outcome.
     - Once verified, the agent **MUST** automatically stage, commit, and push changes to git (`main` branch) without requiring separate confirmation (unless explicitly instructed not to).

6. **Communication & Links:**
   - Always provide clickable GitHub-style markdown file links using the `file://` scheme with forward slashes for all referenced files, components, and functions.
