package com.spotibook.app.reader

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.semantics.SemanticsProperties
import com.spotibook.app.SpotibookApp
import com.spotibook.app.data.*
import com.spotibook.app.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=SpotibookApp::class,qualifiers="w740dp-h360dp-mdpi",shadows=[FixturePdfRenderer::class,FixturePdfPage::class])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReaderRotationUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun landscapeZoomRestorationAndFocusStayConnectedToTheReader() {
        val model=AppState(compose.activity.application as SpotibookApp)
        val id=UUID.randomUUID().toString()
        runBlocking {
            model.repo.documents.directory(id).mkdirs();File(model.repo.documents.directory(id),"book.pdf").writeText("%PDF-1.4 fixture")
            model.repo.dao.putBook(Book(id,"Buku rotasi",format="PDF",fileName="book.pdf",hash="f".repeat(64),total=3))
            model.repo.preferences.update{it.copy(readerOrientation=ReaderOrientation.Auto,readerTheme="Light",focusNotification=false,focusSound="Silent")}
        }
        org.robolectric.Shadows.shadowOf(model.app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val restore=StateRestorationTester(compose)
        restore.setContent { SpotibookTheme(Settings()) { ReaderScreen(model,id,{}) } }
        compose.waitUntil(15_000){compose.onAllNodesWithContentDescription("Perbesar PDF").fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithContentDescription("Halaman 1").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Perbesar PDF").performClick();compose.waitForIdle()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pembesaran 150 persen")).assertExists()
        restore.emulateSavedInstanceStateRestore()
        compose.waitUntil(15_000){compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pembesaran 150 persen")).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Perkecil PDF").performClick();compose.waitForIdle()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pembesaran 100 persen")).assertExists()
        compose.onNodeWithContentDescription("Halaman berikutnya").performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithContentDescription("Halaman 3").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Fokus membaca").performClick()
        compose.onNodeWithText("Mulai fokus").performScrollTo()
        compose.onNodeWithText("10").performClick()
        compose.onNodeWithText("Mulai fokus").performScrollTo().performClick()
        compose.waitUntil(15_000){compose.onAllNodesWithText("Jeda").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Tutup Fokus").performClick()
        compose.waitUntil(5_000){compose.onAllNodesWithContentDescription("Tutup Fokus").fetchSemanticsNodes().isEmpty()}
        runBlocking {
            val focus=model.repo.preferences.focus.first()
            assertTrue(focus.running);assertEquals(id,focus.bookId);assertEquals(600_000L,focus.plannedMillis)
            model.app.focus.stop()
        }
    }
}
