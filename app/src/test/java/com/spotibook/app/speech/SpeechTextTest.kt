package com.spotibook.app.speech

import org.junit.Assert.*
import org.junit.Test

class SpeechTextTest{
    @Test fun longTextKeepsAllWordsAndFitsEngineLimits(){
        val text=(1..1800).joinToString(" "){"kalimat$it."}
        val chunks=speechChunks(text)
        assertTrue(chunks.size>1);assertTrue(chunks.all{it.length in 1..1800})
        assertEquals(text,chunks.joinToString(" "))
    }
    @Test fun blankPageHasNoUtterances(){assertTrue(speechChunks(" \n \t ").isEmpty())}
}
