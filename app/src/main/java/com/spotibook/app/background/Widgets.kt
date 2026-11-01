package com.spotibook.app.background

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.action.*
import androidx.glance.appwidget.action.*
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.spotibook.app.R
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.spotibook.app.MainActivity
import com.spotibook.app.SpotibookApp
import com.spotibook.app.domain.Reading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate

open class ReadingWidget(private val kind: Int) : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val stateDefinition = PreferencesGlanceStateDefinition
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as SpotibookApp).repository
        val books = repo.dao.allBooks(); val sessions = repo.dao.allSessions(); val prefs = repo.preferences.settings.first()
        val today = LocalDate.now(); val daily = sessions.groupBy { LocalDate.parse(it.date) }.mapValues { (_, v) -> v.sumOf { it.millis } }
        val streak = Reading.streak(daily, today, prefs.qualifyingMinutes)
        val book = books.filter { it.status == "Reading" }.maxByOrNull { it.opened }
        val bitmap = withContext(Dispatchers.IO) { book?.takeIf { kind == 2 }?.cover?.let { path ->
            runCatching { com.spotibook.app.reader.Images.decode(com.spotibook.app.reader.SafeFiles.child(repo.documents.directory(book.id), path), 256) }.getOrNull()
        } }
        provideContent {
            val ivory = ColorProvider(Color(0xFFFFFCF6)); val gold = ColorProvider(Color(0xFFF3C64B))
            val destination = if (kind == 2 && book != null) "reader/${book.id}" else if (kind == 3) "library" else "home"
            if (kind == 0) {
                StreakWidgetContent(context, today, daily, prefs.qualifyingMinutes, streak.current, streak.longest)
            } else Column(GlanceModifier.fillMaxSize().background(Color(0xFF2F7299)).cornerRadius(24.dp).padding(18.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java).putExtra("destination", destination)))) {
                Text("SPOTIBOOK", style = TextStyle(color = gold, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(10.dp))
                when (kind) {
                    0 -> { Text("${streak.current} hari beruntun", style = TextStyle(color = ivory, fontSize = 25.sp, fontWeight = FontWeight.Bold)); Text("Terpanjang · ${streak.longest} hari", style = TextStyle(color = ivory, fontSize = 13.sp)); Text("Aktivitas membaca", style = TextStyle(color = gold, fontSize = 12.sp)) }
                    1 -> { Text("${(daily[today] ?: 0) / 60_000} / ${prefs.dailyMinutes} menit", style = TextStyle(color = ivory, fontSize = 25.sp, fontWeight = FontWeight.Bold)); Spacer(GlanceModifier.height(10.dp)); LinearProgressIndicator(Reading.goal(daily[today] ?: 0, prefs.dailyMinutes), GlanceModifier.fillMaxWidth(), color = gold, backgroundColor = ColorProvider(Color(0xFF538EB1))); Text("Target membaca hari ini", style = TextStyle(color = ivory, fontSize = 13.sp)) }
                    2 -> { Row {
                        if (bitmap != null) { Image(ImageProvider(bitmap), "Sampul buku", GlanceModifier.width(44.dp).height(65.dp)); Spacer(GlanceModifier.width(10.dp)) }
                        Column { Text(book?.title ?: "Belum ada buku aktif", maxLines = 2, style = TextStyle(color = ivory, fontSize = 18.sp, fontWeight = FontWeight.Bold)); Text(book?.let { "${(it.progress * 100).toInt()}% · Lanjut membaca" } ?: "Impor buku dari pustaka", style = TextStyle(color = gold, fontSize = 12.sp)) }
                    } }
                    else -> { Text("Akses cepat", style = TextStyle(color = ivory, fontSize = 20.sp, fontWeight = FontWeight.Bold)); Spacer(GlanceModifier.height(8.dp)); Row { Text("Buka pustaka", GlanceModifier.padding(6.dp), style = TextStyle(color = ivory)); Text("Mulai fokus", GlanceModifier.padding(6.dp).clickable(actionStartActivity(Intent(context, MainActivity::class.java).putExtra("destination", "focus"))), style = TextStyle(color = gold)) } }
                }
            }
        }
    }
}
class StreakWidget : ReadingWidget(0)
class GoalWidget : ReadingWidget(1)
class BookWidget : ReadingWidget(2)
class QuickWidget : ReadingWidget(3)
class StreakReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = StreakWidget() }
class GoalReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = GoalWidget() }
class BookReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = BookWidget() }
class QuickReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = QuickWidget() }
object Widgets {
    fun pinStreak(context: Context): Boolean {
        val manager=android.appwidget.AppWidgetManager.getInstance(context)
        return manager.isRequestPinAppWidgetSupported && manager.requestPinAppWidget(android.content.ComponentName(context,StreakReceiver::class.java),null,null)
    }
    suspend fun update(context: Context) { listOf(StreakWidget(), GoalWidget(), BookWidget(), QuickWidget()).forEach { widget -> runCatching { widget.updateAll(context) } } }
}
