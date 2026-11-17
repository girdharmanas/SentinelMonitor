package com.sentinel.monitor.util

import android.content.Context
import android.os.Process
import java.util.concurrent.TimeUnit

/**
 * Optional privileged shell fast-path (supports ADB shell UID / Root `su` when present,
 * and detects Shizuku presence). Strictly non-blocking (<1.5s timeout) and falls back
 * cleanly to standard Android APIs / Accessibility automation on stock unrooted devices.
 */
object ShizukuShellBridge {

    private val SAFE_PACKAGE_REGEX = Regex("^[a-zA-Z0-9._]+$")

    internal fun isValidPackageIdentifier(packageName: String): Boolean {
        val trimmed = packageName.trim()
        return trimmed.length in 3..180 && SAFE_PACKAGE_REGEX.matches(trimmed)
    }

    fun isShizukuInstalled(context: Context): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        }.getOrDefault(false)
    }

    fun hasDirectPrivilegedShell(): Boolean {
        val uid = Process.myUid()
        return uid == 0 || uid == 2000
    }

    fun tryShellForceStop(packageName: String): Boolean {
        if (!isValidPackageIdentifier(packageName)) return false
        if (hasDirectPrivilegedShell()) {
            return execTimed(arrayOf("am", "force-stop", packageName))
        }
        return false
    }

    fun tryShellTrimCaches(freeTargetBytes: Long = 64L * 1024L * 1024L * 1024L): Boolean {
        if (freeTargetBytes <= 0L) return false
        if (hasDirectPrivilegedShell()) {
            return execTimed(arrayOf("pm", "trim-caches", freeTargetBytes.toString()))
        }
        return false
    }

    private fun execTimed(cmd: Array<String>, timeoutMs: Long = 1200L): Boolean {
        return runCatching {
            val proc = ProcessBuilder(*cmd)
                .redirectErrorStream(true)
                .start()
            val finished = proc.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                proc.destroyForcibly()
                false
            } else {
                proc.exitValue() == 0
            }
        }.getOrDefault(false)
    }
}
