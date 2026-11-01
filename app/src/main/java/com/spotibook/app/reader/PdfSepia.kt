package com.spotibook.app.reader

import android.graphics.Bitmap
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Only neutral pixels are tinted. Every RGB pixel with any chroma stays bit-for-bit original. */
object PdfSepia {
    private val neutrals = IntArray(256) { value ->
        fun channel(ink: Int, paper: Int) = ink + ((paper - ink) * value + 127) / 255
        (channel(48, 241) shl 16) or (channel(44, 229) shl 8) or channel(37, 203)
    }
    fun color(argb: Int): Int {
        val r = argb ushr 16 and 255; val g = argb ushr 8 and 255; val b = argb and 255
        return if (r == g && g == b) (argb and 0xff000000.toInt()) or neutrals[r] else argb
    }
    // Bounded working buffer, in-place on the private display bitmap, never on the PDF file.
    suspend fun apply(bitmap: Bitmap) {
        val pixels = IntArray(bitmap.width * minOf(32, bitmap.height))
        for (y in 0 until bitmap.height step 32) {
            currentCoroutineContext().ensureActive()
            val rows = minOf(32, bitmap.height - y)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, rows)
            for (i in 0 until bitmap.width * rows) pixels[i] = color(pixels[i])
            bitmap.setPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, rows)
        }
    }
}
