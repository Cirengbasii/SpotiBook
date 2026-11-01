package com.spotibook.app

import android.graphics.*
import android.view.*
import android.widget.*
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.emptyPreferences
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import com.spotibook.app.background.*
import com.spotibook.app.data.Session
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.*
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=SpotibookApp::class,qualifiers="mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WidgetTest {
    @OptIn(androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi::class)
    @Test fun streakWidgetRendersAWeekAndFireFromCommittedReading()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<SpotibookApp>()
        val today=LocalDate.now()
        app.repository.dao.putSession(Session("run/$today","gone","Buku",today.toString(),1,2,300_000,0,1,zone="Asia/Jakarta"))
        for(size in listOf(DpSize(180.dp,110.dp),DpSize(300.dp,210.dp),DpSize(250.dp,180.dp),DpSize(420.dp,240.dp))) {
            val rv=StreakWidget().compose(app,size=size,state=emptyPreferences())
            val frame=FrameLayout(app);val view=rv.apply(app,frame);frame.addView(view)
            frame.measure(View.MeasureSpec.makeMeasureSpec(size.width.value.toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size.height.value.toInt(),View.MeasureSpec.EXACTLY));frame.layout(0,0,frame.measuredWidth,frame.measuredHeight)
            fun flatten(v:View):List<View> = listOf(v)+(if(v is ViewGroup)(0 until v.childCount).flatMap{flatten(v.getChildAt(it))}else emptyList())
            val children=flatten(frame)
            assertTrue(children.filterIsInstance<TextView>().any{it.text.toString().contains("1 hari beruntun")})
            assertTrue(children.any{it.contentDescription?.toString()=="Api membaca $today"})
            val bitmap=Bitmap.createBitmap(frame.width,frame.height,Bitmap.Config.ARGB_8888);frame.draw(Canvas(bitmap))
            val out=File("build/qa/widget-streak-${size.width.value.toInt()}.png");out.parentFile.mkdirs();out.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
    }
}
