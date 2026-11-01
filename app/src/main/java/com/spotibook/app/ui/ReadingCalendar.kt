package com.spotibook.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.data.*
import java.time.*
import java.time.format.DateTimeFormatter

internal data class CalendarBook(val id: String, val title: String, val book: Book?, val millis: Long, val sessions: Int)
internal fun calendarBooks(sessions: List<Session>, books: List<Book>): Map<LocalDate, List<CalendarBook>> {
    val byId = books.associateBy { it.id }
    return sessions.filter { it.millis > 0 }.groupBy { LocalDate.parse(it.date) }.mapValues { (_, day) ->
        day.groupBy { it.bookId }.map { (id, records) ->
            CalendarBook(id, records.first().bookTitle, byId[id], records.sumOf { it.millis }, records.map { it.id.substringBefore('/') }.distinct().size)
        }.sortedByDescending { it.millis }
    }
}

/** The same cover calendar is used on Home and in the full history view. Dates use the session's recorded local date. */
@Composable fun ReadingCalendar(model: AppState, open: (String) -> Unit, selected: LocalDate, select: (LocalDate) -> Unit) {
    val sessions by model.sessions.collectAsStateWithLifecycle()
    val books by model.books.collectAsStateWithLifecycle()
    val days = remember(sessions, books) { calendarBooks(sessions, books) }
    val month = YearMonth.from(selected)
    val records = days[selected].orEmpty()
    val monthDays = days.filterKeys { YearMonth.from(it) == month }
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Kalender membaca", style = MaterialTheme.typography.titleLarge)
                    Text("${monthDays.size} hari · ${monthDays.values.flatten().map { it.id }.distinct().size} buku bulan ini", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ select(selected.minusMonths(1)) }) { Icon(Icons.Outlined.ChevronLeft, "Bulan sebelumnya") }
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Indonesian)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                IconButton({ select(selected.plusMonths(1)) }) { Icon(Icons.Outlined.ChevronRight, "Bulan berikutnya") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min").forEach {
                Text(it, Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } }
            val lead = month.atDay(1).dayOfWeek.value - 1
            repeat((lead + month.lengthOfMonth() + 6) / 7) { week ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                    repeat(7) { weekday ->
                        val number = week * 7 + weekday - lead + 1
                        if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(88.dp))
                        else {
                            val date = month.atDay(number); val entries = days[date].orEmpty(); val picked = date == selected
                            Column(Modifier.weight(1f).heightIn(min = 88.dp).padding(2.dp)
                                .clip(RoundedCornerShape(10.dp)).background(if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .clickable { select(date) }.testTag("calendar-day-$date")
                                .semantics(mergeDescendants = true) { contentDescription = "${date.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Indonesian))}: ${if(entries.isEmpty()) "belum membaca" else entries.joinToString { it.title }}" },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("$number", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium, color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                BoxWithConstraints(Modifier.fillMaxWidth().height(59.dp), contentAlignment = Alignment.TopCenter) {
                                    val coverWidth = minOf(38.dp, maxWidth - 5.dp)
                                    if (entries.isNotEmpty()) {
                                        val first = entries.first()
                                        if (first.book != null) BookCover(first.book, model, Modifier.width(coverWidth).height(56.dp), thumbnail = true)
                                        else Box(Modifier.width(coverWidth).height(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Book, null, Modifier.size(18.dp)) }
                                        if (entries.size > 1) Surface(shape = CircleShape, color = ReadingGold, modifier = Modifier.align(Alignment.BottomEnd)) { Text("+${entries.size - 1}", Modifier.padding(horizontal = 4.dp), color = androidx.compose.ui.graphics.Color(0xFF45350B), style = MaterialTheme.typography.labelSmall) }
                                    } else Box(Modifier.width(22.dp).height(3.dp).padding(top = 1.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(selected.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Indonesian)), Modifier.padding(horizontal = 18.dp), style = MaterialTheme.typography.titleMedium)
            if (records.isEmpty()) Text("Belum ada sesi membaca pada tanggal ini.", Modifier.padding(horizontal = 18.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                Text("${minutes(records.sumOf { it.millis })} · ${records.size} buku", Modifier.padding(horizontal = 18.dp, vertical = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(records, key = { it.id }) { entry ->
                        Column(Modifier.width(100.dp).clickable(enabled = entry.book != null) { open(entry.id) }.testTag("calendar-book-${entry.id}"), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            if (entry.book != null) BookCover(entry.book, model, Modifier.width(100.dp).height(145.dp))
                            else Box(Modifier.width(100.dp).height(145.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Book, null) }
                            Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium)
                            Text(if(entry.book == null) "Buku dihapus" else minutes(entry.millis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
