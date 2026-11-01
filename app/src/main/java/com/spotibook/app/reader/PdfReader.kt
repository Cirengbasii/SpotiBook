package com.spotibook.app.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.pager.*
import androidx.compose.animation.core.tween
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.*
import com.spotibook.app.data.Book
import com.spotibook.app.data.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable
import java.io.File
import kotlin.math.*


class PdfDocument(file: File) : Closeable {
    private val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = try { PdfRenderer(descriptor) } catch (e: Exception) { descriptor.close(); throw e }
    private val gate = Mutex(); private var closed = false
    val count = renderer.pageCount
    suspend fun render(index: Int, width: Int, theme: String = "Light", pixelBudget:Int = 7_260_000): Bitmap = withContext(Dispatchers.IO) { gate.withLock {
        check(!closed) { "PDF sudah ditutup." }
        renderer.openPage(index).use { page ->
            val scale = minOf(width.coerceIn(360, 2200).toFloat() / page.width, 3300f / page.height, sqrt(pixelBudget.toFloat()/(page.width.toFloat()*page.height)))
            val bitmap = Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1), (page.height*scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            if (theme == "Sepia") PdfSepia.apply(bitmap)
            bitmap
        }
    } }
    suspend fun search(query: String): List<Int> = withContext(Dispatchers.IO) { gate.withLock {
        if (Build.VERSION.SDK_INT >= 35) {
            check(!closed)
            (0 until renderer.pageCount).filter { i -> ensureActive(); renderer.openPage(i).use { it.searchText(query).isNotEmpty() } }
        } else error("Pencarian PDF memerlukan Android 15 atau lebih baru.")
    } }
    suspend fun shutdown() = withContext(Dispatchers.IO) { gate.withLock { close() } }
    override fun close() { if (!closed) { closed = true; renderer.close() } }
}

private data class PdfGeometry(val fit:PdfFit,val margin:Int,val width:Dp,val height:Dp)
internal fun spreadStart(page:Int)=page.coerceAtLeast(0)/2*2

@Composable
fun PdfReader(
    document:PdfDocument,book:Book,jump:Pair<Int,Float>?,navigationKey:Long,
    zoomState:MutableFloatState,settings:Settings,location:(Int,Float)->Unit,
    toggle:()->Unit,turn:(Int)->Unit,error:(String)->Unit
){
    val latestLocation by rememberUpdatedState(location)
    val currentBook by rememberUpdatedState(book)
    var handledCommand by remember(document){mutableLongStateOf(-1)}
    var transforming by remember{mutableStateOf(false)}
    val zoom by zoomState
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()){
        val spread=maxWidth>maxHeight
        val stride=if(spread)2 else 1
        val units=(document.count+stride-1)/stride
        val mode=settings.pageTurn
        val background=if(spread)Color(0xFF11171C)else when(settings.readerTheme){"Dark","Black"->Color(0xFF171C20);"Sepia"->Color(0xFFDDD1B9);else->Color(0xFFE5E7E4)}
        val geometry=PdfGeometry(if(spread)PdfFit.Page else settings.pdfFit,settings.pdfMargin,maxWidth,maxHeight)
        val viewportHeight=maxHeight;val viewportWidth=maxWidth
        Box(Modifier.fillMaxSize().background(background)){
        key(mode,spread){
            val start=remember{book.position.coerceIn(0,document.count-1)/stride}
            val offsets=remember{mutableStateMapOf(start to book.offset)}
            fun report(unit:Int,fraction:Float){
                val first=unit*stride
                val logical=currentBook.position.takeIf{it/stride==unit}?:first
                latestLocation(logical,fraction.coerceIn(0f,1f))
            }
            fun tap(unit:Int,fraction:Float){
                if(spread&&zoom<=1f){val first=unit*stride;val target=(first+if(fraction>.5f)1 else 0).coerceAtMost(document.count-1);latestLocation(target,offsets[unit]?:0f)}
                readerTap(fraction,settings,turn,toggle)
            }
            if(mode==PageTurn.Scrolling){
                val list=rememberLazyListState(start)
                val heights=remember(geometry){mutableStateMapOf<Int,Int>()}
                val widths=remember(geometry){mutableStateMapOf<Int,Float>()}
                var navigating by remember(geometry){mutableStateOf(true)}
                LaunchedEffect(navigationKey,geometry){
                    val command=jump?.takeIf{navigationKey>handledCommand}
                    val page=command?.first?:if(navigating)currentBook.position else return@LaunchedEffect
                    val fraction=command?.second?:currentBook.offset
                    val unit=(page/stride).coerceIn(0,units-1)
                    navigating=true;list.scrollToItem(unit)
                    val height=snapshotFlow{heights[unit]}.filterNotNull().first()
                    list.scrollToItem(unit,(height*fraction).toInt())
                    if(command!=null){handledCommand=navigationKey;latestLocation(page,fraction)}
                    navigating=false
                }
                LaunchedEffect(list,geometry){snapshotFlow{Triple(list.firstVisibleItemIndex,list.firstVisibleItemScrollOffset,navigating)}.collect{(unit,pixels,moving)->
                    if(!moving)heights[unit]?.let{report(unit,pixels.toFloat()/it.coerceAtLeast(1))}
                }}
                val density=LocalDensity.current
                PdfZoomViewport(zoomState,list,widths[list.firstVisibleItemIndex]?:with(density){viewportWidth.toPx()},interaction={transforming=it},tap={tap(list.firstVisibleItemIndex,it)}){_,enabled->
                    LazyColumn(Modifier.fillMaxSize(),state=list,userScrollEnabled=enabled){items(units,key={it}){unit->
                        PdfPageGroup(document,unit*stride,stride,viewportHeight,false,zoomState,settings,spread,0f,0,null,false,
                            {height,width->heights[unit]=height;widths[unit]=width},{},{},{},error)
                    }}
                }
            }else{
                val pager=rememberPagerState(initialPage=start){units}
                LaunchedEffect(navigationKey){if(jump!=null&&navigationKey>handledCommand){
                    val unit=(jump.first/stride).coerceIn(0,units-1);offsets[unit]=jump.second
                    if(mode==PageTurn.NoAnimation)pager.scrollToPage(unit)else pager.animateScrollToPage(unit,animationSpec=tween(300))
                    handledCommand=navigationKey;latestLocation(jump.first,jump.second)
                }}
                LaunchedEffect(pager){snapshotFlow{pager.settledPage}.collect{report(it,offsets[it]?:0f)}}
                val pageContent: @Composable PagerScope.(Int)->Unit={unit->
                    val relative={pager.currentPage-unit+pager.currentPageOffsetFraction}
                    val layer=Modifier.fillMaxSize().zIndex(when(mode){PageTurn.Overlay->-relative();PageTurn.Withdrawal->relative();PageTurn.NoAnimation->if(unit==pager.settledPage)1f else 0f;else->0f}).graphicsLayer{
                        val distance=relative()
                        alpha=if(mode==PageTurn.NoAnimation&&unit!=pager.settledPage)0f else 1f
                        translationX=when{mode==PageTurn.NoAnimation->distance*size.width;mode==PageTurn.Overlay&&distance>0->distance*size.width;mode==PageTurn.Withdrawal&&distance<0->distance*size.width;else->0f}
                        shadowElevation=if(mode==PageTurn.Withdrawal||mode==PageTurn.Overlay)18f*(1-abs(distance)).coerceIn(0f,1f)else 0f
                    }
                    Box(layer.background(background)){
                        PdfPageGroup(document,unit*stride,stride,viewportHeight,true,zoomState,settings,spread,offsets[unit]?:0f,navigationKey,jump?.takeIf{it.first/stride==unit}?.second,unit==pager.settledPage,
                            {_,_->},{fraction->offsets[unit]=fraction;if(unit==pager.settledPage)report(unit,fraction)},{transforming=it},{tap(unit,it)},error,
                            fold={if(mode==PageTurn.Withdrawal)relative().coerceIn(0f,1f)else 0f})
                    }
                }
                if(mode==PageTurn.Scroll)VerticalPager(pager,Modifier.fillMaxSize(),userScrollEnabled=zoom<=1f&&!transforming,key={it},pageContent=pageContent)
                else HorizontalPager(pager,Modifier.fillMaxSize(),userScrollEnabled=zoom<=1f&&!transforming,key={it},pageContent=pageContent)
            }
        }
        }
    }
}
private fun readerTap(fraction:Float,settings:Settings,turn:(Int)->Unit,toggle:()->Unit){
    if(settings.tapToTurn&&fraction<.25f)turn(-1)else if(settings.tapToTurn&&fraction>.75f)turn(1)else toggle()
}

@Composable private fun PdfPageGroup(
    document:PdfDocument,first:Int,stride:Int,viewportHeight:Dp,paged:Boolean,zoomState:MutableFloatState,settings:Settings,spread:Boolean,
    startOffset:Float,commandKey:Long,commandOffset:Float?,active:Boolean,measured:(Int,Float)->Unit,scrolled:(Float)->Unit,
    interaction:(Boolean)->Unit,tap:(Float)->Unit,error:(String)->Unit,fold:()->Float={0f}
){
    val zoom by zoomState
    val count=minOf(stride,document.count-first)
    BoxWithConstraints(Modifier.fillMaxWidth().then(if(paged)Modifier.fillMaxHeight()else Modifier)){
        val density=LocalDensity.current;val margin=settings.pdfMargin.dp;val gap=if(spread&&count==2)2.dp else 0.dp
        val available=(maxWidth-margin*2-gap).coerceAtLeast(1.dp)
        val slot=available/count
        val renderWidth=with(density){(slot.toPx()*maxOf(1.5f,zoom)).toInt().coerceAtMost((2200f/sqrt(count.toFloat())).toInt())}
        val images=remember(document,first,count){mutableStateListOf<Bitmap?>().apply{repeat(count){add(null)}}}
        var failure by remember(document,first){mutableStateOf<String?>(null)};var retry by remember{mutableIntStateOf(0)}
        LaunchedEffect(document,first,renderWidth,settings.readerTheme,retry){
            if(images.any{it!=null})delay(150)
            try{failure=null;repeat(count){part->images[part]=document.render(first+part,renderWidth,settings.readerTheme,7_260_000/count)}}
            catch(e:CancellationException){throw e}catch(_:Exception){failure="Halaman ${first+1} tidak dapat dibuka.";error(failure!!)}
        }
        val aspects=images.map{image->image?.let{it.width.toFloat()/it.height}?:.707f}
        val widths=aspects.map{aspect->if(spread||settings.pdfFit==PdfFit.Page)minOf(slot,(viewportHeight-margin*2).coerceAtLeast(1.dp)*aspect)else slot}
        val heights=widths.mapIndexed{i,width->width/aspects[i]}
        val extent=(heights.maxOrNull()?:1.dp)+margin*2
        val paperWidth=widths.fold(0.dp){total,w->total+w}+gap
        val extentPx=with(density){extent.toPx()};val widthPx=with(density){paperWidth.toPx()}
        val scroll=rememberScrollState();val latestScrolled by rememberUpdatedState(scrolled);val latestActive by rememberUpdatedState(active)
        var restored by remember{mutableStateOf(!paged)};var appliedKey by remember{mutableLongStateOf(commandKey)};var appliedGeometry by remember{mutableStateOf<Pair<Dp,Dp>?>(null)}
        val ready=images.all{it!=null}||failure!=null
        LaunchedEffect(ready,commandKey,paperWidth,viewportHeight){if(paged&&ready){
            val geometry=paperWidth to viewportHeight
            val fraction=when{!restored->startOffset;appliedKey!=commandKey->commandOffset?:return@LaunchedEffect;appliedGeometry!=geometry->startOffset;else->return@LaunchedEffect}
            restored=false;withFrameNanos{};scroll.scrollTo((extentPx*fraction).toInt());restored=true;appliedKey=commandKey;appliedGeometry=geometry
        }}
        LaunchedEffect(scroll,extentPx){snapshotFlow{Triple(scroll.value,scroll.maxValue,restored)}.collect{(value,maximum,done)->if(done&&latestActive&&(!spread||maximum>0))latestScrolled(value/extentPx.coerceAtLeast(1f))}}
        if(!paged&&ready)SideEffect{measured(extentPx.roundToInt(),widthPx)}
        val pages: @Composable (Dp) -> Unit={space->
            Box(Modifier.fillMaxWidth().height(maxOf(space,extent)),contentAlignment=Alignment.Center){
                if(failure!=null)Column(horizontalAlignment=Alignment.CenterHorizontally){Text(failure!!);TextButton({retry++}){Text("Coba lagi")}}
                else if(!ready)CircularProgressIndicator()
                else Row(horizontalArrangement=Arrangement.spacedBy(gap),verticalAlignment=Alignment.CenterVertically){
                    repeat(count){part->images[part]?.let{image->PaperImage(image,settings.readerTheme,fold,Modifier.size(widths[part],heights[part]),"Halaman ${first+part+1}")}}
                }
            }
        }
        if(paged)PdfZoomViewport(zoomState,scroll,widthPx,extentPx,active,interaction,tap){virtualHeight,enabled->Box(Modifier.fillMaxSize().verticalScroll(scroll,enabled=enabled)){pages(virtualHeight)}}
        else pages(0.dp)
    }
}

/** A cylindrical strip mesh bends the trailing edge while the sheet withdraws. */
@Composable
private fun PaperImage(bitmap: Bitmap, theme: String, fold: () -> Float, modifier: Modifier, description: String) {
    val paint = remember(theme) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = pdfColors(theme)?.let { android.graphics.ColorMatrixColorFilter(it) }
    } }
    val vertices = remember { FloatArray(25 * 2 * 2) }
    val colors = remember { IntArray(25 * 2) }
    Canvas(modifier.semantics { contentDescription = description }) {
        val progress = fold()
        val angle = progress * PI.toFloat() * .85f
        for (row in 0..1) for (column in 0..24) {
            val u = column / 24f
            val bend = ((u - .68f) / .32f).coerceIn(0f, 1f)
            val theta = bend * angle
            val x = if (u <= .68f || angle < .001f) u * size.width else size.width * (.68f + .32f * sin(theta)/angle)
            val depth = if (angle < .001f) 0f else size.width * .32f * (1-cos(theta))/angle
            val n = row*25 + column
            vertices[n*2] = x
            vertices[n*2+1] = if (row == 0) depth*.08f else size.height-depth*.08f
            val shade = (255*(1-.24f*sin(theta).coerceAtLeast(0f))).toInt().coerceIn(0,255)
            colors[n] = android.graphics.Color.rgb(shade, shade, shade)
        }
        drawIntoCanvas { canvas -> canvas.nativeCanvas.drawBitmapMesh(bitmap, 24, 1, vertices, 0, colors, 0, paint) }
        if (progress > .01f) {
            val edge = vertices[48]
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .18f * sin(progress*PI).toFloat()))), topLeft = Offset((edge-24.dp.toPx()).coerceAtLeast(0f), 0f), size = androidx.compose.ui.geometry.Size(24.dp.toPx(), size.height))
        }
    }
}

