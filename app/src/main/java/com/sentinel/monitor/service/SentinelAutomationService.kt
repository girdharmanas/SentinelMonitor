package com.sentinel.monitor.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.sentinel.monitor.MainActivity
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.TelemetryFormatters
import com.sentinel.monitor.util.AppInspectorEngine
import com.sentinel.monitor.util.SystemTelemetryEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale

enum class AutomationMode {
    FORCE_STOP_PROCESSES,
    CLEAR_APP_CACHES
}

data class AutomationProgress(
    val isRunning: Boolean = false,
    val mode: AutomationMode = AutomationMode.FORCE_STOP_PROCESSES,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val currentAppLabel: String = "",
    val completedCount: Int = 0,
    val lastSummaryBanner: String? = null
)

class SentinelAutomationService : AccessibilityService() {

    companion object {
        @Volatile
        private var instanceRef: WeakReference<SentinelAutomationService>? = null

        private val _progressFlow = MutableStateFlow(AutomationProgress())
        val progressFlow: StateFlow<AutomationProgress> = _progressFlow.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            if (instanceRef?.get() != null) return true
            val enabled = runCatching {
                Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
            }.getOrNull().orEmpty()
            return enabled.contains("${context.packageName}/", ignoreCase = true) ||
                enabled.contains(SentinelAutomationService::class.java.simpleName, ignoreCase = true)
        }

        fun startForceStopNonEssential(
            targets: List<InstalledProcessEntry>,
            returnTab: String = "processes"
        ): Boolean {
            val svc = instanceRef?.get() ?: return false
            svc.launchBatch(AutomationMode.FORCE_STOP_PROCESSES, targets, returnTab)
            return true
        }

        fun startClearCaches(
            targets: List<InstalledProcessEntry>,
            returnTab: String = "storage"
        ): Boolean {
            val svc = instanceRef?.get() ?: return false
            svc.launchBatch(AutomationMode.CLEAR_APP_CACHES, targets, returnTab)
            return true
        }

        fun cancelActiveRun() {
            instanceRef?.get()?.cancelCurrentJob()
        }

        fun clearBanner() {
            _progressFlow.value = _progressFlow.value.copy(lastSummaryBanner = null)
        }

        // -------------------------------------------------------------------
        // Pure Safety & Semantic Node Matchers (Unit-Tested)
        // -------------------------------------------------------------------

        private val DANGEROUS_STORAGE_BLACKLIST = listOf(
            "data",
            "storage",
            "manage",
            "space",
            "uninstall",
            "delete",
            "reset",
            "archive",
            "defaults"
        )

        internal fun isSafeClearCacheLabel(rawText: String?): Boolean {
            if (rawText.isNullOrBlank()) return false
            val norm = rawText.trim().lowercase(Locale.US)
            val firstLine = norm.lineSequence().firstOrNull()?.trim().orEmpty()
            if (DANGEROUS_STORAGE_BLACKLIST.any { bad -> norm.contains(bad) }) return false
            return firstLine == "clear cache" ||
                firstLine == "clear app cache" ||
                norm == "clear cache"
        }

        internal fun isStorageSectionLabel(rawText: String?): Boolean {
            if (rawText.isNullOrBlank()) return false
            val firstLine = rawText.trim().lineSequence().firstOrNull()?.trim()?.lowercase(Locale.US).orEmpty()
            if (firstLine.contains("clear") || firstLine.contains("manage")) return false
            return firstLine == "storage & cache" ||
                firstLine == "storage and cache" ||
                firstLine == "storage"
        }

        internal fun isForceStopButtonLabel(rawText: String?): Boolean {
            if (rawText.isNullOrBlank()) return false
            val firstLine = rawText.trim().lineSequence().firstOrNull()?.trim()?.lowercase(Locale.US).orEmpty()
            return firstLine == "force stop" || firstLine == "force stop app"
        }

        internal fun isForceStopConfirmButtonLabel(rawText: String?): Boolean {
            if (rawText.isNullOrBlank()) return false
            val norm = rawText.trim().lowercase(Locale.US)
            if (norm.contains("cancel")) return false
            return norm == "ok" || norm == "force stop" || norm == "confirm"
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instanceRef = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Zero-overhead invariant: completely inert when user has not triggered an active batch job
    }

    override fun onInterrupt() {
        cancelCurrentJob()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cancelCurrentJob()
        instanceRef = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelCurrentJob()
        serviceScope.cancel()
        instanceRef = null
        super.onDestroy()
    }

    private fun cancelCurrentJob() {
        activeJob?.cancel()
        activeJob = null
        _progressFlow.value = AutomationProgress(
            isRunning = false,
            lastSummaryBanner = "Automation cancelled"
        )
    }

    private fun launchBatch(
        mode: AutomationMode,
        targets: List<InstalledProcessEntry>,
        returnTab: String
    ) {
        if (targets.isEmpty()) {
            _progressFlow.value = AutomationProgress(
                isRunning = false,
                mode = mode,
                lastSummaryBanner = if (mode == AutomationMode.FORCE_STOP_PROCESSES) {
                    "✓ All non-essential apps are already stopped"
                } else {
                    "✓ No reclaimable app caches above threshold"
                }
            )
            return
        }

        activeJob?.cancel()
        activeJob = serviceScope.launch {
            var completed = 0
            val beforeCacheTotal = if (mode == AutomationMode.CLEAR_APP_CACHES) {
                targets.sumOf { it.cacheBytes }
            } else {
                0L
            }

            try {
                if (mode == AutomationMode.FORCE_STOP_PROCESSES) {
                    SystemTelemetryEngine.performSafeMemorySweep(applicationContext)
                }

                targets.forEachIndexed { idx, entry ->
                    if (!isActive) return@launch
                    _progressFlow.value = AutomationProgress(
                        isRunning = true,
                        mode = mode,
                        currentIndex = idx + 1,
                        totalCount = targets.size,
                        currentAppLabel = entry.appLabel,
                        completedCount = completed
                    )

                    val ok = when (mode) {
                        AutomationMode.FORCE_STOP_PROCESSES -> executeSingleForceStop(entry.packageName)
                        AutomationMode.CLEAR_APP_CACHES -> executeSingleClearCache(entry.packageName)
                    }
                    if (ok) completed++
                }

                returnToSentinelMonitor(returnTab)
                delay(350L)

                val summary = if (mode == AutomationMode.FORCE_STOP_PROCESSES) {
                    "✓ Force-stopped $completed of ${targets.size} non-essential apps"
                } else {
                    val refreshed = AppInspectorEngine.queryFullAppCatalog(applicationContext)
                    val targetSet = targets.mapTo(HashSet()) { it.packageName }
                    val afterCacheTotal = refreshed.filter { it.packageName in targetSet }.sumOf { it.cacheBytes }
                    val freed = (beforeCacheTotal - afterCacheTotal).coerceAtLeast(0L)
                    if (freed >= 512 * 1024L) {
                        "✓ Cleared caches across $completed apps • Freed ${TelemetryFormatters.formatBytes(freed)}"
                    } else {
                        "✓ Cleared app caches across $completed of ${targets.size} apps"
                    }
                }

                _progressFlow.value = AutomationProgress(
                    isRunning = false,
                    mode = mode,
                    completedCount = completed,
                    totalCount = targets.size,
                    lastSummaryBanner = summary
                )
            } catch (_: CancellationException) {
                returnToSentinelMonitor(returnTab)
            } catch (e: Exception) {
                returnToSentinelMonitor(returnTab)
                _progressFlow.value = AutomationProgress(
                    isRunning = false,
                    mode = mode,
                    lastSummaryBanner = "Completed $completed/${targets.size} apps"
                )
            }
        }
    }

    private suspend fun executeSingleForceStop(packageName: String): Boolean {
        if (isPackageStopped(packageName)) return true

        openAppDetailsScreen(packageName)
        var clickedForceStop = false
        var scrolledOnce = false
        val deadline = System.currentTimeMillis() + 3_800L

        while (System.currentTimeMillis() < deadline) {
            delay(170L)
            if (isPackageStopped(packageName)) return true

            val root = rootInActiveWindow ?: continue

            // 1. Check if confirmation dialog ("Force stop?" -> OK / Force stop) is open
            if (isConfirmationDialogVisible(root)) {
                val confirmNode = findConfirmDialogButton(root)
                if (confirmNode != null && clickSelfOrParent(confirmNode)) {
                    delay(260L)
                    if (isPackageStopped(packageName)) return true
                    continue
                }
            }

            // 2. Find "Force stop" button on App Info screen
            val forceNode = findNodeRecursive(root) { node ->
                val id = node.viewIdResourceName.orEmpty()
                val label = nodeLabel(node)
                id.endsWith(":id/force_stop_button") ||
                    (id.endsWith(":id/right_button") && isForceStopButtonLabel(label)) ||
                    (id.endsWith(":id/button2") && isForceStopButtonLabel(label)) ||
                    isForceStopButtonLabel(label)
            }

            if (forceNode != null) {
                val clickable = resolveClickableTarget(forceNode)
                if (clickable != null && !clickable.isEnabled) {
                    // Already stopped!
                    return true
                }
                if (!clickedForceStop && clickSelfOrParent(forceNode)) {
                    clickedForceStop = true
                    delay(220L)
                    continue
                }
            } else if (!scrolledOnce) {
                scrollDownFirstScrollable(root)
                scrolledOnce = true
            }
        }
        return isPackageStopped(packageName)
    }

    private suspend fun executeSingleClearCache(packageName: String): Boolean {
        openAppDetailsScreen(packageName)
        var openedStorageSubpage = false
        var scrolledOnce = false
        val deadline = System.currentTimeMillis() + 4_200L

        while (System.currentTimeMillis() < deadline) {
            delay(180L)
            val root = rootInActiveWindow ?: continue

            // 1. Look for safe "Clear cache" button (strictly excluding Clear storage / Clear data!)
            val clearCacheNode = findNodeRecursive(root) { node ->
                val id = node.viewIdResourceName.orEmpty()
                val label = nodeLabel(node)
                id.endsWith(":id/clear_cache_button") ||
                    (id.endsWith(":id/button2") && isSafeClearCacheLabel(label)) ||
                    isSafeClearCacheLabel(label)
            }

            if (clearCacheNode != null) {
                val target = resolveClickableTarget(clearCacheNode)
                if (target != null && !target.isEnabled) {
                    // Cache is already 0 B
                    return true
                }
                if (clickSelfOrParent(clearCacheNode)) {
                    delay(280L)
                    return true
                }
            }

            // 2. Otherwise navigate into "Storage & cache" / "Storage" row from App Info
            if (!openedStorageSubpage) {
                val storageRow = findNodeRecursive(root) { node ->
                    isStorageSectionLabel(nodeLabel(node))
                }
                if (storageRow != null && clickSelfOrParent(storageRow)) {
                    openedStorageSubpage = true
                    delay(260L)
                    continue
                } else if (!scrolledOnce) {
                    scrollDownFirstScrollable(root)
                    scrolledOnce = true
                }
            }
        }
        return false
    }

    private fun openAppDetailsScreen(packageName: String) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION
                )
            }
            startActivity(intent)
        }
    }

    private fun returnToSentinelMonitor(tab: String) {
        runCatching {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("sentinel://monitor?tab=$tab"),
                applicationContext,
                MainActivity::class.java
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            startActivity(intent)
        }
    }

    private fun isPackageStopped(packageName: String): Boolean {
        return runCatching {
            val ai = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
            (ai.flags and ApplicationInfo.FLAG_STOPPED) != 0
        }.getOrDefault(false)
    }

    private fun isConfirmationDialogVisible(root: AccessibilityNodeInfo): Boolean {
        val hasDialogId = findNodeRecursive(root) { node ->
            val id = node.viewIdResourceName.orEmpty()
            id == "android:id/alertTitle" || id == "android:id/message" || id.endsWith(":id/parentPanel")
        } != null
        if (hasDialogId) return true

        val hasCancelAndPrompt = findNodeRecursive(root) { node ->
            val txt = nodeLabel(node).lowercase(Locale.US)
            txt.contains("misbehave") || txt.contains("force stop?")
        } != null
        return hasCancelAndPrompt
    }

    private fun findConfirmDialogButton(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        findNodeRecursive(root) { it.viewIdResourceName == "android:id/button1" }?.let { return it }
        return findNodeRecursive(root) { node ->
            isForceStopConfirmButtonLabel(nodeLabel(node)) && resolveClickableTarget(node) != null
        }
    }

    private fun nodeLabel(node: AccessibilityNodeInfo): String {
        val t = node.text?.toString()?.trim().orEmpty()
        if (t.isNotEmpty()) return t
        return node.contentDescription?.toString()?.trim().orEmpty()
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val hit = findNodeRecursive(child, predicate)
            if (hit != null) return hit
        }
        return null
    }

    private fun resolveClickableTarget(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var cur = node
        var depth = 0
        while (cur != null && depth < 4) {
            if (cur.isClickable) return cur
            cur = cur.parent
            depth++
        }
        return node
    }

    private fun clickSelfOrParent(node: AccessibilityNodeInfo): Boolean {
        val target = resolveClickableTarget(node) ?: return false
        if (!target.isEnabled) return false
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    private fun scrollDownFirstScrollable(root: AccessibilityNodeInfo): Boolean {
        val scrollable = findNodeRecursive(root) { it.isScrollable } ?: return false
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }
}
