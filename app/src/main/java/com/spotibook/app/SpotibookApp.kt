package com.spotibook.app

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.util.Locale
import com.spotibook.app.data.*
import com.spotibook.app.background.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

class SpotibookApp : Application(), androidx.work.Configuration.Provider {
    override val workManagerConfiguration: androidx.work.Configuration = androidx.work.Configuration.Builder().build()
    override fun attachBaseContext(base: Context) {
        val language = Locale.forLanguageTag("id-ID")
        val configuration = Configuration(base.resources.configuration).apply { setLocale(language) }
        super.attachBaseContext(base.createConfigurationContext(configuration))
    }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var repository: Repository
    lateinit var tracker: SessionTracker
    lateinit var focus: FocusTimer
    override fun onCreate() {
        super.onCreate()
        Locale.setDefault(Locale.forLanguageTag("id-ID"))
        repository = Repository(this, SpotibookDatabase.open(this), Preferences(this))
        tracker = SessionTracker(repository, scope)
        focus = FocusTimer(this, repository, scope, tracker)
        Notifications.channels(this)
        scope.launch { repository.expireHistory();HistoryCleanup.schedule(this@SpotibookApp);Reminders.schedule(this@SpotibookApp, repository.preferences.settings.first()); focus.recover(); Widgets.update(this@SpotibookApp) }
    }
}
