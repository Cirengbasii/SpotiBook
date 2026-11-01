package com.spotibook.app.reader

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.File
import java.nio.file.Files

internal data class DeviceBook(val file: File, val size: Long, val modified: Long) {
    val name get() = file.nameWithoutExtension
    val format get() = file.extension.uppercase(java.util.Locale.ROOT)
}
internal object DeviceBooks {
    fun allowed(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    fun roots(context: Context): List<File> = if (Build.VERSION.SDK_INT >= 30)
        context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { it.directory }.distinctBy { it.absolutePath }
        else listOf(Environment.getExternalStorageDirectory())

    // Never follows links outside a volume, enters other apps' private storage, or reads file contents during discovery.
    suspend fun scan(roots: List<File>, progress: (Int) -> Unit = {}): List<DeviceBook> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, DeviceBook>()
        val visited = hashSetOf<String>()
        roots.forEach { volume ->
            val root = volume.canonicalFile
            val queue = java.util.ArrayDeque<File>(); queue.add(root)
            while (queue.isNotEmpty()) {
                ensureActive()
                val dir = queue.removeFirst()
                if (!visited.add(dir.absolutePath)) continue
                val children = try { dir.listFiles() } catch (_: SecurityException) { null } ?: continue
                for (file in children) {
                    ensureActive()
                    if (Files.isSymbolicLink(file.toPath())) continue
                    if (file.isDirectory) {
                        if (!(dir.name == "Android" && file.name in setOf("data", "obb"))) queue.add(file)
                    } else if (file.isFile && file.extension.lowercase(java.util.Locale.ROOT) in setOf("pdf", "epub") && file.length() > 0) {
                        found[file.absolutePath] = DeviceBook(file, file.length(), file.lastModified())
                        if (found.size % 25 == 0) progress(found.size)
                    }
                }
                yield()
            }
        }
        progress(found.size)
        found.values.sortedWith(compareByDescending<DeviceBook> { it.modified }.thenBy { it.file.name.lowercase() })
    }
}
