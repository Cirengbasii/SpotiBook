package com.spotibook.app.speech

import android.content.Intent
import android.speech.tts.*
import androidx.test.core.app.ApplicationProvider
import com.spotibook.app.SpotibookApp
import com.spotibook.app.data.Book
import com.spotibook.app.reader.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.*
import java.io.File
import java.util.Locale
import java.util.UUID
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=SpotibookApp::class)
class SpeechIntegrationTest{
    private fun makeBook(format:String):Book{
        val app=ApplicationProvider.getApplicationContext<SpotibookApp>()
        val book=Book(UUID.randomUUID().toString(),"Buku suara",format=format,fileName=if(format=="PDF")"book.pdf"else"book.html",hash="b".repeat(64),total=1)
        val dir=app.repository.documents.directory(book.id);dir.mkdirs()
        if(format=="PDF"){
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(app)
            PDDocument().use{doc->val p=PDPage();doc.addPage(p);PDPageContentStream(doc,p).use{it.beginText();it.setFont(PDType1Font.HELVETICA,16f);it.newLineAtOffset(40f,700f);it.showText("Selamat pagi. Mari membaca buku.");it.endText()};doc.save(File(dir,"book.pdf"))}
        }else{
            File(dir,"content").mkdirs();File(dir,"content/bab.html").writeText("<html><head><style>rahasia</style></head><body><p>Selamat pagi. Mari membaca buku.</p><script>jangan dibaca</script></body></html>")
            File(dir,"publication.json").writeText(Json.encodeToString(Publication("Buku suara","Penulis",listOf(Chapter("Bab satu","bab.html")))))
        }
        runBlocking{app.repository.dao.putBook(book)};return book
    }
    @Test fun extractsRealPdfAndImportedEbookContent(){
        val app=ApplicationProvider.getApplicationContext<SpotibookApp>()
        for(format in listOf("PDF","EPUB")){
            val book=makeBook(format);val text=extractSpeech(app.repository,book,0).joinToString(" ")
            assertTrue(text.contains("Mari membaca buku."));assertFalse(text.contains("jangan dibaca"));assertFalse(text.contains("rahasia"))
        }
    }
    @Test fun playsOfflineIndonesianAndPauseStopReleaseResources(){
        val app=ApplicationProvider.getApplicationContext<SpotibookApp>();val book=makeBook("EPUB")
        ShadowTextToSpeech.addVoice(Voice("id-offline",Locale.forLanguageTag("id-ID"),300,100,false,emptySet()))
        ShadowTextToSpeech.addVoice(Voice("id-online",Locale.forLanguageTag("id-ID"),500,100,true,emptySet()))
        val controller=Robolectric.buildService(SpeechService::class.java).create();val service=controller.get()
        val engine=ShadowTextToSpeech.getLastTextToSpeechInstance();val shadow=Shadows.shadowOf(engine)
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
        service.onStartCommand(Intent(app,SpeechService::class.java).setAction("OPEN").putExtra("book",book.id),0,1)
        val deadline=System.nanoTime()+10_000_000_000L
        while(shadow.lastSpokenText==null && System.nanoTime()<deadline){ShadowLooper.idleMainLooper();Thread.sleep(10)}
        assertEquals("id-offline",shadow.currentVoice.name)
        assertTrue(shadow.lastSpokenText.contains("Mari membaca buku."));assertEquals("",SpeechService.state.value.error)
        service.onStartCommand(Intent().setAction("PAUSE"),0,2);assertFalse(SpeechService.state.value.playing);assertTrue(shadow.isStopped)
        service.onStartCommand(Intent().setAction("STOP"),0,3);assertEquals("",SpeechService.state.value.bookId)
        controller.destroy();assertTrue(shadow.isShutdown)
    }
}
