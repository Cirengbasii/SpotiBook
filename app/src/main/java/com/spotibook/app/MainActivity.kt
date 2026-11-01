package com.spotibook.app

import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.MotionEvent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.ui.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base:Context) {
        val config=Configuration(base.resources.configuration).apply { setLocale(java.util.Locale.forLanguageTag("id-ID")) }
        super.attachBaseContext(base.createConfigurationContext(config))
    }
    private val model: AppState by viewModels { AppState.factory(application as SpotibookApp) }
    private val destination = MutableStateFlow<Pair<String, Long>?>(null)
    private var resumed = false
    var readerPageKey: ((Boolean) -> Unit)? = null
    private val app get() = application as SpotibookApp
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        readDestination(intent)
        setContent { val prefs by model.settings.collectAsStateWithLifecycle(); val route by destination.collectAsStateWithLifecycle()
            SpotibookTheme(prefs) { Spotibook(model, route) }
        }
    }
    private fun readDestination(intent: Intent) { intent.getStringExtra("destination")?.let { destination.value = it to System.nanoTime() } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); readDestination(intent) }
    override fun onResume() { super.onResume(); resumed = true; app.scope.launch { app.tracker.visibility(hasWindowFocus()); app.focus.recover() } }
    override fun onPause() { resumed = false; app.scope.launch { app.tracker.visibility(false); com.spotibook.app.background.Widgets.update(app) }; super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus); app.scope.launch { app.tracker.visibility(resumed && hasFocus) } }
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean { app.tracker.touch(); return super.dispatchTouchEvent(ev) }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handler = readerPageKey
        if (handler != null && keyCode in listOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP)) {
            app.tracker.touch()
            if (event.repeatCount == 0) handler(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        if (readerPageKey != null && keyCode in listOf(KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_UP)) true else super.onKeyUp(keyCode,event)
}
