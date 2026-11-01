package com.spotibook.app.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ReadingTest {
    private val today = LocalDate.of(2026,10,31)
    @Test fun emptyLibraryHasNoStreak() { assertEquals(Streak(0,0,0), Reading.streak(emptyMap(),today,5)) }
    @Test fun openingAppDoesNotQualify() { assertEquals(0, Reading.streak(mapOf(today to 0L),today,5).current) }
    @Test fun thresholdIsExact() { assertEquals(0, Reading.streak(mapOf(today to 299_999L),today,5).current); assertEquals(1, Reading.streak(mapOf(today to 300_000L),today,5).current) }
    @Test fun yesterdayKeepsStreakAliveUntilTonight() { val d=(1L..7L).associate { today.minusDays(it) to 300_000L }; assertEquals(Streak(7,7,7),Reading.streak(d,today,5)) }
    @Test fun missedDayResetsWithoutAutomaticRecovery() { assertEquals(Streak(0,1,1),Reading.streak(mapOf(today.minusDays(2) to 300_000L),today,5)) }
    @Test fun longestStreakSurvivesReset() { val d=(4L..10L).associate { today.minusDays(it) to 300_000L }+mapOf(today to 300_000L); assertEquals(Streak(1,7,8),Reading.streak(d,today,5)) }
    @Test fun customizedMinimumApplies() { assertEquals(1, Reading.streak(mapOf(today to 600_000L),today,10).current); assertEquals(0, Reading.streak(mapOf(today to 599_999L),today,10).current) }
    @Test fun futureDatesCannotCreateStreak() { assertEquals(Streak(0,0,0),Reading.streak(mapOf(today.plusDays(1) to 300_000L),today,5)) }
    @Test fun splitExactlyAtJakartaMidnight() {
        val z=ZoneId.of("Asia/Jakarta"); val start=today.atTime(23,59,58).atZone(z).toInstant().toEpochMilli()
        val slices=Reading.split(start,start+5000,z)
        assertEquals(listOf("2026-10-31","2026-11-01"),slices.map{it.date}); assertEquals(listOf(2000L,3000L),slices.map{it.millis})
    }
    @Test fun daylightSavingSpringDayHas23Hours() {
        val z=ZoneId.of("America/New_York");val date=LocalDate.of(2026,3,8)
        val slices=Reading.split(date.atStartOfDay(z).toInstant().toEpochMilli(),date.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli(),z)
        assertEquals(23*3_600_000L,slices.single().millis)
    }
    @Test fun daylightSavingFallDayHas25Hours() {
        val z=ZoneId.of("America/New_York");val date=LocalDate.of(2026,11,1)
        assertEquals(25*3_600_000L,Reading.split(date.atStartOfDay(z).toInstant().toEpochMilli(),date.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli(),z).single().millis)
    }
    @Test fun reversedWallTimeNeverAddsDuration() { assertTrue(Reading.split(500,100,ZoneId.of("UTC")).isEmpty()) }
    @Test fun activeSessionUsesMonotonicClock() { assertEquals(5000L,Reading.activeDelta(10_000,15_000,true)); assertEquals(0L,Reading.activeDelta(10_000,15_000,false)) }
    @Test fun processRecoveryNeverBackfillsUnobservedTime() { assertEquals(0L,Reading.activeDelta(0,50_000,true)); assertEquals(0L,Reading.activeDelta(10_000,90_000,true)); assertEquals(0L,Reading.activeDelta(90_000,500,true)) }
    @Test fun progressIsBoundedAndUsesInChapterOffset() { assertEquals(.5f,Reading.progress(1,3,.5f),.0001f);assertEquals(0f,Reading.progress(1,0),0f);assertEquals(1f,Reading.progress(99,10),0f) }
    @Test fun goalsUseActualDurationAndClampAtCompletion() { assertEquals(.5f,Reading.goal(900_000,30),.0001f);assertEquals(1f,Reading.goal(3_600_000,30),0f);assertEquals(0f,Reading.goal(0,30),0f) }
    @Test fun aggregationAcrossMidnightPreservesEveryMillisecond() {
        val z=ZoneId.of("Asia/Jakarta");val start=today.atTime(23,59).atZone(z).toInstant().toEpochMilli()
        val parts=Reading.split(start,start+180_000,z);assertEquals(180_000L,parts.sumOf{it.millis})
        assertEquals(2,parts.map{it.date}.distinct().size)
    }
    @Test fun achievementsUseActualRecords() {
        assertTrue(Reading.achievements(0,Streak(0,0,0),0,false,emptySet()).isEmpty())
        val unlocked=Reading.achievements(1,Streak(7,30,30),10,true,setOf(6,23))
        assertEquals(setOf("First chapter","First five","Consistent reader","Dedicated reader","Bookworm","Scholar","Deep focus","Night owl","Early bird"),unlocked)
    }
}
