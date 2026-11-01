package com.spotibook.app

import com.spotibook.app.reader.DeviceBooks
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DeviceBooksTest {
    @Test fun discoversNestedBooksAndSkipsPrivateFoldersAndEmptyFiles() = runBlocking {
        val root=Files.createTempDirectory("spotibook-scan").toFile()
        try {
            fun file(path:String)=java.io.File(root,path).apply{parentFile.mkdirs();writeText("data")}
            file("Download/BUKU.PDF");file("Documents/nested/novel.epub");file("Documents/notes.txt");file("Android/data/another.app/private.pdf");file("Android/obb/private.epub");file("Android/media/books/shared.pdf")
            java.io.File(root,"empty.pdf").createNewFile()
            var count=0
            val results=DeviceBooks.scan(listOf(root,root)){count=it}
            assertEquals(3,results.size);assertEquals(3,count)
            assertEquals(setOf("BUKU.PDF","novel.epub","shared.pdf"),results.map{it.file.name}.toSet())
            assertTrue(results.all{it.size==4L});assertEquals("data",java.io.File(root,"Download/BUKU.PDF").readText())
        } finally { assertEquals(java.io.File(System.getProperty("java.io.tmpdir")).canonicalFile,root.canonicalFile.parentFile);root.deleteRecursively() }
    }
    @Test fun cancellationDuringDiscoveryDoesNotReturnPartialResults() = runBlocking {
        val root=Files.createTempDirectory("spotibook-cancel").toFile()
        try {
            repeat(60){java.io.File(root,"book-$it.pdf").writeText("pdf")}
            var observed=0
            try { DeviceBooks.scan(listOf(root)){ observed=it;if(it==25)throw CancellationException("cancel") };fail("Scan must propagate cancellation") }
            catch(_:CancellationException){assertEquals(25,observed)}
        } finally {assertEquals(java.io.File(System.getProperty("java.io.tmpdir")).canonicalFile,root.canonicalFile.parentFile);root.deleteRecursively()}
    }
}
