package com.spotibook.app.domain

import java.time.*

data class Streak(val current: Int, val longest: Int, val totalDays: Int)
data class TimeSlice(val date: String, val start: Long, val end: Long, val millis: Long)

object Reading {
    fun streak(dailyMillis: Map<LocalDate, Long>, today: LocalDate, thresholdMinutes: Int): Streak {
        val days = dailyMillis.filter { it.key <= today && it.value >= thresholdMinutes.coerceAtLeast(1) * 60_000L }.keys.sorted()
        var longest = 0; var run = 0; var previous: LocalDate? = null
        days.forEach { day -> run = if (previous?.plusDays(1) == day) run + 1 else 1; longest = maxOf(longest, run); previous = day }
        var cursor = if (today in days) today else today.minusDays(1)
        val qualified = days.toHashSet(); var current = 0
        while (cursor in qualified) { current++; cursor = cursor.minusDays(1) }
        return Streak(current, longest, days.size)
    }
    fun split(start: Long, end: Long, zone: ZoneId): List<TimeSlice> {
        if (end <= start) return emptyList()
        val out = mutableListOf<TimeSlice>(); var cursor = start
        while (cursor < end) {
            val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val midnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val stop = minOf(end, midnight)
            out += TimeSlice(date.toString(), cursor, stop, stop - cursor); cursor = stop
        }
        return out
    }
    // Lost checkpoints are never recovered from wall-clock time after process death.
    fun activeDelta(previousElapsed: Long, nowElapsed: Long, active: Boolean): Long =
        if (active && previousElapsed > 0 && nowElapsed - previousElapsed in 1..15_000) nowElapsed - previousElapsed else 0
    fun progress(position: Int, count: Int, offset: Float = 0f): Float =
        if (count <= 0) 0f else ((position.coerceAtLeast(0) + offset.coerceIn(0f, 1f)) / count).coerceIn(0f, 1f)
    fun goal(millis: Long, targetMinutes: Int): Float = (millis.toDouble() / (targetMinutes.coerceAtLeast(1) * 60_000)).toFloat().coerceIn(0f, 1f)
    fun achievements(sessionCount: Int, streak: Streak, finished: Int, hasDeepFocus: Boolean, hours: Set<Int>): Set<String> = buildSet {
        if (sessionCount > 0) add("First chapter")
        if (streak.totalDays >= 5) add("First five")
        if (streak.longest >= 7) add("Consistent reader")
        if (streak.longest >= 30) add("Dedicated reader")
        if (finished >= 5) add("Bookworm")
        if (finished >= 10) add("Scholar")
        if (hasDeepFocus) add("Deep focus")
        if (hours.any { it in 0..4 || it >= 22 }) add("Night owl")
        if (hours.any { it in 5..8 }) add("Early bird")
    }
}

/** History is a 30-local-day view; the statistical ledger remains intact. */
fun recentHistory(records: List<com.spotibook.app.data.Session>, today: java.time.LocalDate): List<com.spotibook.app.data.Session> {
    val first=today.minusDays(29).toString();val last=today.toString()
    return records.filter { !it.historyHidden && it.date >= first && it.date <= last }.sortedByDescending { it.start }
}
