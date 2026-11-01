package com.spotibook.app.data

import android.content.Context
import androidx.room.withTransaction
import com.spotibook.app.domain.Reading
import com.spotibook.app.reader.Documents
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.*
import java.util.UUID

class Repository(val context: Context, val database: SpotibookDatabase, val preferences: Preferences) {
    val dao = database.library()
    val documents = Documents(context, dao)
    // Imports, backup and restore share the same gate so files and DB snapshots agree.
    val dataGate = Mutex()
    suspend fun deleteBook(book: Book) = deleteBooks(setOf(book.id))
    suspend fun deleteBooks(ids: Set<String>) = dataGate.withLock { withContext(Dispatchers.IO) {
        val selected=dao.allBooks().filter{it.id in ids}
        database.withTransaction { ids.toList().chunked(500).forEach { dao.deleteBooks(it) } }
        selected.forEach { book ->
            val dir=documents.directory(book.id)
            check(!dir.exists()||dir.deleteRecursively()) { "Buku dihapus dari daftar, tetapi salinan lokal belum dapat dibersihkan." }
        }
    } }
    suspend fun removeFromShelf(ids:Set<String>,shelf:String) = database.withTransaction {
        ids.toList().chunked(500).forEach { dao.unlinkBooks(it,shelf) }
    }
    suspend fun hideHistory(ids:Set<String>) = database.withTransaction {
        ids.toList().chunked(500).forEach { dao.hideHistory(it) }
    }
    suspend fun expireHistory(today:LocalDate=LocalDate.now()) { dao.expireHistory(today.minusDays(29).toString()) }
    suspend fun addToShelf(bookIds: Set<String>, shelfId: String) = database.withTransaction {
        require(dao.allShelves().any { it.id == shelfId }) { "Rak ini sudah tidak tersedia." }
        require(bookIds.isNotEmpty() && dao.allBookIds().toSet().containsAll(bookIds)) { "Daftar buku berubah. Pilih ulang buku yang ingin dimasukkan." }
        dao.putMemberships(bookIds.map { BookShelf(it, shelfId) })
    }
    suspend fun position(id: String, position: Int, offset: Float, zoom: Float? = null) {
        database.withTransaction {
            val book = dao.book(id) ?: return@withTransaction
            val page = position.coerceIn(0, (book.total - 1).coerceAtLeast(0)); val scroll = offset.coerceIn(0f, 1f)
            val progress = Reading.progress(page, book.total, scroll)
            dao.putBook(book.copy(position = page, offset = scroll, zoom = zoom ?: book.zoom, progress = if (book.status == "Finished") 1f else progress, opened = System.currentTimeMillis(), status = if (book.status == "To read") "Reading" else book.status))
        }
    }
    suspend fun status(id: String, status: String) {
        database.withTransaction { dao.book(id)?.let { dao.putBook(it.copy(status = status, completed = if (status == "Finished") System.currentTimeMillis() else null, progress = if (status == "Finished") 1f else Reading.progress(it.position, it.total, it.offset))) } }
        unlockAchievements()
    }
    suspend fun unlockAchievements() {
        val sessions = dao.allSessions().filter { it.millis > 0 }; val settings = preferences.settings.first()
        val daily = sessions.groupBy { LocalDate.parse(it.date) }.mapValues { (_, v) -> v.sumOf { it.millis } }
        val streak = Reading.streak(daily, LocalDate.now(), settings.qualifyingMinutes)
        val hours = sessions.map { Instant.ofEpochMilli(it.start).atZone(ZoneId.of(it.zone)).hour }.toSet()
        Reading.achievements(sessions.map { it.id.substringBefore('/') }.toSet().size, streak, dao.allBooks().count { it.status == "Finished" }, dao.allFocus().any { it.plannedMillis >= 3_600_000 }, hours).forEach { dao.putAchievement(Achievement(it)) }
    }
    suspend fun resetStatistics() = dataGate.withLock {
        database.withTransaction { dao.clearSessions(); dao.clearAchievements(); dao.clearFocus() }
    }
}

class SessionTracker(private val repository: Repository, private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private var book: Book? = null
    private var runId = ""
    private var segment = 0
    private var focusId = ""
    private var previous = 0L
    private var touched = 0L
    private var active = false
    private var position = 0
    private var job: Job? = null
    fun touch() { touched = android.os.SystemClock.elapsedRealtime() }
    suspend fun open(book: Book) = mutex.withLock {
        commit(); this.book = book; position = book.position; runId = UUID.randomUUID().toString();segment=0
        val focus=repository.preferences.focus.first();focusId=if(focus.running)focus.id else ""
        previous = android.os.SystemClock.elapsedRealtime(); touch()
        job?.cancel(); job = scope.launch { while (isActive) { delay(5000); mutex.withLock { commit() } } }
    }
    suspend fun focusBoundary(id:String)=mutex.withLock{
        commit();focusId=id
        if(book!=null)segment++
        previous=android.os.SystemClock.elapsedRealtime()
    }
    suspend fun visibility(visible: Boolean) = mutex.withLock { commit(); active = visible; previous = android.os.SystemClock.elapsedRealtime(); if (visible) touch() }
    suspend fun updatePosition(bookId: String, value: Int) = mutex.withLock { if (book?.id == bookId) position = value }
    suspend fun close(bookId: String) = mutex.withLock { if (book?.id == bookId) { commit(); active = false; book = null; job?.cancel(); job = null; repository.unlockAchievements() } }
    private suspend fun commit() {
        val now = android.os.SystemClock.elapsedRealtime()
        val delta = Reading.activeDelta(previous, now, active && now - touched < 300_000)
        previous = now
        val selected = book ?: return
        if (delta == 0L) return
        val end = System.currentTimeMillis(); val zone = ZoneId.systemDefault()
        repository.database.withTransaction {
            Reading.split(end - delta, end, zone).forEach { slice ->
                val key = "$runId/${slice.date}/${zone.id}${if(segment==0)""else"/$segment"}"; val old = repository.dao.session(key)
                val pages = if (selected.format == "PDF" && old != null && position == old.endPosition + 1) 1 else 0
                repository.dao.putSession(Session(key, selected.id, selected.title, slice.date, minOf(old?.start ?: slice.start, slice.start), maxOf(old?.end ?: slice.end, slice.end),
                    (old?.millis ?: 0L) + slice.millis, old?.startPosition ?: selected.position, position, (old?.pages ?: 0) + pages, zone.id, focusId=focusId))
            }
        }
    }
}
