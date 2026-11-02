package com.spotibook.app.speech

import com.spotibook.app.data.Book
import com.spotibook.app.data.Repository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import java.io.File
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.spotibook.app.reader.PdfDocument

internal fun speechChunks(text:String):List<String> {
    val clean=text.replace(Regex("[\\t ]+")," ").trim()
    if(clean.isBlank())return emptyList()
    val result=mutableListOf<String>();var remaining=clean
    while(remaining.isNotEmpty()){
        var end=minOf(1800,remaining.length)
        if(end<remaining.length){val boundary=remaining.lastIndexOfAny(charArrayOf('.','!','?','\n',' '),end-1);if(boundary>900)end=boundary+1}
        result+=remaining.substring(0,end).trim();remaining=remaining.substring(end).trimStart()
    }
    return result
}

internal suspend fun readablePdfText(native:String,ocr:suspend ()->String):String = if(native.count{it.isLetterOrDigit()}>=8)native else ocr()

internal suspend fun recognizePdfPage(repo:Repository,book:Book,page:Int):String{
    val bitmap=PdfDocument(repo.documents.source(book)).let{pdf->try{pdf.render(page,2000)}finally{pdf.shutdown()}}
    val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    return suspendCancellableCoroutine{continuation->
        try{
            recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnCompleteListener(java.util.concurrent.Executor{it.run()}){task->
                try{if(continuation.isActive){if(task.isSuccessful)continuation.resume(task.result.text)else continuation.resumeWithException(task.exception?:IllegalStateException("OCR tidak dapat membaca halaman ini."))}}
                finally{bitmap.recycle();recognizer.close()}
            }
        }catch(e:Exception){bitmap.recycle();recognizer.close();if(continuation.isActive)continuation.resumeWithException(e)}
    }
}

internal suspend fun extractSpeech(repo:Repository,book:Book,page:Int):List<String> {
    val text=if(book.format=="PDF"){
        val cache=File(repo.documents.directory(book.id),"ocr-latin-v1-$page.txt")
        if(cache.isFile)return speechChunks(cache.readText())
        val native=try{PDDocument.load(repo.documents.source(book),com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(16L*1024*1024)).use{doc->
            require(!doc.isEncrypted && doc.currentAccessPermission.canExtractContent()){ "PDF ini tidak mengizinkan pembacaan teks." }
            PDFTextStripper().apply{startPage=page+1;endPage=page+1;sortByPosition=true}.getText(doc)
        }}catch(e:java.io.IOException){""}
        readablePdfText(native){recognizePdfPage(repo,book,page).also{if(it.isNotBlank()){val temp=File(cache.path+".tmp");temp.writeText(it);java.nio.file.Files.move(temp.toPath(),cache.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)}}}
    }else{
        val pub=repo.documents.publication(book)
        val root=File(repo.documents.directory(book.id),"content").canonicalFile
        val file=File(root,pub.chapters[page].path.substringBefore('#')).canonicalFile
        require(file.toPath().startsWith(root.toPath())){"Lokasi bab tidak valid."}
        Jsoup.parse(file,"UTF-8").apply{select("script,style,nav").remove()}.body().wholeText()
    }
    return speechChunks(text)
}
