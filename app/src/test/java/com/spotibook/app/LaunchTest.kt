package com.spotibook.app

import android.content.pm.PackageManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class LaunchTest {
    @Test fun volumeKeysOnlyTurnPagesWhileReaderHandlerIsRegistered() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get();val turns=mutableListOf<Boolean>()
            activity.readerPageKey={turns+=it}
            val down=android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_VOLUME_DOWN)
            assertTrue(activity.onKeyDown(down.keyCode,down));assertEquals(listOf(true),turns)
            val up=android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_VOLUME_UP)
            assertTrue(activity.onKeyDown(up.keyCode,up));assertEquals(listOf(true,false),turns)
            activity.readerPageKey=null;activity.onKeyDown(down.keyCode,down);assertEquals(2,turns.size)
        } finally {controller.pause().stop().destroy()}
    }
    @Test fun launcherActivityStartsWithSpotibookIdentityAndNoInternetPermission() {
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            val activity=controller.get()
            assertEquals("Spotibook",activity.getString(R.string.app_name))
            assertEquals(PackageManager.PERMISSION_DENIED,activity.packageManager.checkPermission(android.Manifest.permission.INTERNET,activity.packageName))
            assertFalse(activity.isFinishing)
        } finally { controller.pause().stop().destroy() }
    }
}
