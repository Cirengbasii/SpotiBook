package com.spotibook.app.reader

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.view.*
import com.spotibook.app.data.Settings

@Composable fun ReaderWindow(activity: Activity, settings: Settings, controls: Boolean, brightness: Float?, ready: Boolean) {
    val originalOrientation = rememberSaveable { activity.requestedOrientation }
    DisposableEffect(activity) { onDispose { if (!activity.isChangingConfigurations) activity.requestedOrientation = originalOrientation } }
    LaunchedEffect(settings.readerOrientation, ready) {
        if (ready && activity.requestedOrientation != settings.readerOrientation.androidValue) activity.requestedOrientation = settings.readerOrientation.androidValue
    }
    DisposableEffect(activity, settings.keepScreenOn) {
        val wasOn = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        if (settings.keepScreenOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { if (wasOn) activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    DisposableEffect(activity, settings.brightness, brightness) {
        val previous = activity.window.attributes.screenBrightness
        activity.window.attributes = activity.window.attributes.apply { screenBrightness = brightness ?: settings.brightness }
        onDispose { activity.window.attributes = activity.window.attributes.apply { screenBrightness = previous } }
    }
    DisposableEffect(activity, controls) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (controls) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
