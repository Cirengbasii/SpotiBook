package com.spotibook.app.reader

import android.content.pm.ActivityInfo
import kotlinx.serialization.Serializable

@Serializable
enum class PageTurn(val title: String, val description: String) {
    Withdrawal("Withdrawal", "Lembar ditarik dengan tekukan kertas"),
    Overlay("Overlay", "Halaman baru menutupi halaman sebelumnya"),
    Shift("Shift", "Geser halaman ke kanan atau kiri"),
    Scroll("Scroll", "Pilihan lama; dialihkan ke Scrolling"),
    NoAnimation("No Animation", "Ganti halaman langsung, tanpa gerakan"),
    Scrolling("Scrolling", "Gulir atas–bawah tanpa jeda antarhalaman"),
    HorizontalScrolling("Scrolling samping", "Gulir kanan–kiri menyambung antarhalaman");
    companion object { val available get() = entries.filter { it != Scroll } }
}

@Serializable
enum class ReaderOrientation(val title: String, val androidValue: Int) {
    Auto("Otomatis", ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR),
    Portrait("Potret", ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT),
    Landscape("Lanskap", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
}

@Serializable enum class PdfFit(val title: String) { Width("Pas lebar"), Page("Pas halaman") }

/** Dark-mode display matrix. Sepia is handled by PdfSepia so colored pixels are preserved. */
fun pdfColors(theme: String): FloatArray? {
    if (theme != "Dark" && theme != "Black") return null
    val paper = 0x272C30; val ink = 0xE0E3DE
    val result = FloatArray(20)
    for (row in 0..2) {
        val shift = (2 - row) * 8
        val dark = (ink shr shift) and 255
        val light = (paper shr shift) and 255
        result[row*5] = (light-dark)*.2126f/255
        result[row*5+1] = (light-dark)*.7152f/255
        result[row*5+2] = (light-dark)*.0722f/255
        result[row*5+4] = dark.toFloat()
    }
    result[18] = 1f
    return result
}
