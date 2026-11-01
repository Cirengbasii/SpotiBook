package com.spotibook.app

import com.spotibook.app.data.*
import com.spotibook.app.ui.calendarBooks
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CalendarTest {
    @Test fun joinsRealCoversAndRetainsMultipleBooksAndDeletedBookHistory() {
        val day=LocalDate.of(2026,10,31)
        val book=Book("a","Basis data",format="PDF",fileName="a.pdf",hash="a".repeat(64),cover="cover.png")
        fun session(id:String,bookId:String,date:String,duration:Long)=Session(id,bookId,"Judul $bookId",date,1,1000,duration,0,1,zone="Asia/Jakarta")
        val entries=calendarBooks(listOf(session("run1/31","a",day.toString(),10000),session("run2/31","a",day.toString(),20000),session("run3/31","removed",day.toString(),5000),session("zero","empty",day.toString(),0),session("run4/30","a",day.minusDays(1).toString(),3000)),listOf(book))
        assertEquals(2,entries[day]!!.size)
        val first=entries[day]!!.first();assertEquals(book,first.book);assertEquals("cover.png",first.book!!.cover)
        assertEquals(30000,first.millis);assertEquals(2,first.sessions)
        assertNull(entries[day]!![1].book);assertEquals("Judul removed",entries[day]!![1].title)
        assertEquals(1,entries[day.minusDays(1)]!!.size)
        assertNull(entries[day.plusDays(1)])
    }
    @Test fun recordedLocalDateIsUsedAcrossTimezoneChangesAndSplitMidnightSessions() {
        val records=listOf(Session("run/2026-10-31","a","Buku A","2026-10-31",0,1,3000,1,2,zone="Asia/Jakarta"),Session("run/2026-11-01","a","Buku A","2026-11-01",1,2,4000,2,3,zone="Asia/Jakarta"))
        val entries=calendarBooks(records,emptyList())
        assertEquals(3000,entries[LocalDate.of(2026,10,31)]!!.single().millis)
        assertEquals(4000,entries[LocalDate.of(2026,11,1)]!!.single().millis)
    }
}
