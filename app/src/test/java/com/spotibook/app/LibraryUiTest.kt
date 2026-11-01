package com.spotibook.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.spotibook.app.data.*
import com.spotibook.app.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.time.LocalDate
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=SpotibookApp::class,qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryUiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun selectAllAddsBooksToAShelfAndShowsACoverRackInsteadOfFolderChips() {
        val model=AppState(compose.activity.application as SpotibookApp)
        val ids=(1..3).map { UUID.randomUUID().toString() };val shelf=Shelf(UUID.randomUUID().toString(),"Teknologi")
        runBlocking {
            ids.forEachIndexed { i,id ->
                val dir=model.repo.documents.directory(id);dir.mkdirs()
                val cover=android.graphics.Bitmap.createBitmap(180,270,android.graphics.Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(cover);canvas.drawColor(listOf(0xff2f7299.toInt(),0xffba6247.toInt(),0xff526a48.toInt())[i])
                val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {color=android.graphics.Color.WHITE;textSize=25f}
                canvas.drawText("BUKU ${i+1}",20f,95f,paint)
                java.io.File(dir,"cover.png").outputStream().use{cover.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};cover.recycle()
                model.repo.dao.putBook(Book(id,"Buku ${i+1}",format="PDF",fileName="book.pdf",hash=(i+1).toString().repeat(64),cover="cover.png",total=100,position=20,offset=.1f,zoom=2f,progress=.2f))
            }
            model.repo.dao.putShelf(shelf)
        }
        compose.setContent { SpotibookTheme(Settings()) { Surface { LibraryScreen(model,{},{}) } } }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("3 buku tersimpan").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Pilih").performClick();compose.onNodeWithText("Pilih semua").performClick()
        compose.onNodeWithText("3 dipilih").assertExists()
        compose.onNodeWithText("Masukkan ke rak").performClick()
        compose.onNodeWithText("Teknologi").performClick()
        compose.onNodeWithText("Simpan").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Pilih").fetchSemanticsNodes().isNotEmpty() }
        runBlocking {
            assertEquals(3,model.repo.dao.allMemberships().count{it.shelfId==shelf.id})
            assertTrue(model.repo.dao.allBooks().all{it.position==20&&it.zoom==2f&&it.offset==.1f})
            model.repo.addToShelf(ids.toSet(),shelf.id);assertEquals(3,model.repo.dao.allMemberships().size)
        }
        compose.onNodeWithText("Rak buku").performClick()
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("shelf-${shelf.id}"))
        compose.onNodeWithTag("shelf-${shelf.id}").assertExists()
        compose.waitUntil(10_000){compose.onAllNodesWithContentDescription("Sampul Buku 1").fetchSemanticsNodes().isNotEmpty()}
        compose.runOnIdle { captureScreen(compose.activity,"rak-buku") }
        compose.onNodeWithTag("shelf-${shelf.id}").performClick()
        compose.onNodeWithText("Semua rak").assertExists()
        compose.onNodeWithTag("library-grid").performScrollToNode(hasTestTag("library-book-${ids.first()}"))
        compose.runOnIdle {captureScreen(compose.activity,"isi-rak")}
    }
    @Test fun removesFromOneShelfAndCanDeleteThePrivateCopyFromTheLibrary(){
        val model=AppState(compose.activity.application as SpotibookApp)
        val id=UUID.randomUUID().toString();val one=Shelf(UUID.randomUUID().toString(),"Rak Satu");val two=Shelf(UUID.randomUUID().toString(),"Rak Dua")
        val original=java.io.File(compose.activity.cacheDir,"asli.pdf").apply{writeText("original")}
        val copy=java.io.File(model.repo.documents.directory(id).apply{mkdirs()},"book.pdf").apply{writeText("private")}
        runBlocking{
            model.repo.dao.putBook(Book(id,"Buku dihapus",format="PDF",fileName="book.pdf",sourceUri=android.net.Uri.fromFile(original).toString(),hash="f".repeat(64)))
            model.repo.dao.putShelf(one);model.repo.dao.putShelf(two);model.repo.addToShelf(setOf(id),one.id);model.repo.addToShelf(setOf(id),two.id)
        }
        compose.setContent{SpotibookTheme(Settings()){Surface{LibraryScreen(model,{},{})}}}
        compose.waitUntil(10_000){compose.onAllNodesWithText("1 buku tersimpan").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Rak buku").performClick();compose.onNodeWithTag("shelf-${one.id}").performClick()
        fun selectAndDelete(option:String){
            compose.onNodeWithText("Pilih").performClick();compose.onNodeWithText("Pilih semua").performClick();compose.onNodeWithText("Hapus").performClick()
            compose.onNodeWithText(option).performClick();compose.onAllNodesWithText("Hapus").onLast().performClick()
            compose.waitUntil(15_000){compose.onAllNodesWithContentDescription("Batal memilih").fetchSemanticsNodes().isEmpty()}
        }
        selectAndDelete("Keluarkan dari rak Rak Satu")
        runBlocking{assertNotNull(model.repo.dao.book(id));assertEquals(listOf(BookShelf(id,two.id)),model.repo.dao.allMemberships())}
        assertTrue(copy.exists());compose.onNodeWithText("Semua rak").performClick();compose.onNodeWithText("Buku").performClick()
        selectAndDelete("Hapus dari pustaka aplikasi")
        runBlocking{assertNull(model.repo.dao.book(id));assertTrue(model.repo.dao.allMemberships().isEmpty())}
        assertFalse(copy.exists());assertEquals("original",original.readText())
    }
    @Test fun invalidBulkAssignmentRollsBackWithoutRemovingOtherShelfMemberships() {
        val model=AppState(compose.activity.application as SpotibookApp)
        runBlocking {
            val id=UUID.randomUUID().toString();val shelf=Shelf(UUID.randomUUID().toString(),"Lama")
            model.repo.dao.putBook(Book(id,"Buku",format="TXT",fileName="a.txt",hash="a".repeat(64)))
            model.repo.dao.putShelf(shelf);model.repo.addToShelf(setOf(id),shelf.id)
            assertTrue(runCatching{model.repo.addToShelf(setOf(id,"missing"),shelf.id)}.isFailure)
            assertEquals(listOf(BookShelf(id,shelf.id)),model.repo.dao.allMemberships())
        }
    }
    @Test fun streakWeeksSwipeAndQualifiedDatesUseFireIcons() {
        val today=LocalDate.of(2026,10,31);val prior=today.minusWeeks(1)
        compose.setContent { SpotibookTheme(Settings()) { StreakCalendar(mapOf(today to 300_000L,prior to 300_000L),today,5,{},{}) } }
        compose.onAllNodesWithContentDescription("Api membaca").onFirst().assertExists()
        compose.onNodeWithTag("streak-pager").performTouchInput{swipeRight()}
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Sabtu, 24 Oktober 2026: target tercapai").assertExists()
        compose.runOnIdle {captureScreen(compose.activity,"streak-mingguan")}
        compose.onNodeWithText("Hari ini").performClick();compose.waitForIdle()
        compose.onNodeWithContentDescription("Sabtu, 31 Oktober 2026: target tercapai").assertExists()
    }
}
