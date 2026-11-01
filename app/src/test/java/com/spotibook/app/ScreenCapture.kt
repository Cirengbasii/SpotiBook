package com.spotibook.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File

// PixelCopy needs a real Android Surface. Draw the laid-out native Robolectric view instead.
fun captureScreen(activity:Activity,name:String) {
    val view=activity.window.decorView
    val bitmap=Bitmap.createBitmap(view.width.coerceAtLeast(1),view.height.coerceAtLeast(1),Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    val file=File("build/qa/$name.png");file.parentFile?.mkdirs()
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
}
