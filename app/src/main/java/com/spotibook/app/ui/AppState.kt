package com.spotibook.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spotibook.app.SpotibookApp
import com.spotibook.app.background.*
import com.spotibook.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class AppState(val app: SpotibookApp) : ViewModel() {
    val repo = app.repository
    val books = repo.dao.books().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sessions = repo.dao.sessions().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val shelves = repo.dao.shelves().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val memberships = repo.dao.memberships().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val annotations = repo.dao.annotations().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val achievements = repo.dao.achievements().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings = repo.preferences.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val focus = repo.preferences.focus.stateIn(viewModelScope, SharingStarted.Eagerly, FocusState())
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val busy = MutableStateFlow<String?>(null)
    fun task(label: String? = null, work: suspend () -> Unit) { viewModelScope.launch {
        if (label != null && busy.value != null) return@launch
        if (label != null) busy.value = label
        try { withContext(Dispatchers.IO) { work() } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.e("Spotibook", "Operation failed", e); messages.emit(e.message ?: "Terjadi kesalahan. Silakan coba lagi.") }
        finally { if (label != null) busy.value = null }
    } }
    fun preferences(update: (Settings) -> Settings) = task {
        val before = repo.preferences.settings.first()
        repo.preferences.update(update)
        val after = repo.preferences.settings.first()
        if (listOf(before.reminder,before.streakReminder,before.reminderHour,before.reminderMinute) != listOf(after.reminder,after.streakReminder,after.reminderHour,after.reminderMinute)) Reminders.schedule(app,after)
        if (before.dailyMinutes != after.dailyMinutes || before.qualifyingMinutes != after.qualifyingMinutes) Widgets.update(app)
    }
    companion object { fun factory(app: SpotibookApp) = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = AppState(app) as T
    } }
}
