package com.spotibook.app.reader

import com.spotibook.app.data.Settings
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ReaderPreferencesTest {
    @Test fun oldPreferencesLoadWithoutChangingReadingData() {
        val restored=Json.decodeFromString<Settings>("""{"fontSize":22,"brightness":0.4,"dailyMinutes":45}""")
        assertEquals(22,restored.fontSize);assertEquals(45,restored.dailyMinutes)
        assertEquals(PageTurn.Scrolling,restored.pageTurn);assertEquals(ReaderOrientation.Auto,restored.readerOrientation)
    }
    @Test fun readerPreferencesRoundTripEveryModeAndOrientation() {
        for(mode in PageTurn.entries) for(orientation in ReaderOrientation.entries) {
            val value=Settings(pageTurn=mode,readerOrientation=orientation,pdfFit=PdfFit.Page,readerTheme="Dark",pdfMargin=12,volumeKeys=true,tapToTurn=true,keepScreenOn=false)
            assertEquals(value,Json.decodeFromString<Settings>(Json.encodeToString(value)))
        }
    }
    @Test fun themesMapPaperAndInkWithoutChangingTransparency() {
        assertNull(pdfColors("Light"))
        assertNull(pdfColors("Sepia"))
        for(theme in listOf("Dark","Black")) {
            val matrix=pdfColors(theme)!!
            fun channel(row:Int,input:Float)=matrix[row*5]*input+matrix[row*5+1]*input+matrix[row*5+2]*input+matrix[row*5+4]
            for(row in 0..2) { assertTrue(channel(row,255f) in 0f..255f);assertTrue(channel(row,0f) in 0f..255f) }
            assertTrue(channel(0,255f)<channel(0,0f))
            assertEquals(1f,matrix[18],0f)
        }
    }
}
