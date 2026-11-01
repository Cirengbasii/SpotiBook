package com.spotibook.app

import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import com.spotibook.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.util.UUID
import java.io.File
import android.net.Uri
import com.spotibook.app.background.FocusAudio

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=SpotibookApp::class)
class FocusIntegrationTest {
    @Test fun chosenSoundIsCopiedAndInvalidReplacementKeepsThePreviousSound()=runBlocking{
        val app=ApplicationProvider.getApplicationContext<SpotibookApp>()
        val source=File(app.cacheDir,"pilihan.mp3").apply{writeBytes(byteArrayOf(73,68,51,4,0,0,0,0,0,0))}
        val bytes=source.readBytes()
        FocusAudio.import(app,Uri.fromFile(source))
        assertArrayEquals(bytes,FocusAudio.file(app).readBytes());assertArrayEquals(bytes,source.readBytes())
        val empty=File(app.cacheDir,"kosong.mp3").apply{writeBytes(byteArrayOf())}
        assertTrue(runCatching{FocusAudio.import(app,Uri.fromFile(empty))}.isFailure)
        assertArrayEquals(bytes,FocusAudio.file(app).readBytes())
        assertFalse(File(app.filesDir,"focus-sound.tmp").exists())
    }
    @Test fun readingWithoutFocusAndReadingDuringFocusAreRecordedSeparately()=runBlocking{
        val app=ApplicationProvider.getApplicationContext<SpotibookApp>()
        app.repository.preferences.update{it.copy(focusNotification=false,focusSound="Silent")}
        val book=Book(UUID.randomUUID().toString(),"Buku fokus",format="TXT",fileName="book.txt",hash="d".repeat(64))
        app.repository.dao.putBook(book)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1));app.tracker.open(book);app.tracker.visibility(true)
        suspend fun read(seconds:Int){repeat(seconds/5){ShadowSystemClock.advanceBy(Duration.ofSeconds(5));app.tracker.visibility(true)}}
        read(10)
        app.focus.start(1,book.id);val id=app.repository.preferences.focus.first().id
        read(15);app.focus.pause();read(20);app.focus.resume();read(45)
        app.focus.recover(id)
        val all=app.repository.dao.allSessions()
        assertEquals(1,all.map{it.id.substringBefore('/')}.distinct().size)
        assertEquals(60000L,all.filter{it.focusId==id}.sumOf{it.millis})
        assertEquals(30000L,all.filter{it.focusId.isEmpty()}.sumOf{it.millis})
        assertTrue(app.repository.preferences.focus.first().done)
        assertEquals(1,app.repository.dao.allFocus().size)
        app.tracker.close(book.id);app.focus.stop()
    }
    @Test fun completedTimerNotifiesOnceAndRejectsStaleAlarmIds()=runBlocking{
        val app=ApplicationProvider.getApplicationContext<SpotibookApp>()
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        app.repository.preferences.update{it.copy(focusNotification=true,focusSound="Silent")}
        app.focus.start(1,null);val id=app.repository.preferences.focus.first().id
        app.focus.recover("old-timer");assertFalse(app.repository.preferences.focus.first().done)
        ShadowSystemClock.advanceBy(Duration.ofMinutes(1));app.focus.recover(id);app.focus.recover(id)
        assertEquals(1,app.repository.dao.allFocus().size)
        val manager=app.getSystemService(NotificationManager::class.java)
        val notice=manager.activeNotifications.single{it.id==101}
        assertEquals("focus_complete",notice.notification.channelId)
        assertNull(manager.getNotificationChannel("focus_complete").sound)
        app.focus.stop()
    }
}
