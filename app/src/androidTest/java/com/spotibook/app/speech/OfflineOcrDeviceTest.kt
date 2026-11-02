package com.spotibook.app.speech

import android.graphics.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.spotibook.app.SpotibookApp
import com.spotibook.app.data.Book
import kotlinx.coroutines.*
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class OfflineOcrDeviceTest{
    @Test fun readsImageOnlyPdfWithBundledOcr()=runBlocking(Dispatchers.IO){
        val app=ApplicationProvider.getApplicationContext<SpotibookApp>()
        val book=Book(UUID.randomUUID().toString(),"OCR test",format="PDF",fileName="scan.pdf",total=1,hash="c".repeat(64))
        val dir=app.repository.documents.directory(book.id);dir.mkdirs()
        val bitmap=Bitmap.createBitmap(1200,1600,Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply{drawColor(Color.WHITE);drawText("Selamat pagi Indonesia",80f,350f,Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textSize=70f})}
        val pdf=android.graphics.pdf.PdfDocument()
        try{val page=pdf.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(1200,1600,1).create());page.canvas.drawBitmap(bitmap,0f,0f,null);pdf.finishPage(page);File(dir,"scan.pdf").outputStream().use{pdf.writeTo(it)}}finally{pdf.close()}
        bitmap.recycle()
        val result=withTimeout(60000){extractSpeech(app.repository,book,0)}.joinToString(" ")
        assertTrue(result.lowercase().contains("indonesia"))
        assertTrue(File(dir,"ocr-latin-v1-0.txt").isFile)
        assertTrue(extractSpeech(app.repository,book,0).joinToString(" ")==result)
    }
}
