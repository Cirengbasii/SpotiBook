package com.spotibook.app

import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.spotibook.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfImportTest {
    @Test fun actualPdfCanBeImportedRenderedAndPositionRestored() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=Room.inMemoryDatabaseBuilder(context,SpotibookDatabase::class.java).build()
        val repo=Repository(context,db,Preferences(context));val file=File(context.cacheDir,"test-document.pdf")
        try {
            val pdf=PdfDocument()
            try { repeat(2) { index -> val page=pdf.startPage(PdfDocument.PageInfo.Builder(300,500,index+1).create());page.canvas.drawText("Spotibook page ${index+1}",20f,30f,android.graphics.Paint().apply{textSize=18f});pdf.finishPage(page) };file.outputStream().use{pdf.writeTo(it)} } finally { pdf.close() }
            val book=repo.documents.import(Uri.fromFile(file));assertEquals(2,book.total);assertTrue(File(repo.documents.directory(book.id),"cover.png").isFile)
            val renderer=com.spotibook.app.reader.PdfDocument(repo.documents.source(book));val bitmap=renderer.render(1,600);assertEquals(600,bitmap.width);bitmap.recycle();renderer.shutdown()
            repo.position(book.id,1,.2f,2f);val saved=repo.dao.book(book.id)!!;assertEquals(1,saved.position);assertEquals(.2f,saved.offset,0f);assertEquals(2f,saved.zoom,0f)
            repo.deleteBook(saved);assertTrue(file.exists())
        } finally { db.close();file.delete() }
    }
}
