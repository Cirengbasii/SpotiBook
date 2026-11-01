package com.spotibook.app.data

import android.net.Uri
import androidx.room.withTransaction
import com.spotibook.app.reader.SafeFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.zip.*

@Serializable data class BackupData(val format: String = "spotibook", val version: Int = 1,
    val created: Long = System.currentTimeMillis(), val includesBooks: Boolean,
    val books: List<Book>, val shelves: List<Shelf>, val memberships: List<BookShelf>,
    val sessions: List<Session>, val annotations: List<Annotation>, val achievements: List<Achievement>,
    val focus: List<FocusRecord>, val settings: Settings)

class Backup(private val repo: Repository) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    suspend fun export(uri: Uri, includeBooks: Boolean) = repo.dataGate.withLock { withContext(Dispatchers.IO) {
        val data = repo.database.withTransaction { BackupData(includesBooks = includeBooks, books = repo.dao.allBooks(), shelves = repo.dao.allShelves(),
            memberships = repo.dao.allMemberships(), sessions = repo.dao.allSessions(), annotations = repo.dao.allAnnotations(),
            achievements = repo.dao.allAchievements(), focus = repo.dao.allFocus(), settings = repo.preferences.settings.first()) }
        repo.context.contentResolver.openOutputStream(uri, "wt")?.use { output -> ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json")); zip.write(json.encodeToString(data).toByteArray()); zip.closeEntry()
            if (includeBooks) data.books.forEach { book ->
                repo.documents.directory(book.id).walkTopDown().filter { it.isFile }.forEach { file ->
                    zip.putNextEntry(ZipEntry("books/${book.id}/${file.relativeTo(repo.documents.directory(book.id)).invariantSeparatorsPath}"))
                    file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                }
            }
        } } ?: error("Berkas cadangan tidak dapat dibuat.")
    } }
    suspend fun inspect(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        repo.context.contentResolver.openInputStream(uri)?.use { input -> ZipInputStream(input.buffered()).use { zip ->
            var entries = 0
            while (true) {
                val entry = zip.nextEntry ?: break; require(++entries <= 50_000) { "Cadangan berisi terlalu banyak berkas." }
                if (entry.name == "manifest.json") {
                    val bytes = java.io.ByteArrayOutputStream().also { out ->
                        val buffer = ByteArray(8192); var count = 0
                        while (true) { val n = zip.read(buffer); if (n < 0) break; count += n
                            require(count <= 16 * 1024 * 1024) { "Metadata cadangan terlalu besar." }; out.write(buffer, 0, n) }
                    }.toByteArray()
                    return@withContext json.decodeFromString<BackupData>(bytes.decodeToString()).also(::validate)
                }
                zip.closeEntry()
            }
        } }; error("Berkas ini bukan cadangan Spotibook.")
    }
    private fun validate(data: BackupData) {
        require(data.format == "spotibook" && data.version == 1) { "Format atau versi cadangan tidak didukung." }
        val ids = data.books.map { it.id }.toSet()
        require(ids.size == data.books.size && data.books.map { it.hash }.distinct().size == data.books.size) { "Ada buku duplikat dalam cadangan." }
        require(data.shelves.map { it.id }.distinct().size == data.shelves.size) { "Ada rak duplikat dalam cadangan." }
        val shelves = data.shelves.map { it.id }.toSet()
        data.books.forEach { b ->
            require(runCatching { UUID.fromString(b.id) }.isSuccess && b.hash.matches(Regex("[0-9a-f]{64}"))) { "Identitas buku dalam cadangan tidak valid." }
            require(b.fileName.matches(Regex("book\\.(pdf|epub|txt|html|htm)")) && b.format in listOf("PDF", "EPUB", "TXT", "HTML")) { "Berkas buku dalam cadangan tidak valid." }
            require(b.total in 1..1_000_000 && b.position in 0 until b.total && b.offset.isFinite() && b.offset in 0f..1f && b.progress.isFinite() && b.progress in 0f..1f && b.zoom in 1f..5f) { "Posisi baca dalam cadangan tidak valid." }
            b.cover?.let { SafeFiles.child(File(repo.context.cacheDir, "validation"), it) }
        }
        data.memberships.forEach { require(it.bookId in ids && it.shelfId in shelves) { "Isi rak dalam cadangan tidak valid." } }
        data.annotations.forEach { require(it.bookId in ids && it.kind in listOf("Bookmark", "Highlight", "Note") && it.position >= 0 && it.offset in 0f..1f) { "Catatan dalam cadangan tidak valid." } }
        data.sessions.forEach { s ->
            LocalDate.parse(s.date); ZoneId.of(s.zone)
            require(s.millis >= 0 && s.end >= s.start && s.pages >= 0 && s.startPosition >= 0 && s.endPosition >= 0) { "Sesi membaca dalam cadangan tidak valid." }
        }
        val s = data.settings
        require(s.focusSound in setOf("Gentle","Custom","Silent") && s.focusVolume.isFinite() && s.focusVolume in 0f..1f && s.focusSoundName.length<=200) { "Pengaturan bunyi fokus tidak valid." }
        require(s.appearance in listOf("System", "Light", "Dark") && s.accent in listOf("Ocean", "Forest", "Olive", "Amber") && s.readerTheme in listOf("Light", "Sepia", "Dark", "Black") && s.fontFamily in listOf("Serif", "Sans", "Mono") && s.pdfMargin in 0..32) { "Pengaturan tampilan dalam cadangan tidak valid." }
        require(s.dailyMinutes in 1..1440 && s.weeklyMinutes in 1..10_080 && s.monthlyMinutes in 1..44_640 && s.qualifyingMinutes in 1..1440 && s.annualBooks in 1..10_000 && s.fontSize in 12..36 && s.lineSpacing in 1f..2.5f && s.margin in 4..48 && s.brightness in -1f..1f && s.reminderHour in 0..23 && s.reminderMinute in 0..59) { "Pengaturan cadangan tidak valid." }
    }
    suspend fun restore(uri: Uri, restoreSettings: Boolean): Int = repo.dataGate.withLock { withContext(Dispatchers.IO) {
        val stage = File(repo.context.cacheDir, "restore-${UUID.randomUUID()}").apply { mkdirs() }
        val copied = mutableListOf<File>(); var committed = false
        try {
            val archive = File(stage, "backup.zip")
            repo.context.contentResolver.openInputStream(uri)?.use { input -> archive.outputStream().use { out ->
                val buf = ByteArray(65536); var size = 0L
                while (true) { val n = input.read(buf); if (n < 0) break; size += n; require(size <= 2L * 1024 * 1024 * 1024) { "Ukuran cadangan melebihi 2 GB." }; out.write(buf, 0, n) }
            } } ?: error("Cadangan tidak dapat dibaca.")
            val extracted = File(stage, "unpacked"); SafeFiles.unzip(archive, extracted, 2L * 1024 * 1024 * 1024, 50_000)
            val manifest = File(extracted, "manifest.json"); require(manifest.length() <= 16 * 1024 * 1024) { "Metadata cadangan terlalu besar." }
            val data = json.decodeFromString<BackupData>(manifest.readText()); validate(data)
            val existing = repo.dao.allBooks(); val mapping = mutableMapOf<String, String>(); val newBooks = mutableListOf<Book>()
            data.books.forEach { book ->
                val same = existing.firstOrNull { it.hash == book.hash }
                if (same != null && (repo.documents.source(same).isFile || !File(extracted, "books/${book.id}").isDirectory)) mapping[book.id] = same.id else {
                    val id = same?.id ?: if (existing.any { it.id == book.id } || repo.documents.directory(book.id).exists()) UUID.randomUUID().toString() else book.id
                    mapping[book.id] = id
                    val sourceDir = File(extracted, "books/${book.id}")
                    if (sourceDir.exists()) {
                        val original = SafeFiles.child(sourceDir, book.fileName)
                        require(original.isFile) { "Berkas buku dalam cadangan tidak ditemukan." }
                        val hash = MessageDigest.getInstance("SHA-256"); original.inputStream().use { stream -> val bytes = ByteArray(65536); while (true) { val n = stream.read(bytes); if (n < 0) break; hash.update(bytes, 0, n) } }
                        require(hash.digest().joinToString("") { "%02x".format(it) } == book.hash) { "Isi berkas buku tidak cocok dengan cadangan." }
                        val dest = repo.documents.directory(id)
                        require(!dest.exists()) { "Folder buku tidak lengkap. Buat cadangan sebelum memperbaikinya." }
                        copied += dest; check(sourceDir.copyRecursively(dest, overwrite = false)) { "Berkas buku tidak dapat dipulihkan." }
                    }
                    newBooks += same ?: book.copy(id = id)
                }
            }
            repo.database.withTransaction {
                newBooks.forEach { repo.dao.putBook(it) }
                val localShelves = repo.dao.allShelves(); val shelfIds = mutableMapOf<String, String>()
                data.shelves.forEach { shelf ->
                    val match = localShelves.firstOrNull { it.name == shelf.name }
                    val id = match?.id ?: if (localShelves.any { it.id == shelf.id }) UUID.randomUUID().toString() else shelf.id
                    shelfIds[shelf.id] = id; if (match == null) repo.dao.putShelf(shelf.copy(id = id))
                }
                data.memberships.forEach { repo.dao.putMembership(BookShelf(mapping.getValue(it.bookId), shelfIds.getValue(it.shelfId))) }
                data.sessions.forEach { s -> if (repo.dao.session(s.id) == null) repo.dao.putSession(s.copy(bookId = mapping[s.bookId] ?: s.bookId)) }
                val annotationIds = repo.dao.allAnnotations().map { it.id }.toHashSet()
                data.annotations.filter { it.id !in annotationIds }.forEach { repo.dao.putAnnotation(it.copy(bookId = mapping.getValue(it.bookId))) }
                data.achievements.forEach { repo.dao.putAchievement(it) }; data.focus.forEach { repo.dao.putFocus(it.copy(bookId = mapping[it.bookId] ?: it.bookId)) }
            }
            committed = true
            if (restoreSettings) repo.preferences.update { data.settings }
            newBooks.size
        } catch (e: Exception) {
            if (!committed) withContext(NonCancellable) { copied.forEach { folder ->
                // A cancelled transaction can finish committing before cancellation is delivered.
                if (runCatching { repo.dao.book(folder.name) == null }.getOrDefault(false)) folder.deleteRecursively()
            } }
            throw e
        }
        finally { stage.deleteRecursively() }
    } }
    suspend fun exportCsv(uri: Uri) = withContext(Dispatchers.IO) {
        fun quote(text: String) = "\"${text.replace("\"", "\"\"")}\""
        repo.context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { out ->
            out.appendLine("book,title,date,start,end,duration_seconds,start_position,end_position,pages,timezone")
            repo.dao.allSessions().sortedBy { it.start }.forEach { s -> out.appendLine(listOf(s.bookId, s.bookTitle, s.date, s.start.toString(), s.end.toString(), (s.millis / 1000).toString(), s.startPosition.toString(), s.endPosition.toString(), s.pages.toString(), s.zone).joinToString(",") { quote(it) }) }
        } ?: error("Berkas ekspor tidak dapat dibuat.")
    }
}
