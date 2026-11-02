package com.spotibook.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.spotibook.app.reader.*

private val Context.settingsStore by preferencesDataStore("spotibook_settings")
@Serializable data class Settings(
    val appearance: String = "System", val accent: String = "Ocean", val readerTheme: String = "Sepia",
    val fontSize: Int = 19, val fontFamily: String = "Serif", val lineSpacing: Float = 1.7f, val margin: Int = 22,
    val brightness: Float = -1f, val dailyMinutes: Int = 30, val weeklyMinutes: Int = 210,
    val monthlyMinutes: Int = 900, val annualBooks: Int = 12, val qualifyingMinutes: Int = 5,
    val reminder: Boolean = false, val reminderHour: Int = 20, val reminderMinute: Int = 0,
    val streakReminder: Boolean = false, val focusNotification: Boolean = true,
    val focusSound: String = "Gentle", val focusVolume: Float = .35f, val focusSoundName: String = "",
    val grid: Boolean = true,
    val pageTurn: PageTurn = PageTurn.Scrolling,
    val readerOrientation: ReaderOrientation = ReaderOrientation.Auto,
    val pdfFit: PdfFit = PdfFit.Width,
    val pdfMargin: Int = 8,
    val keepScreenOn: Boolean = true,
    val volumeKeys: Boolean = false,
    val tapToTurn: Boolean = false
)
/** Normalize retired choices at the persistence boundary, including restored backups. */
fun Settings.normalized() = copy(
    readerTheme = if (readerTheme == "Black") "Dark" else readerTheme,
    accent = if (accent == "Forest") "Ocean" else accent,
    pageTurn = if (pageTurn == PageTurn.Scroll) PageTurn.Scrolling else pageTurn
)

@Serializable data class FocusState(val id: String = "", val bookId: String? = null, val plannedMillis: Long = 0,
    val remainingMillis: Long = 0, val deadlineElapsed: Long = 0, val boot: Int = -1,
    val running: Boolean = false, val done: Boolean = false, val started: Long = 0, val finished: Long = 0)
class Preferences(context: Context) {
    private val store = context.settingsStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val settingsKey = stringPreferencesKey("settings")
    private val focusKey = stringPreferencesKey("focus")
    val settings = store.data.map { (it[settingsKey]?.let { raw -> runCatching { json.decodeFromString<Settings>(raw) }.getOrNull() } ?: Settings()).normalized() }
    val focus = store.data.map { it[focusKey]?.let { raw -> runCatching { json.decodeFromString<FocusState>(raw) }.getOrNull() } ?: FocusState() }
    suspend fun update(transform: (Settings) -> Settings) { store.edit { p ->
        val current = p[settingsKey]?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() } ?: Settings()
        p[settingsKey] = json.encodeToString(transform(current.normalized()).normalized())
    } }
    suspend fun setFocus(focus: FocusState) { store.edit { it[focusKey] = json.encodeToString(focus) } }
}
