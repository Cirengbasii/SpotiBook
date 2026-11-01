package com.spotibook.app.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

object Images {
    private val cache = object : android.util.LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun decode(file: File, maxPixels: Int = 600): Bitmap? {
        val key = "${file.absolutePath}:${file.lastModified()}:${file.length()}:$maxPixels"
        cache.get(key)?.let { return it }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxPixels * 2 || bounds.outHeight / sample > maxPixels * 2) sample *= 2
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.also { cache.put(key, it) }
    }
}
