package com.spotibook.app.speech

import com.spotibook.app.data.Book
import com.spotibook.app.data.Repository
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.jsoup.Jsoup
import java.io.File

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

internal fun extractSpeech(repo:Repository,book:Book,page:Int):List<String> {
    val text=if(book.format=="PDF"){
        PDDocument.load(repo.documents.source(book),com.tom_roush.pdfbox.io.MemoryUsageSetting.setupMixed(16L*1024*1024)).use{doc->
            require(!doc.isEncrypted && doc.currentAccessPermission.canExtractContent()){ "PDF ini tidak mengizinkan pembacaan teks." }
            PDFTextStripper().apply{startPage=page+1;endPage=page+1;sortByPosition=true}.getText(doc)
        }
    }else{
        val pub=repo.documents.publication(book)
        val root=File(repo.documents.directory(book.id),"content").canonicalFile
        val file=File(root,pub.chapters[page].path.substringBefore('#')).canonicalFile
        require(file.toPath().startsWith(root.toPath())){"Lokasi bab tidak valid."}
        Jsoup.parse(file,"UTF-8").apply{select("script,style,nav").remove()}.body().wholeText()
    }
    return speechChunks(text)
}
