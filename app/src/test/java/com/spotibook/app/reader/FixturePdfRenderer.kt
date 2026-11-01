package com.spotibook.app.reader

import android.graphics.*
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.robolectric.annotation.*
import org.robolectric.shadow.api.Shadow

// Robolectric does not implement Android's PDF processor on Windows.
// Only that framework boundary is simulated; the production pager, gestures and mesh are exercised.
@Implements(PdfRenderer::class)
class FixturePdfRenderer {
    @Implementation fun __constructor__(descriptor:ParcelFileDescriptor) {}
    @Implementation fun getPageCount()=3
    @Implementation fun openPage(index:Int):PdfRenderer.Page {
        val page=Shadow.newInstanceOf(PdfRenderer.Page::class.java)
        Shadow.extract<FixturePdfPage>(page).index=index
        return page
    }
    @Implementation fun close() {}
}
@Implements(PdfRenderer.Page::class)
class FixturePdfPage {
    var index=0
    @Implementation fun getWidth()=360
    @Implementation fun getHeight()=540
    @Implementation fun render(bitmap:Bitmap,clip:Rect?,matrix:Matrix?,mode:Int) {
        val canvas=Canvas(bitmap);canvas.drawColor(Color.WHITE)
        canvas.scale(bitmap.width/360f,bitmap.height/540f)
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(36,76,59) }
        canvas.drawRect(24f,40f,336f,160f,paint)
        paint.color=Color.WHITE;paint.textSize=26f;canvas.drawText("HALAMAN ${index+1}",42f,105f,paint)
        paint.color=Color.DKGRAY;paint.textSize=18f
        repeat(14){canvas.drawText("Contoh teks halaman ${index+1}",30f,220f+it*28,paint)}
    }
    @Implementation fun close() {}
}
