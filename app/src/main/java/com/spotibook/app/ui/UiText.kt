package com.spotibook.app.ui

import java.util.Locale

val Indonesian: Locale = Locale.forLanguageTag("id-ID")

// Translate display labels, not persisted keys: existing books and backups remain compatible.
fun String.labelId(): String = when (this) {
    "System" -> "Ikuti sistem"; "Light" -> "Terang"; "Dark" -> "Gelap"; "Black" -> "Hitam"; "Sepia" -> "Sepia"
    "Ocean" -> "Biru laut"
    "Forest" -> "Hijau"; "Olive" -> "Zaitun"; "Amber" -> "Cokelat"
    "Serif" -> "Serif"; "Sans" -> "Sans serif"; "Mono" -> "Monospace"
    "To read" -> "Akan dibaca"; "Reading" -> "Sedang dibaca"; "Finished" -> "Selesai"
    "All books" -> "Semua buku"; "Recently opened" -> "Terakhir dibuka"; "Recently added" -> "Baru ditambahkan"
    "Title A–Z" -> "Judul A–Z"; "Author" -> "Penulis"; "Progress" -> "Progres"; "Finished first" -> "Selesai lebih dulu"
    "Bookmark" -> "Penanda"; "Highlight" -> "Sorotan"; "Note" -> "Catatan"
    "Unknown author" -> "Penulis tidak diketahui"; "Library" -> "Buku"; "Star" -> "Bintang"; "Study" -> "Belajar"; "Leaf" -> "Daun"
    "Today" -> "Hari ini"; "Yesterday" -> "Kemarin"; "Week" -> "Minggu ini"; "Month" -> "Bulan ini"; "Year" -> "Tahun ini"; "All time" -> "Semua"
    "First chapter" -> "Sesi pertama"; "First five" -> "Lima hari membaca"; "Consistent reader" -> "7 hari beruntun"
    "Dedicated reader" -> "30 hari beruntun"; "Bookworm" -> "5 buku selesai"; "Scholar" -> "10 buku selesai"
    "Deep focus" -> "Fokus 60 menit"; "Night owl" -> "Membaca malam"; "Early bird" -> "Membaca pagi"
    else -> this
}
