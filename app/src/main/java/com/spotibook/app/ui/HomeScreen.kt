package com.spotibook.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.domain.Reading
import com.spotibook.app.domain.recentHistory
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun HomeScreen(model: AppState, open: (String) -> Unit, import: () -> Unit, navigate: (String) -> Unit) {
    val books by model.books.collectAsStateWithLifecycle()
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(LocalDate.now()) }
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    var removeHistory by remember { mutableStateOf<String?>(null) }
    var calendarDay by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now(); hour = LocalTime.now().hour; kotlinx.coroutines.delay(30_000) } }
    val daily = remember(sessions) { sessions.groupBy { LocalDate.parse(it.date) }.mapValues { (_, records) -> records.sumOf { it.millis } } }
    val streak = Reading.streak(daily, today, settings.qualifyingMinutes)
    val read = daily[today] ?: 0
    val current = books.firstOrNull { it.status == "Reading" } ?: books.firstOrNull { it.status == "To read" } ?: books.firstOrNull()
    val blue = MaterialTheme.colorScheme.primary
    LazyColumn(Modifier.fillMaxSize().testTag("home-list"), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        item {
            Column(Modifier.fillMaxWidth().drawBehind {
                val path = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - 85.dp.toPx())
                    cubicTo(size.width * .7f, size.height - 10.dp.toPx(), size.width * .25f, size.height - 80.dp.toPx(), 0f, size.height - 55.dp.toPx()); close()
                }; drawPath(path, blue)
            }.padding(horizontal = 22.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Spotibook", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimary)
                    FilledIconButton({ navigate("settings") }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = .16f), contentColor = MaterialTheme.colorScheme.onPrimary), modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Tune, "Pengaturan") }
                }
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(when(hour) { in 5..10 -> "Selamat pagi :)"; in 11..14 -> "Selamat siang :)"; in 15..17 -> "Selamat sore :)"; else -> "Selamat malam :)" }, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onPrimary)
                    Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Indonesian)), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.bodyMedium)
                }
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column {
                        Box(Modifier.fillMaxWidth().height(7.dp).background(ReadingGold))
                        if (current == null) Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Icon(Icons.Outlined.AutoStories, null, Modifier.size(38.dp), tint = blue)
                            Text("Tambahkan buku pertama", style = MaterialTheme.typography.titleLarge)
                            Text("Pilih PDF, EPUB, TXT, atau HTML dari perangkat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(import, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Impor buku") }
                        } else Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.secondary)
                                Text(if(current.status == "Finished") "Baca lagi" else "Sedang dibaca", Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.labelLarge, color = blue)
                                Text("${(current.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, color = blue)
                            }
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val coverWidth = minOf(132.dp, maxWidth * .34f)
                                Row(horizontalArrangement = Arrangement.spacedBy(17.dp), verticalAlignment = Alignment.CenterVertically) {
                                    BookCover(current, model, Modifier.width(coverWidth).height(coverWidth * 1.5f).clickable { open(current.id) })
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Text(current.title, style = MaterialTheme.typography.titleLarge, maxLines = 4, overflow = TextOverflow.Ellipsis)
                                        Text(current.author.labelId(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                        Text("${if(current.format == "PDF") "Halaman" else "Bab"} ${current.position + 1} / ${current.total}", style = MaterialTheme.typography.labelMedium, color = blue)
                                    }
                                }
                            }
                            LinearProgressIndicator({ current.progress }, Modifier.fillMaxWidth().height(6.dp), color = blue, trackColor = MaterialTheme.colorScheme.primaryContainer)
                            Button({ open(current.id) }, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Icon(Icons.Outlined.MenuBook, null); Spacer(Modifier.width(9.dp)); Text("Buka buku") }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (current != null) FilledTonalButton(import, Modifier.weight(1f).heightIn(min = 58.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(6.dp)); Text("Impor buku") }
                FilledTonalButton({ navigate("focus") }, Modifier.weight(1f).heightIn(min = 58.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Icon(Icons.Outlined.Timelapse, null); Spacer(Modifier.width(7.dp)); Text("Waktu fokus") }
            }
        }
        item {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator({ Reading.goal(read, settings.dailyMinutes) }, Modifier.fillMaxSize(), strokeWidth = 5.dp, trackColor = MaterialTheme.colorScheme.primaryContainer)
                        Text("${(Reading.goal(read, settings.dailyMinutes) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = blue)
                    }
                    Column(Modifier.weight(1f)) { Text("Bacaan hari ini", style = MaterialTheme.typography.titleMedium); Text("${read / 60_000} dari ${settings.dailyMinutes} menit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        item { Box(Modifier.padding(horizontal = 16.dp)) { ReadingCalendar(model, open, LocalDate.parse(calendarDay)) { calendarDay = it.toString() } } }
        item { Box(Modifier.padding(horizontal=20.dp)) {
            StreakCalendar(daily,today,settings.qualifyingMinutes,{date->navigate("calendar?day=$date")}) {
                if(!com.spotibook.app.background.Widgets.pinStreak(model.app))model.messages.tryEmit("Tambahkan dari menu Widget di layar utama, lalu pilih Spotibook · Hari beruntun.")
            }
        } }
        val queue = books.filter { it.status == "To read" && it.id != current?.id }.take(8)
        if (queue.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 20.dp)) { SectionTitle("Buku untuk nanti", "Pustaka") { navigate("library") } } }
            item { LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(queue, key = { it.id }) { book ->
                Column(Modifier.width(108.dp).clickable { open(book.id) }, verticalArrangement = Arrangement.spacedBy(8.dp)) { BookCover(book, model, Modifier.width(108.dp).height(158.dp)); Text(book.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium) }
            } } }
        }
        item { Box(Modifier.padding(horizontal = 20.dp).clickable { navigate("history") }) { SectionTitle("Riwayat terbaru", "Lihat semua") { navigate("history") } } }
        val recent = recentHistory(sessions,today).take(3)
        if (recent.isEmpty()) item { Text("Sesi membaca Anda akan tercatat di sini.", Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else items(recent, key = { it.id }) { session ->
            Box(Modifier.padding(horizontal=16.dp)) {HistoryRow(session,model,open){removeHistory=session.id}}
        }
    }
    removeHistory?.let { id->Confirm("Hapus dari riwayat?","Entri ini hilang dari daftar riwayat. Statistik dan kalender membaca tetap tersimpan.","Hapus",{removeHistory=null}) {removeHistory=null;model.task{model.repo.hideHistory(setOf(id))}} }

}
