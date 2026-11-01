package com.spotibook.app.reader

import com.spotibook.app.data.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PdfZoomTest {
    @Test fun zoomAnchorsTheDocumentPointUnderTheFingers() {
        val delta = zoomScrollDelta(1f, 2f, 300f, -40f, 800f, null)
        assertEquals(170f, delta, .001f)
        // The original point y=300 remains at the moved centroid y=260.
        assertEquals(260f, (300f-delta)*2, .001f)
        assertEquals(-100f, zoomPan(1f,2f,280f,0f,0f,360f,344f),.001f)
    }
    @Test fun shortCenteredPagesAndPanEdgesAreBounded() {
        assertEquals(0f, zoomScrollDelta(1f,2f,400f,0f,800f,400f),.001f)
        assertEquals(344f,zoomPan(2f,2f,180f,999f,0f,360f,344f),.001f)
        assertEquals(0f,zoomPan(2f,1f,180f,0f,164f,360f,344f),.001f)
        val forward=zoomScrollDelta(1f,3f,310f,0f,800f,700f)
        val back=zoomScrollDelta(3f,1f,310f,0f,800f,700f)
        assertEquals(0f,forward+back,.001f)
    }
    @Test fun sepiaRetainsEveryColoredPixelAndTheOriginalPaperTone() {
        assertEquals(0xfff1e5cb.toInt(),PdfSepia.color(0xffffffff.toInt()))
        assertEquals(0xff302c25.toInt(),PdfSepia.color(0xff000000.toInt()))
        for(r in 0..255 step 17) for(g in 0..255 step 17) for(b in 0..255 step 17) {
            val pixel=0x7f000000 or (r shl 16) or (g shl 8) or b
            if(r!=g||g!=b) assertEquals(pixel,PdfSepia.color(pixel))
            assertEquals(0x7f,PdfSepia.color(pixel) ushr 24)
        }
    }
    @Test fun oldBlackAndForestPreferencesMigrateWithoutDroppingOtherValues() {
        val old=Json.decodeFromString<Settings>("""{"readerTheme":"Black","accent":"Forest","dailyMinutes":47,"brightness":0.4,"pdfMargin":13}""").normalized()
        assertEquals("Dark",old.readerTheme);assertEquals("Ocean",old.accent)
        assertEquals(47,old.dailyMinutes);assertEquals(.4f,old.brightness);assertEquals(13,old.pdfMargin)
        assertEquals(old,old.normalized())
    }
}
