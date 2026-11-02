package com.spotibook.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.flow.first
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.Role

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],qualifiers="w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeUiTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun coralHomeSupportsLightAndDarkWithRealBookState(){
        val app=compose.activity.application as SpotibookApp
        val id=java.util.UUID.randomUUID().toString()
        val dir=app.repository.documents.directory(id);dir.mkdirs()
        val cover=android.graphics.Bitmap.createBitmap(240,360,android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(cover).apply{
            drawColor(0xffc4dfe1.toInt())
            val p=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            p.color=0xfffbeed8.toInt();drawCircle(174f,150f,52f,p)
            p.color=0xff809e92.toInt();drawPath(android.graphics.Path().apply{moveTo(0f,230f);quadTo(70f,150f,150f,220f);quadTo(200f,180f,240f,200f);lineTo(240f,360f);lineTo(0f,360f);close()},p)
            p.color=0xff305a52.toInt();drawPath(android.graphics.Path().apply{moveTo(0f,280f);quadTo(100f,200f,240f,270f);lineTo(240f,360f);lineTo(0f,360f);close()},p)
            p.color=0xff1b433e.toInt();p.textSize=30f;p.typeface=android.graphics.Typeface.create("serif",android.graphics.Typeface.BOLD)
            drawText("Ruang untuk",25f,65f,p);drawText("Tumbuh",55f,100f,p)
            p.color=0xfffbeed8.toInt();p.textSize=13f;drawText("NADIA PUTRI",73f,335f,p)
        }
        java.io.File(dir,"cover.png").outputStream().use{cover.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};cover.recycle()
        kotlinx.coroutines.runBlocking{app.repository.dao.putBook(com.spotibook.app.data.Book(id,"Ruang untuk Tumbuh",author="Nadia Putri",format="PDF",fileName="book.pdf",cover="cover.png",hash="a".repeat(64),total=200,position=83,progress=.42f,status="Reading"));app.repository.preferences.update{it.copy(appearance="Light")}}
        compose.waitUntil(10000){compose.onAllNodesWithText("Lanjutkan").fetchSemanticsNodes().isNotEmpty()}
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("Sampul Ruang untuk Tumbuh").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Lanjutkan").assertIsDisplayed();save("beranda-coral-terang")
        kotlinx.coroutines.runBlocking{app.repository.preferences.update{it.copy(appearance="Dark")}}
        compose.waitForIdle();compose.onNodeWithText("Lanjutkan").assertIsDisplayed();save("beranda-coral-gelap")
    }
    @Test fun focusCompletionAppearsWithoutOpeningFocusAndCanBeDismissed() {
        val app=compose.activity.application as SpotibookApp
        kotlinx.coroutines.runBlocking {
            app.repository.preferences.update{it.copy(focusNotification=false,focusSound="Silent")}
            app.focus.start(1,null)
        }
        compose.waitForIdle()
        org.robolectric.shadows.ShadowSystemClock.advanceBy(java.time.Duration.ofMinutes(1))
        compose.mainClock.advanceTimeBy(60_100)
        compose.waitUntil(10000){compose.onAllNodesWithText("Fokus selesai").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Fokus selesai").assertIsDisplayed()
        save("fokus-selesai")
        compose.onNodeWithContentDescription("Tutup penanda Fokus").performClick()
        compose.waitForIdle();compose.onNodeWithText("Fokus selesai").assertDoesNotExist()
        kotlinx.coroutines.runBlocking{app.focus.stop()}
    }
    @Test fun homeAndLibraryAreIndonesianAndCanBeNavigated() {
        compose.onNodeWithText("Tambahkan buku pertama").assertIsDisplayed()
        save("beranda")
        compose.onNodeWithText("Pustaka").performClick()
        compose.onNodeWithText("Cari judul atau penulis").assertIsDisplayed()
        save("pustaka")
        compose.onNodeWithText("Profil").performClick()
        compose.onNodeWithText("Bahasa aplikasi · Bahasa Indonesia").assertIsDisplayed()
        compose.onNode(hasText("Gelap") and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Checkbox)).performClick()
        compose.waitUntil(5000){kotlinx.coroutines.runBlocking{(compose.activity.application as SpotibookApp).repository.preferences.settings.first().appearance}=="Dark"}
        save("profil-coral-gelap")
    }
    @Test @Config(qualifiers="w900dp-h600dp-mdpi") fun largeScreenUsesNavigationRail() {
        compose.onNodeWithText("Pustaka").performClick()
        compose.onNodeWithText("Cari judul atau penulis").assertIsDisplayed()
        save("pustaka-tablet")
    }
    @Test fun homeCalendarShowsRealCoversAndAllBooksReadOnTheSelectedDay() {
        val repo=(compose.activity.application as SpotibookApp).repository
        val day=java.time.LocalDate.now()
        val ids=listOf(java.util.UUID.randomUUID().toString(),java.util.UUID.randomUUID().toString())
        kotlinx.coroutines.runBlocking {
            ids.forEachIndexed { index,id ->
                val dir=repo.documents.directory(id);dir.mkdirs()
                val cover=android.graphics.Bitmap.createBitmap(180,270,android.graphics.Bitmap.Config.ARGB_8888)
                val canvas=android.graphics.Canvas(cover);canvas.drawColor(if(index==0)0xff2f7299.toInt()else 0xffba6247.toInt())
                val paint=android.graphics.Paint().apply { color=android.graphics.Color.WHITE;textSize=22f;isAntiAlias=true }
                canvas.drawText(if(index==0)"BASIS DATA"else "ALGORITMA",12f,80f,paint)
                java.io.File(dir,"cover.png").outputStream().use { cover.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };cover.recycle()
                repo.dao.putBook(com.spotibook.app.data.Book(id,if(index==0)"Basis data"else "Algoritma",author="Penulis pengujian",format="PDF",fileName="test.pdf",hash=(if(index==0)"a"else"b").repeat(64),cover="cover.png",total=233,position=30,progress=.13f,status="Reading",opened=System.currentTimeMillis()-index*1000L))
                repo.dao.putSession(com.spotibook.app.data.Session("run$index/$day",id,if(index==0)"Basis data"else"Algoritma",day.toString(),System.currentTimeMillis()-600000,System.currentTimeMillis(),300000L+index*1000,0,30,pages=30,zone="Asia/Jakarta"))
            }
        }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Basis data").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription("Sampul Basis data").fetchSemanticsNodes().isNotEmpty() }
        save("beranda-berisi")
        compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("calendar-day-$day"))
        compose.onNodeWithTag("calendar-day-$day").performClick()
        compose.onNodeWithText("+1").assertExists()
        save("kalender-sampul")
        for(id in ids) {
            compose.onNodeWithTag("home-list").performScrollToNode(hasTestTag("calendar-book-$id"))
            compose.onNodeWithTag("calendar-book-$id").assertExists()
        }
        compose.onAllNodesWithContentDescription("Sampul Algoritma").onFirst().assertExists()
        compose.onNodeWithTag("home-list").performScrollToNode(hasContentDescription("Hapus riwayat Basis data"))
        compose.onNodeWithContentDescription("Hapus riwayat Basis data").performClick();compose.onNodeWithText("Hapus").performClick()
        compose.waitUntil(10_000){compose.onAllNodesWithContentDescription("Hapus riwayat Basis data").fetchSemanticsNodes().isEmpty()}
        kotlinx.coroutines.runBlocking{org.junit.Assert.assertEquals(2,repo.dao.allSessions().size);org.junit.Assert.assertTrue(repo.dao.allSessions().first{it.bookTitle=="Basis data"}.historyHidden)}

    }
    @Test fun focusCanNavigateToEveryTabIncludingHomeWhileTimerKeepsRunning(){
        val app=compose.activity.application as SpotibookApp
        compose.onNodeWithText("Fokus").performClick()
        compose.onNodeWithText("Fokus membaca").assertIsDisplayed()
        kotlinx.coroutines.runBlocking{app.repository.preferences.update{it.copy(focusNotification=false,focusSound="Silent")};app.focus.start(10,null)}
        for(target in listOf("Beranda","Pustaka","Statistik","Profil","Fokus","Beranda")){
            val tab=hasText(target) and SemanticsMatcher.expectValue(SemanticsProperties.Role,Role.Tab)
            compose.onNode(tab).performClick();compose.waitForIdle();compose.onNode(tab).assertIsSelected()
            if(target=="Beranda")compose.onNodeWithText("Tambahkan buku pertama").assertIsDisplayed()
        }
        kotlinx.coroutines.runBlocking{org.junit.Assert.assertTrue(app.repository.preferences.focus.first().running);app.focus.stop()}
    }
    private fun save(name:String) { compose.runOnIdle { captureScreen(compose.activity,name) } }
}
