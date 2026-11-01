package com.spotibook.app.data

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.room.Room
import com.spotibook.app.reader.SafeFiles
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.util.UUID
import java.io.File
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35], application=Application::class)
class LibraryIntegrationTest {
    @Test fun importsCollectionsPositionsAnnotationsAndBackupsRoundTripWithoutDuplicates() = runBlocking {
        val context=RuntimeEnvironment.getApplication() as Context
        val db=Room.inMemoryDatabaseBuilder(context,SpotibookDatabase::class.java).allowMainThreadQueries().build()
        val repo=Repository(context,db,Preferences(context)); val dao=repo.dao
        try {
            val txt=File(context.cacheDir,"A real book.txt").apply{writeText("One chapter.\nAnother quiet page.")}
            val book=repo.documents.import(Uri.fromFile(txt))
            assertEquals("TXT",book.format);assertTrue(repo.documents.source(book).exists())
            assertTrue(runCatching{repo.documents.import(Uri.fromFile(txt))}.isFailure)
            assertEquals(1,dao.allBooks().size)
            repo.position(book.id,0,.75f);assertEquals(.75f,dao.book(book.id)!!.offset,0f)
            dao.putShelf(Shelf("shelf-one","Favorites"));dao.putShelf(Shelf("shelf-two","Research"))
            dao.putMembership(BookShelf(book.id,"shelf-one"));dao.putMembership(BookShelf(book.id,"shelf-two"));assertEquals(2,dao.allMemberships().size)
            dao.putAnnotation(Annotation("bookmark",book.id,"Bookmark",0,.75f,"My place"));assertEquals(.75f,dao.allAnnotations().single().offset,0f)
            dao.putSession(Session("run/day",book.id,book.title,"2026-10-31",1000,301000,300000,0,0,0,"Asia/Jakarta"))
            val backup=Backup(repo);val zip=File(context.cacheDir,"backup.zip");backup.export(Uri.fromFile(zip),true)
            assertEquals(1,backup.inspect(Uri.fromFile(zip)).books.size)
            assertEquals(0,backup.restore(Uri.fromFile(zip),false));assertEquals(1,dao.allSessions().size)
            repo.deleteBook(book);assertTrue(txt.exists());assertTrue(dao.allAnnotations().isEmpty());assertTrue(dao.allMemberships().isEmpty());assertEquals(1,dao.allSessions().size)
            assertEquals(1,backup.restore(Uri.fromFile(zip),false));val restored=dao.allBooks().single()
            assertEquals(.75f,restored.offset,0f);assertEquals(1,dao.allAnnotations().size);assertEquals(2,dao.allMemberships().size);assertTrue(repo.documents.source(restored).isFile)
            val metadata=File(context.cacheDir,"metadata.zip");backup.export(Uri.fromFile(metadata),false)
            repo.deleteBook(restored);backup.restore(Uri.fromFile(metadata),false)
            val unavailable=dao.allBooks().single();assertFalse(repo.documents.source(unavailable).exists())
            val repaired=repo.documents.import(Uri.fromFile(txt));assertEquals(unavailable.id,repaired.id);assertEquals(.75f,repaired.offset,0f)
            val epub=File(context.cacheDir,"Reader.epub")
            ZipOutputStream(epub.outputStream()).use { z ->
                val files=mapOf("mimetype" to "application/epub+zip", "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>",
                    "OPS/book.opf" to "<package xmlns:dc='http://purl.org/dc/elements/1.1/'><metadata><dc:title>Real EPUB</dc:title><dc:creator>A Reader</dc:creator></metadata><manifest><item id='c1' href='chapter.html' media-type='application/xhtml+xml'/></manifest><spine><itemref idref='c1'/></spine></package>",
                    "OPS/chapter.html" to "<html><head><title>First chapter</title></head><body><h1>Hello</h1><p>A book.</p><script>alert('unsafe')</script></body></html>")
                files.forEach{(path,text)->z.putNextEntry(ZipEntry(path));z.write(text.toByteArray());z.closeEntry()}
            }
            val e=repo.documents.import(Uri.fromFile(epub));assertEquals("Real EPUB",e.title);assertEquals("A Reader",e.author);assertEquals(1,repo.documents.publication(e).chapters.size)
            assertFalse(File(repo.documents.directory(e.id),"content/OPS/chapter.html").readText().contains("<script"))
            val html=File(context.cacheDir,"page.html").apply{writeText("<title>Local HTML</title><p>Read me.</p><iframe src='https://example.com'></iframe>")}
            assertEquals("Local HTML",repo.documents.import(Uri.fromFile(html)).title)
            val csv=File(context.cacheDir,"history.csv");backup.exportCsv(Uri.fromFile(csv));assertTrue(csv.readText().contains("duration_seconds"));assertTrue(csv.readText().contains("300"))
            val bad=File(context.cacheDir,"unsafe.zip");ZipOutputStream(bad.outputStream()).use { z -> z.putNextEntry(ZipEntry("../escape.txt"));z.write("bad".toByteArray());z.closeEntry() }
            assertTrue(runCatching{SafeFiles.unzip(bad,File(context.cacheDir,"unpacked"))}.isFailure)
            assertTrue(runCatching{backup.restore(Uri.fromFile(bad),false)}.isFailure)
            assertEquals(3,dao.allBooks().size)
            dao.deleteShelf("shelf-one");assertEquals(1,dao.allMemberships().size);assertEquals(3,dao.allBooks().size)
            val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
            try {
                ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
                val tracker=SessionTracker(repo,scope);tracker.open(dao.book(repaired.id)!!);tracker.visibility(true)
                ShadowSystemClock.advanceBy(Duration.ofSeconds(5));tracker.visibility(false)
                assertEquals(305_000L,dao.allSessions().sumOf{it.millis})
                ShadowSystemClock.advanceBy(Duration.ofHours(1));tracker.close(repaired.id)
                assertEquals(305_000L,dao.allSessions().sumOf{it.millis})
                val recovered=SessionTracker(repo,scope);recovered.open(dao.book(repaired.id)!!);recovered.visibility(true);recovered.close(repaired.id)
                assertEquals(305_000L,dao.allSessions().sumOf{it.millis})
            } finally { scope.cancel() }
        } finally { db.close() }
    }
    @Test fun roomPersistsBooksAcrossDatabaseRecreation() = runBlocking {
        val context=RuntimeEnvironment.getApplication() as Context;val name="persistence-${UUID.randomUUID()}.db"
        val book=Book(UUID.randomUUID().toString(),"Persistent book",format="TXT",fileName="book.txt",hash="a".repeat(64),total=3,position=1,offset=.25f)
        var db=Room.databaseBuilder(context,SpotibookDatabase::class.java,name).build()
        try {
            db.library().putBook(book);db.close();db=Room.databaseBuilder(context,SpotibookDatabase::class.java,name).build()
            assertEquals(book,db.library().book(book.id))
        } finally { db.close();context.deleteDatabase(name) }
    }
}
