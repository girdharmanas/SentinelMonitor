package com.sentinel.monitor.util

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.sentinel.monitor.data.model.InstalledProcessEntry
import com.sentinel.monitor.data.model.ScavengerFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

object StorageScavengerEngine {

    private val INSTALLER_EXTENSIONS = setOf("apk", "apks", "xapk", "aab")
    private const val LARGE_FILE_THRESHOLD_BYTES = 50L * 1024L * 1024L // 50 MB
    private const val MIN_DUPLICATE_SIZE_BYTES = 64L * 1024L // 64 KB
    private const val MAX_DUPLICATE_HASH_BYTES = 512L * 1024L * 1024L // 512 MB cap for fast I/O

    fun hasFullScavengerAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    suspend fun scanStorageScavenger(context: Context): List<ScavengerFileItem> = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val pm = appCtx.packageManager
        val discoveredByPath = LinkedHashMap<String, File>()

        val roots = listOf(
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_MOVIES,
            Environment.DIRECTORY_DCIM,
            Environment.DIRECTORY_PICTURES,
            Environment.DIRECTORY_MUSIC
        ).mapNotNull { type ->
            runCatching { Environment.getExternalStoragePublicDirectory(type) }.getOrNull()
        }

        for (root in roots) {
            collectReadableFilesRecursive(root, depth = 0, maxDepth = 4, out = discoveredByPath)
        }

        // Also query MediaStore Downloads on Android 10+ so public downloads appear even before All-Files grant
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                appCtx.contentResolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (dataCol >= 0) {
                        while (cursor.moveToNext()) {
                            val p = cursor.getString(dataCol) ?: continue
                            val f = File(p)
                            if (f.isFile && f.canRead()) {
                                discoveredByPath.putIfAbsent(f.absolutePath, f)
                            }
                        }
                    }
                }
            }
        }

        val allFiles = discoveredByPath.values.toList()
        val results = ArrayList<ScavengerFileItem>()

        // 1. Leftover App Installers (.apk / .apks / .xapk / .aab)
        for (file in allFiles) {
            val ext = file.extension.lowercase(Locale.US)
            if (ext in INSTALLER_EXTENSIONS && file.length() >= 16 * 1024L) {
                val sub = if (ext == "apk") {
                    describeApkArchiveStatus(pm, file)
                } else {
                    "Split / Bundle Installer (.${ext.uppercase(Locale.US)})"
                }
                results.add(
                    ScavengerFileItem(
                        absolutePath = file.absolutePath,
                        displayName = file.name,
                        sizeBytes = file.length(),
                        category = "INSTALLER",
                        subtitle = sub
                    )
                )
            }
        }

        // 2. Large Space Hogs (>= 50 MB)
        allFiles
            .filter { it.length() >= LARGE_FILE_THRESHOLD_BYTES }
            .sortedByDescending { it.length() }
            .take(50)
            .forEach { file ->
                val parentLabel = file.parentFile?.name ?: "Storage"
                results.add(
                    ScavengerFileItem(
                        absolutePath = file.absolutePath,
                        displayName = file.name,
                        sizeBytes = file.length(),
                        category = "LARGE_FILE",
                        subtitle = "Folder: $parentLabel"
                    )
                )
            }

        // 3. Exact SHA-256 Duplicates (size pre-filter -> streaming SHA-256)
        val duplicatePairs = findDuplicateFilesBySizeAndHash(
            allFiles.filter { it.length() in MIN_DUPLICATE_SIZE_BYTES..MAX_DUPLICATE_HASH_BYTES }
        )
        for ((duplicateFile, originalFile) in duplicatePairs) {
            results.add(
                ScavengerFileItem(
                    absolutePath = duplicateFile.absolutePath,
                    displayName = duplicateFile.name,
                    sizeBytes = duplicateFile.length(),
                    category = "DUPLICATE",
                    subtitle = "Exact SHA-256 copy of ${originalFile.name} (original kept)"
                )
            )
        }

        results.sortedByDescending { it.sizeBytes }
    }

    /**
     * Returns `(duplicateCopy, retainedOriginal)` pairs where files share identical byte length
     * and identical SHA-256 content digest. Retains the oldest file in each hash group as original.
     */
    internal fun findDuplicateFilesBySizeAndHash(files: List<File>): List<Pair<File, File>> {
        val bySize = files.groupBy { it.length() }.values.filter { it.size >= 2 }
        val duplicates = ArrayList<Pair<File, File>>()
        for (sameSizeGroup in bySize) {
            val byHash = LinkedHashMap<String, MutableList<File>>()
            for (f in sameSizeGroup) {
                val hash = computeFileSha256(f) ?: continue
                byHash.getOrPut(hash) { ArrayList() }.add(f)
            }
            for (identicalGroup in byHash.values) {
                if (identicalGroup.size >= 2) {
                    val sorted = identicalGroup.sortedWith(
                        compareBy<File> { it.lastModified() }.thenBy { it.name.length }
                    )
                    val original = sorted.first()
                    for (idx in 1 until sorted.size) {
                        duplicates.add(sorted[idx] to original)
                    }
                }
            }
        }
        return duplicates
    }

    internal fun computeFileSha256(file: File): String? {
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            FileInputStream(file).use { fis ->
                while (true) {
                    val read = fis.read(buf)
                    if (read <= 0) break
                    md.update(buf, 0, read)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()
    }

    private fun describeApkArchiveStatus(pm: PackageManager, apkFile: File): String {
        return runCatching {
            val archiveInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(apkFile.absolutePath, 0)
            } ?: return "Corrupt or unreadable APK archive"

            val pkg = archiveInfo.packageName
            val archiveVer = archiveInfo.versionName ?: "1.0"
            val installedInfo = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.getOrNull()

            if (installedInfo != null) {
                "Already installed ($pkg v${installedInfo.versionName ?: archiveVer}) — safe to delete"
            } else {
                "Not installed ($pkg v$archiveVer)"
            }
        }.getOrDefault("Leftover APK installer")
    }

    private fun collectReadableFilesRecursive(
        dir: File?,
        depth: Int,
        maxDepth: Int,
        out: MutableMap<String, File>
    ) {
        if (dir == null || depth > maxDepth || !dir.exists() || !dir.canRead()) return
        val name = dir.name
        if (name.startsWith(".") || name.equals("Android", ignoreCase = true)) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                collectReadableFilesRecursive(child, depth + 1, maxDepth, out)
            } else if (child.isFile && child.canRead()) {
                out.putIfAbsent(child.absolutePath, child)
            }
        }
    }

    suspend fun deleteScavengerFile(absolutePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(absolutePath)
            if (!target.exists() || !target.isFile) return@runCatching false
            target.delete()
        }.getOrDefault(false)
    }

    /**
     * 1-Tap APK Backup / Extractor Vault: copies an installed app's base APK into
     * `Downloads/Sentinel Backups/<Label>-v<Version>.apk` using scoped MediaStore on Android 10+.
     */
    suspend fun exportInstalledApkToDownloads(
        context: Context,
        entry: InstalledProcessEntry
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val appCtx = context.applicationContext
            val pm = appCtx.packageManager
            val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(entry.packageName, PackageManager.ApplicationInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(entry.packageName, 0)
            }
            val srcFile = File(appInfo.sourceDir)
            require(srcFile.exists() && srcFile.canRead()) { "Base APK not readable" }

            val safeLabel = entry.appLabel
                .replace(Regex("[^a-zA-Z0-9._ -]"), "")
                .trim()
                .replace(Regex("\\s+"), "_")
                .ifBlank { entry.packageName }
            val safeVer = entry.versionName
                .replace(Regex("[^a-zA-Z0-9._-]"), "")
                .ifBlank { "1.0" }
            val fileName = "${safeLabel}-v${safeVer}.apk"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appCtx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "${Environment.DIRECTORY_DOWNLOADS}/Sentinel Backups"
                    )
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("MediaStore Downloads rejected backup entry")
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(srcFile).use { inp -> inp.copyTo(out, bufferSize = 64 * 1024) }
                    } ?: error("Unable to open backup stream")
                    val completeValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }
                    resolver.update(uri, completeValues, null, null)
                } catch (t: Throwable) {
                    runCatching { resolver.delete(uri, null, null) }
                    throw t
                }
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Sentinel Backups"
                )
                dir.mkdirs()
                val dst = File(dir, fileName)
                FileInputStream(srcFile).use { inp ->
                    FileOutputStream(dst).use { out -> inp.copyTo(out, bufferSize = 64 * 1024) }
                }
            }
            "Downloads/Sentinel Backups/$fileName"
        }
    }
}
