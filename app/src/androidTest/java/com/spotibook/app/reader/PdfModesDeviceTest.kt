package com.spotibook.app.reader

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.spotibook.app.data.Book
import com.spotibook.app.data.Settings
import com.spotibook.app.ui.SpotibookTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfModesDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun everyModeRendersSwipesJumpsAndKeepsCurrentPageWhenChanged() {
        val file=File(compose.activity.cacheDir,"pages.pdf");fixture(file)
        val document=PdfDocument(file)
        var page by mutableIntStateOf(1)
        var offset by mutableFloatStateOf(0f)
        val zoomState=mutableFloatStateOf(1f);var zoom by zoomState
        var settings by mutableStateOf(Settings(pageTurn=PageTurn.Withdrawal,pdfFit=PdfFit.Page,readerTheme="Light"))
        var jump by mutableStateOf<Pair<Int,Float>?>(null)
        var command by mutableLongStateOf(0L)
        val errors=mutableListOf<String>()
        val book=Book("test-pdf","Buku pengujian",format="PDF",fileName="pages.pdf",hash="a".repeat(64),total=3)
        try {
            compose.setContent { SpotibookTheme(settings) { PdfReader(document,book.copy(position=page,offset=offset),jump,command,zoomState,settings,
                {p,f->page=p;offset=f},{},{delta->jump=(page+delta).coerceIn(0,2) to 0f;command++},{errors+=it}) } }
            fun awaitPage(number:Int) {
                compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Halaman $number").fetchSemanticsNodes().isNotEmpty() }
            }
            awaitPage(2)
            for(mode in PageTurn.available) {
                compose.runOnIdle { settings=settings.copy(pageTurn=mode) }
                compose.waitForIdle();assertEquals("Changing mode must preserve page",1,page)
                awaitPage(2)
                if(mode==PageTurn.Scrolling) compose.onRoot().performTouchInput { swipeUp() }
                else compose.onRoot().performTouchInput { swipeLeft() }
                if(mode==PageTurn.Scrolling)compose.waitUntil(15_000){page>1||offset>.05f}
                else compose.waitUntil(15_000) { page==2 }
                awaitPage(3)
                compose.runOnIdle { jump=1 to 0f;command++ }
                compose.waitUntil(15_000) { page==1 }
                awaitPage(2)
            }
            compose.runOnIdle { zoom=2f;settings=settings.copy(pageTurn=PageTurn.Scrolling,pdfFit=PdfFit.Width);jump=1 to .15f;command++ }
            compose.waitForIdle()
            compose.waitUntil(10_000){page==1&&kotlin.math.abs(offset-.15f)<.01f}
            for(mode in listOf(PageTurn.Shift,PageTurn.NoAnimation,PageTurn.Scrolling)) {
                compose.runOnIdle { settings=settings.copy(pageTurn=mode) }
                compose.waitForIdle()
            compose.waitUntil(10_000){page==1&&kotlin.math.abs(offset-.15f)<.01f}
            }
            compose.runOnIdle { zoom=1f;settings=settings.copy(pageTurn=PageTurn.Withdrawal) }
            compose.waitForIdle();awaitPage(2)
            assertTrue(errors.joinToString(),errors.isEmpty())
        } finally { compose.setContentPlaceholder();runBlocking{document.shutdown()};file.delete() }
    }
    @Test fun pinchPanAndZoomOutWorkInAllSixModesWithoutTurningThePage() {
        val file=File(compose.activity.cacheDir,"pinch.pdf");fixture(file)
        val document=PdfDocument(file)
        var page by mutableIntStateOf(1);var offset by mutableFloatStateOf(0f)
        val zoomState=mutableFloatStateOf(1f);var zoom by zoomState
        var settings by mutableStateOf(Settings(pageTurn=PageTurn.Withdrawal,pdfFit=PdfFit.Width,readerTheme="Sepia"))
        var jump by mutableStateOf<Pair<Int,Float>?>(null);var command by mutableLongStateOf(0L)
        val book=Book("pinch-pdf","Buku cubit",format="PDF",fileName="pinch.pdf",hash="b".repeat(64),total=3)
        val errors=mutableListOf<String>()
        try {
            val original=file.readBytes()
            val light=runBlocking { document.render(0,360,"Light") }; val sepia=runBlocking { document.render(0,360,"Sepia") }
            assertEquals(light.getPixel(40,60),sepia.getPixel(40,60))
            assertEquals(0xfff1e5cb.toInt(),sepia.getPixel(0,0)); assertArrayEquals(original,file.readBytes())
            light.recycle();sepia.recycle()
            compose.setContent { SpotibookTheme(settings) { PdfReader(document,book.copy(position=page,offset=offset),jump,command,zoomState,settings,{p,f->page=p;offset=f},{},{},{errors+=it}) } }
            for(mode in PageTurn.available) {
                compose.runOnIdle { zoom=1f;settings=settings.copy(pageTurn=mode);jump=1 to 0f;command++ }
                compose.waitForIdle()
                compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Halaman 2").fetchSemanticsNodes().isNotEmpty() }
                compose.onNodeWithTag("pdf-viewport").performTouchInput {
                    val c=center
                    down(0,c+androidx.compose.ui.geometry.Offset(-35f,0f));down(1,c+androidx.compose.ui.geometry.Offset(35f,0f))
                    for(i in 1..12) { updatePointerTo(0,c+androidx.compose.ui.geometry.Offset(-35f-i*4,0f));updatePointerTo(1,c+androidx.compose.ui.geometry.Offset(35f+i*4,0f));move(16) }
                    up(0);up(1)
                }
                compose.waitForIdle()
                compose.runOnIdle { assertTrue("$mode must respond to pinch: $zoom",zoom>1.8f);assertEquals("$mode pinch must not turn pages",1,page) }
                val before=offset
                compose.onNodeWithTag("pdf-viewport").performTouchInput { swipe(center,center+androidx.compose.ui.geometry.Offset(30f,-60f),400) }
                compose.waitForIdle()
                compose.runOnIdle { assertEquals("$mode pan must stay on this page",1,page);assertTrue("$mode vertical pan must move the document ($before -> $offset)",offset>before+.01f) }
                compose.onNodeWithTag("pdf-viewport").performTouchInput { doubleClick(center) }
                compose.waitForIdle()
                compose.runOnIdle { assertEquals("$mode double tap resets zoom",1f,zoom,.01f) }
            }
            assertTrue(errors.joinToString(),errors.isEmpty())
        } finally { compose.setContentPlaceholder();runBlocking{document.shutdown()};file.delete() }
    }
    private fun fixture(file:File) {
        val objects=mutableListOf("<< /Type /Catalog /Pages 2 0 R >>","<< /Type /Pages /Count 3 /Kids [3 0 R 5 0 R 7 0 R] >>")
        for(page in 1..3) {
            objects+="<< /Type /Page /Parent 2 0 R /MediaBox [0 0 360 540] /Resources << /Font << /F1 9 0 R >> >> /Contents ${page*2+2} 0 R >>"
            val content="1 1 1 rg 0 0 360 540 re f 0.14 0.3 0.24 rg 26 390 308 90 re f BT /F1 24 Tf 1 1 1 rg 44 430 Td (HALAMAN $page) Tj ET BT /F1 15 Tf 0.1 0.1 0.1 rg 30 350 Td (Spotibook - PDF pengujian) Tj ET"
            objects+="<< /Length ${content.length} >>\nstream\n$content\nendstream"
        }
        objects+="<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"
        val out=StringBuilder("%PDF-1.4\n");val offsets=mutableListOf(0)
        objects.forEachIndexed{index,obj->offsets+=out.length;out.append("${index+1} 0 obj\n$obj\nendobj\n")}
        val start=out.length;out.append("xref\n0 ${objects.size+1}\n0000000000 65535 f \n")
        offsets.drop(1).forEach{out.append("%010d 00000 n \n".format(java.util.Locale.ROOT,it))}
        out.append("trailer\n<< /Size ${objects.size+1} /Root 1 0 R >>\nstartxref\n$start\n%%EOF\n")
        file.writeBytes(out.toString().toByteArray(Charsets.US_ASCII))
    }
}

private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*,*>.setContentPlaceholder() {
    // Dispose the composition before closing the native PDF renderer.
    runOnUiThread { activity.setContentView(android.widget.FrameLayout(activity)) }
}
