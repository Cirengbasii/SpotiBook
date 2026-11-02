package com.spotibook.app.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.BuildConfig
import com.spotibook.app.background.Widgets
import com.spotibook.app.data.Settings
import com.spotibook.app.reader.*

@Composable fun SettingsScreen(model: AppState, permission: () -> Unit, backup: () -> Unit, restore: () -> Unit, csv: () -> Unit) {
    val settings by model.settings.collectAsStateWithLifecycle(); val achievements by model.achievements.collectAsStateWithLifecycle(); val context = LocalContext.current
    var numeric by remember { mutableStateOf<Triple<String, Int, IntRange>?>(null) }; var saveNumber by remember { mutableStateOf<((Int) -> Unit)?>(null) }
    var reset by remember { mutableStateOf(false) }
    fun number(title: String, value: Int, range: IntRange, update: (Settings, Int) -> Settings) { numeric = Triple(title, value, range); saveNumber = { n -> model.preferences { update(it, n) } } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PageTitle("Spotibook", "Pengaturan", "Tampilan, kebiasaan membaca, dan data.")
        Text("Bahasa aplikasi · Bahasa Indonesia", style=MaterialTheme.typography.bodyMedium, color=MaterialTheme.colorScheme.onSurfaceVariant)
        SectionTitle("Tampilan")
        Choices("Tema aplikasi", listOf("System", "Light", "Dark"), settings.appearance) { value -> model.preferences { it.copy(appearance = value) } }
        Text("Palet Coral · warna menyesuaikan tema terang dan gelap",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(); SectionTitle("Preferensi membaca")
        ThemeSwatches(settings.readerTheme) { value -> model.preferences { it.copy(readerTheme=value) } }
        Text("Perpindahan halaman PDF",style=MaterialTheme.typography.titleMedium)
        var modes by remember { mutableStateOf(false) }
        Box { OutlinedButton({modes=true}){Text(settings.pageTurn.title)}; DropdownMenu(modes,{modes=false}) { PageTurn.entries.forEach { mode->DropdownMenuItem(text={Text(mode.title)},onClick={model.preferences{it.copy(pageTurn=mode)};modes=false}) } } }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) { ReaderOrientation.entries.forEach { orientation->FilterChip(settings.readerOrientation==orientation,{model.preferences{it.copy(readerOrientation=orientation)}},label={Text(orientation.title)}) } }
        PreferenceSwitch("Layar tetap menyala","Selama pembaca terbuka.",settings.keepScreenOn){value->model.preferences{it.copy(keepScreenOn=value)}}
        PreferenceSwitch("Tombol volume untuk halaman","Turun: berikutnya. Naik: sebelumnya.",settings.volumeKeys){value->model.preferences{it.copy(volumeKeys=value)}}
        Choices("Jenis huruf", listOf("Serif", "Sans", "Mono"), settings.fontFamily) { value -> model.preferences { it.copy(fontFamily = value) } }
        SettingRow("Ukuran huruf", "${settings.fontSize}") { number("Ukuran huruf", settings.fontSize, 12..36) { s,n -> s.copy(fontSize=n) } }
        Text("Jarak baris · ${"%.1f".format(settings.lineSpacing)}")
        var line by remember(settings.lineSpacing) { mutableFloatStateOf(settings.lineSpacing) }
        Slider(line, { line = it }, valueRange = 1f..2.5f, onValueChangeFinished = { model.preferences { it.copy(lineSpacing = line) } })
        SettingRow("Tepi teks", "${settings.margin}") { number("Tepi teks", settings.margin, 4..48) { s,n -> s.copy(margin=n) } }
        PreferenceSwitch("Ikuti kecerahan sistem", "Kecerahan khusus hanya berlaku saat membaca.", settings.brightness < 0) { checked -> model.preferences { it.copy(brightness = if (checked) -1f else .5f) } }
        if (settings.brightness >= 0) { var brightness by remember(settings.brightness) { mutableFloatStateOf(settings.brightness) }; Slider(brightness, { brightness = it }, valueRange = .02f..1f, onValueChangeFinished = { model.preferences { it.copy(brightness = brightness) } }) }
        HorizontalDivider(); SectionTitle("Target membaca")
        SettingRow("Target harian", "${settings.dailyMinutes} menit") { number("Target harian (menit)", settings.dailyMinutes, 1..1440) { s,n -> s.copy(dailyMinutes=n) } }
        SettingRow("Target mingguan", "${settings.weeklyMinutes} menit") { number("Target mingguan (menit)", settings.weeklyMinutes, 1..10_080) { s,n -> s.copy(weeklyMinutes=n) } }
        SettingRow("Target bulanan", "${settings.monthlyMinutes} menit") { number("Target bulanan (menit)", settings.monthlyMinutes, 1..44_640) { s,n -> s.copy(monthlyMinutes=n) } }
        SettingRow("Target tahunan", "${settings.annualBooks} buku") { number("Target tahunan (buku)", settings.annualBooks, 1..10_000) { s,n -> s.copy(annualBooks=n) } }
        SettingRow("Minimum untuk hari aktif", "${settings.qualifyingMinutes} menit membaca aktif") { number("Minimum membaca harian (menit)", settings.qualifyingMinutes, 1..1440) { s,n -> s.copy(qualifyingMinutes=n) } }
        Text("Membuka aplikasi saja tidak dihitung sebagai membaca. Tanggal mengikuti zona waktu perangkat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider(); SectionTitle("Pengingat")
        PreferenceSwitch("Pengingat harian", "Dikirim jika target harian belum tercapai.", settings.reminder) { enabled -> if (enabled) permission(); model.preferences { it.copy(reminder = enabled) } }
        PreferenceSwitch("Pengingat hari beruntun", "Dikirim pada waktu pengingat jika minimum harian belum tercapai.", settings.streakReminder) { enabled -> if (enabled) permission(); model.preferences { it.copy(streakReminder = enabled) } }
        SettingRow("Waktu pengingat", "%02d:%02d".format(settings.reminderHour, settings.reminderMinute)) { TimePickerDialog(context, { _, h, m -> model.preferences { it.copy(reminderHour=h, reminderMinute=m) } }, settings.reminderHour, settings.reminderMinute, true).show() }
        PreferenceSwitch("Sesi fokus selesai", "Notifikasi saat pengatur waktu berakhir.", settings.focusNotification) { enabled -> if (enabled) permission(); model.preferences { it.copy(focusNotification=enabled) } }
        Text("Penghemat baterai Android dapat menunda pengingat dan notifikasi fokus.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton({ context.startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)) }) { Text("Pengaturan notifikasi Android") }
        HorizontalDivider(); SectionTitle("Data Anda")
        SettingRow("Ekspor riwayat dan statistik", "CSV berisi sesi, durasi, dan halaman", csv)
        SettingRow("Buat cadangan lokal", "ZIP, dengan pilihan menyertakan berkas buku", backup)
        SettingRow("Pulihkan cadangan lokal", "Periksa dan gabungkan dengan pustaka", restore)
        TextButton({ reset = true }) { Text("Hapus statistik membaca…", color = MaterialTheme.colorScheme.error) }
        HorizontalDivider(); SectionTitle("Pencapaian")
        if (achievements.isEmpty()) Text("Belum ada pencapaian.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        achievements.forEach { Text("◇ ${it.name}") }
        HorizontalDivider(); SectionTitle("Tentang Spotibook")
        Text("Spotibook ${BuildConfig.VERSION_NAME} · Edisi pribadi")
        Text("Pembaca buku pribadi tanpa akun, iklan, analitik, atau penyimpanan awan. Semua buku dan riwayat tersimpan di perangkat, kecuali Anda mengekspornya.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Mendukung EPUB tanpa DRM dengan tata letak mengalir, TXT UTF-8, dan HTML mandiri. Pencarian PDF memerlukan Android 15 atau lebih baru. Sorotan teks tersedia untuk EPUB/TXT/HTML; PDF mendukung penanda dan catatan halaman.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Pustaka sumber terbuka: AndroidX (Apache 2.0), Kotlin dan kotlinx (Apache 2.0), jsoup (MIT).", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(20.dp))
    }
    numeric?.let { (title, initial, range) -> TextEntry(title, initial.toString(), true, range, { numeric = null }) { saveNumber?.invoke(it.toInt()) } }
    if (reset) Confirm("Hapus statistik membaca?", "Semua sesi membaca, sesi fokus selesai, dan pencapaian akan dihapus. Buku, posisi baca, rak, dan catatan tetap tersimpan. Buat cadangan lebih dulu jika ingin menyimpan riwayat.", "Hapus statistik", { reset = false }) { reset = false; model.task("Menghapus statistik…") { model.repo.resetStatistics(); Widgets.update(model.app); model.messages.emit("Statistik membaca dihapus") } }
}
@Composable fun Choices(label: String, options: List<String>, selected: String, choose: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { option -> FilterChip(selected == option, { choose(option) }, label = { Text(option.labelId()) }) } }
}
@Composable fun SettingRow(title: String, value: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; Text("›", style = MaterialTheme.typography.titleLarge)
    }
}
@Composable fun PreferenceSwitch(title: String, description: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked, change)
    }
}
