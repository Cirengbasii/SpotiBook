package com.spotibook.app.reader

import android.app.Application
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.spotibook.app.data.Settings
import com.spotibook.app.data.Book
import com.spotibook.app.ui.SpotibookTheme
import com.spotibook.app.captureScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun allSixModesCanBeSelectedOnNarrowScreen() {
        var selected by mutableStateOf(PageTurn.Scrolling)
        compose.setContent { SpotibookTheme(Settings()) { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(20.dp)) { Text("Perpindahan halaman",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(16.dp));PageModeChoices(selected){selected=it} } } } }
        for(mode in PageTurn.entries)compose.onNodeWithTag("reader-mode-${mode.name}").performClick().assertIsSelected()
        save("pilihan-halaman")
    }
    @Test @Config(qualifiers="w740dp-h360dp-mdpi") fun modeChoicesFitLandscape() {
        compose.setContent { SpotibookTheme(Settings(appearance="Dark")) { Surface(Modifier.fillMaxSize()) { Column(Modifier.padding(20.dp)) { PageModeChoices(PageTurn.Withdrawal){} } } } }
        PageTurn.entries.forEach { compose.onNodeWithTag("reader-mode-${it.name}").assertIsDisplayed() }
        save("pilihan-halaman-lanskap")
    }
    @Test fun readerWindowRestoresOrientationBrightnessAndKeepAwakeOnExit() {
        val activity=compose.activity
        val original=activity.requestedOrientation
        val originalBrightness=activity.window.attributes.screenBrightness
        val originalKeepAwake=activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        var reading by mutableStateOf(true)
        var settings by mutableStateOf(Settings(readerOrientation=ReaderOrientation.Landscape,brightness=.4f))
        compose.setContent { if(reading)ReaderWindow(activity,settings,true,null,true) }
        compose.runOnIdle {
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,activity.requestedOrientation)
            assertEquals(.4f,activity.window.attributes.screenBrightness,0f)
            assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON!=0)
            settings=settings.copy(readerOrientation=ReaderOrientation.Portrait)
        }
        compose.runOnIdle { assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,activity.requestedOrientation);reading=false }
        compose.runOnIdle {
            assertEquals(original,activity.requestedOrientation)
            assertEquals(originalBrightness,activity.window.attributes.screenBrightness,0f)
            assertEquals(originalKeepAwake,activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    @Test fun panelChangesSavedOrientationModeThemeAndBrightness() {
        var settings by mutableStateOf(Settings())
        val book=Book("panel-fixture","Buku pengujian",format="PDF",fileName="book.pdf",hash="a".repeat(64),total=233)
        compose.setContent { SpotibookTheme(settings) {
            ReaderPanel(ReaderPanelPage.Settings,book,30,settings,null,emptyList(),{change->settings=change(settings)},{},{_,_,_->},{},{},{},{},{})
        } }
        compose.onNodeWithText("Orientasi").performClick()
        compose.onNodeWithText("Lanskap").performClick()
        compose.runOnIdle { assertEquals(ReaderOrientation.Landscape,settings.readerOrientation) }
        compose.onNodeWithContentDescription("Kembali ke pengaturan").performClick()
        compose.onNodeWithText("Ganti halaman").performClick()
        compose.onNodeWithTag("reader-mode-Shift").performClick()
        compose.runOnIdle { assertEquals(PageTurn.Shift,settings.pageTurn) }
        compose.onNodeWithContentDescription("Kembali ke pengaturan").performClick()
        compose.onNodeWithText("Hitam").assertDoesNotExist()
        compose.onNodeWithText("Gelap").performClick()
        compose.runOnIdle { assertEquals("Dark",settings.readerTheme) }
        compose.onNodeWithText("Kecerahan").performClick()
        compose.onAllNodes(isToggleable()).onFirst().performClick()
        compose.onNodeWithContentDescription("Kecerahan layar baca").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.35f) }
        compose.runOnIdle { assertEquals(.35f,settings.brightness,.01f) }
    }
    private fun save(name:String) {
        compose.runOnIdle { captureScreen(compose.activity,name) }
    }
}
