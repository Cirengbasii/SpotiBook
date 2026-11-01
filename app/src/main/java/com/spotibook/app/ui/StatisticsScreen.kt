package com.spotibook.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.data.Session
import com.spotibook.app.domain.Reading
import com.spotibook.app.domain.recentHistory
import java.time.*
import java.time.format.DateTimeFormatter

private fun periodStart(period: String, today: LocalDate): LocalDate = when(period) {
    "Today" -> today; "Yesterday" -> today.minusDays(1); "Week" -> today.minusDays(6)
    "Month" -> today.withDayOfMonth(1); "Year" -> today.withDayOfYear(1); else -> LocalDate.of(1970, 1, 1)
}
@Composable fun StatisticsScreen(model: AppState, navigate: (String) -> Unit) {
    val books by model.books.collectAsStateWithLifecycle(); val sessions by model.sessions.collectAsStateWithLifecycle(); val prefs by model.settings.collectAsStateWithLifecycle(); val badges by model.achievements.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf("Week") }; val today = LocalDate.now()
    val daily = sessions.groupBy { LocalDate.parse(it.date) }.mapValues { (_, v) -> v.sumOf { it.millis } }; val streak = Reading.streak(daily, today, prefs.qualifyingMinutes)
    val filtered = sessions.filter { LocalDate.parse(it.date) >= periodStart(period, today) && LocalDate.parse(it.date) <= today }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageTitle("Riwayat membaca", "Statistik", "Ringkasan waktu, buku, dan kebiasaan membaca.") }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Today", "Week", "Month", "Year", "All time").forEach { p -> FilterChip(period == p, { period = p }, label = { Text(p.labelId()) }) } } }
        item { Row(Modifier.fillMaxWidth()) { Metric(minutes(filtered.sumOf { it.millis }), "waktu membaca", Modifier.weight(1f)); Metric(filtered.map { it.id.substringBefore('/') }.distinct().size.toString(), "sesi", Modifier.weight(1f)); Metric(filtered.sumOf { it.pages }.toString(), "halaman PDF maju", Modifier.weight(1f)) } }
        item { HorizontalDivider(); Row { Metric(books.size.toString(), "buku", Modifier.weight(1f)); Metric(books.count { it.status == "Reading" }.toString(), "sedang dibaca", Modifier.weight(1f)); Metric(books.count { it.status == "Finished" }.toString(), "selesai", Modifier.weight(1f)) } }
        item { SectionTitle("Waktu membaca")
            val count = when(period) { "Today" -> 1; "Week" -> 7; "Month" -> today.dayOfMonth; else -> 12 }
            val bars = if (period in listOf("Year", "All time")) (1..12).map { month -> YearMonth.of(today.year, month).month.getDisplayName(java.time.format.TextStyle.SHORT, Indonesian) to daily.filter { it.key.year == today.year && it.key.monthValue == month }.values.sum() }
                else (count-1 downTo 0).map { day -> val date = today.minusDays(day.toLong()); date.format(DateTimeFormatter.ofPattern(if (count <= 7) "EEE" else "d")) to (daily[date] ?: 0) }
            ReadingBars(bars)
            if (filtered.isEmpty()) Text("Belum ada waktu membaca dalam rentang ini.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { SectionTitle("Target membaca")
            GoalLine("Today", daily[today] ?: 0, prefs.dailyMinutes)
            GoalLine("7 hari terakhir", daily.filter { it.key >= today.minusDays(6) && it.key <= today }.values.sum(), prefs.weeklyMinutes)
            GoalLine("Bulan ini", daily.filter { it.key.year == today.year && it.key.month == today.month }.values.sum(), prefs.monthlyMinutes)
            val finished = books.count { it.completed?.let { epoch -> Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).year == today.year } == true }
            Text("$finished / ${prefs.annualBooks} buku tahun ini", Modifier.padding(top = 10.dp)); LinearProgressIndicator({ (finished.toFloat() / prefs.annualBooks).coerceIn(0f,1f) }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        item { SectionTitle("Hari membaca", "Kalender") { navigate("calendar") }; Row { Metric(streak.current.toString(), "hari beruntun", Modifier.weight(1f)); Metric(streak.longest.toString(), "rekor beruntun", Modifier.weight(1f)); Metric(streak.totalDays.toString(), "hari aktif", Modifier.weight(1f)) }; YearHeatmap(daily, prefs.qualifyingMinutes) { navigate("calendar?day=$it") } }
        item { SectionTitle("Buku yang sering dibaca") }
        filtered.groupBy { it.bookId }.entries.sortedByDescending { it.value.sumOf { s -> s.millis } }.take(8).forEach { (id, history) -> item(id) {
            val book = books.firstOrNull { it.id == id }
            ListItem(headlineContent = { Text(book?.title ?: history.first().bookTitle) }, supportingContent = { Text("${history.map { it.id.substringBefore('/') }.distinct().size} sesi · ${book?.let { "${(it.progress*100).toInt()}%" } ?: "Dihapus dari pustaka"}") }, trailingContent = { Text(minutes(history.sumOf { it.millis })) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
        } }
        item { SectionTitle("Buku selesai per bulan"); ReadingBars((1..12).map { month -> YearMonth.of(today.year, month).month.getDisplayName(java.time.format.TextStyle.SHORT, Indonesian) to books.count { b -> b.completed?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().let { d -> d.year == today.year && d.monthValue == month } } == true }.toLong() }, durations = false) }
        item { SectionTitle("Pencapaian")
            if (badges.isEmpty()) Text("Belum ada pencapaian.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            badges.forEach { badge -> Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Icon(Icons.Outlined.WorkspacePremium, null, tint = MaterialTheme.colorScheme.tertiary); Column { Text(badge.name.labelId()); Text(dateLabel(badge.unlocked), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        }
    }
}
@Composable private fun GoalLine(label: String, read: Long, target: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label.labelId()); Text("${read / 60_000} / $target menit", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    LinearProgressIndicator({ Reading.goal(read, target) }, Modifier.fillMaxWidth().padding(top = 8.dp))
}
@Composable private fun ReadingBars(values: List<Pair<String, Long>>, durations: Boolean = true) {
    val maximum = (values.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(166.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Bottom) {
        values.forEach { (label, value) -> Column(Modifier.width(if (values.size <= 7) 35.dp else 28.dp).semantics { contentDescription = "$label: ${if (durations) minutes(value) else "$value buku"}" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            if (values.size <= 7) Text(if (durations) "${value/60_000}" else "$value", style = MaterialTheme.typography.labelSmall)
            Box(Modifier.width(18.dp).height((112f * value / maximum).coerceAtLeast(2f).dp).background(if (value == 0L) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary))
            Text(label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
        } }
    }
}
@Composable fun YearHeatmap(daily: Map<LocalDate, Long>, threshold: Int, tap: (LocalDate) -> Unit) {
    val today = LocalDate.now(); val first = today.withDayOfYear(1); val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("${today.year} · hari membaca", style = MaterialTheme.typography.bodySmall)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            (0..53).forEach { week -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                (0..6).forEach { day -> val date = start.plusDays((week * 7 + day).toLong()); val millis = daily[date] ?: 0
                    val color = if (date.year != today.year || date > today) Color.Transparent else if (millis >= threshold * 60_000L) MaterialTheme.colorScheme.primary else if (millis > 0) MaterialTheme.colorScheme.secondary.copy(alpha = .5f) else MaterialTheme.colorScheme.surfaceVariant
                    Box(Modifier.size(12.dp).background(color).semantics { contentDescription = "$date: ${minutes(millis)}" }.then(if (date.year == today.year && date <= today) Modifier.clickable { tap(date) } else Modifier))
                }
            } }
        }
        Text("Pucat: sempat membaca · Pekat: minimum harian tercapai", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun HistoryScreen(model: AppState, back: () -> Unit, open: (String) -> Unit) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(LocalDate.now()) }
    var remove by remember { mutableStateOf<Set<String>?>(null) }
    LaunchedEffect(Unit) { model.repo.expireHistory();while(true){today=LocalDate.now();kotlinx.coroutines.delay(30_000)} }
    val records=recentHistory(sessions,today)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { TextButton(back){Text("← Kembali")};PageTitle("Riwayat","30 hari terakhir","Statistik dan kalender tetap tersimpan saat daftar riwayat dibersihkan.")
            OutlinedButton({remove=records.map{it.id}.toSet()},enabled=records.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.DeleteOutline,null);Spacer(Modifier.width(8.dp));Text("Bersihkan riwayat")}
        }
        if(records.isEmpty())item{EmptyState("Belum ada riwayat","Sesi membaca selama 30 hari terakhir akan muncul di sini.")}
        records.groupBy{it.date}.forEach{(day,entries)->item("date-$day"){SectionTitle(LocalDate.parse(day).format(DateTimeFormatter.ofPattern("EEEE, d MMMM",Indonesian)))};items(entries,key={it.id}){entry->HistoryRow(entry,model,open){remove=setOf(entry.id)}}}
    }
    remove?.let{ids->Confirm("Hapus riwayat?","${ids.size} entri dihapus dari daftar. Waktu baca, kalender, dan statistik tetap tersimpan.","Hapus",{remove=null}){remove=null;model.task{model.repo.hideHistory(ids)}}}
}
@Composable private fun SessionRow(session: Session, model: AppState, open: (String) -> Unit) {
    val books by model.books.collectAsStateWithLifecycle(); val exists = books.any { it.id == session.bookId }
    ListItem(headlineContent = { Text(session.bookTitle) }, supportingContent = { Text("${dateLabel(session.start)} · posisi ${session.startPosition + 1}–${session.endPosition + 1}${if (!exists) " · Buku dihapus" else ""}") }, trailingContent = { Text(minutes(session.millis)) }, modifier = Modifier.clickable(enabled = exists) { model.task { model.repo.position(session.bookId, session.endPosition, 0f); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { open(session.bookId) } } }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
}
@Composable fun CalendarScreen(model: AppState, back: () -> Unit, open: (String) -> Unit, initialDay: String = "") {
    val sessions by model.sessions.collectAsStateWithLifecycle(); val prefs by model.settings.collectAsStateWithLifecycle()
    var selectedDay by rememberSaveable(initialDay) { mutableStateOf(runCatching { LocalDate.parse(initialDay) }.getOrDefault(LocalDate.now()).toString()) }
    val selected = LocalDate.parse(selectedDay)
    val daily = sessions.groupBy { LocalDate.parse(it.date) }.mapValues { (_, v) -> v.sumOf { it.millis } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { TextButton(back) { Text("← Kembali") }; PageTitle("Riwayat", "Kalender baca") }
        item { ReadingCalendar(model, open, selected) { selectedDay = it.toString() } }
        items(sessions.filter { it.date == selectedDay }, key = { it.id }) { SessionRow(it, model, open) }
        item { SectionTitle("Aktivitas tahunan"); YearHeatmap(daily, prefs.qualifyingMinutes) { selectedDay = it.toString() } }
    }
}
