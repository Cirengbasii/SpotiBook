package com.spotibook.app.reader

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.*
import com.spotibook.app.MainActivity
import com.spotibook.app.background.Widgets
import com.spotibook.app.data.*
import com.spotibook.app.data.Annotation
import com.spotibook.app.ui.*
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

@Composable fun ReaderScreen(model: AppState, id: String, back: () -> Unit) {
    val books by model.books.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle()
    val allAnnotations by model.annotations.collectAsStateWithLifecycle()
    val configuration=LocalConfiguration.current
    val landscape=configuration.screenWidthDp>configuration.screenHeightDp
    var initial by remember(id) { mutableStateOf<Book?>(null) }
    var publication by remember(id) { mutableStateOf<Publication?>(null) }
    var pdf by remember(id) { mutableStateOf<PdfDocument?>(null) }
    var failure by remember(id) { mutableStateOf<String?>(null) }
    var controls by rememberSaveable(id) { mutableStateOf(true) }
    var page by rememberSaveable(id) { mutableIntStateOf(-1) }
    var offset by rememberSaveable(id) { mutableFloatStateOf(0f) }
    val portraitZoom = rememberSaveable(id) { mutableFloatStateOf(1f) }
    val spreadZoom = rememberSaveable(id) { mutableFloatStateOf(1f) }
    val zoomState=if(landscape)spreadZoom else portraitZoom
    var zoom by zoomState
    var navKey by remember { mutableLongStateOf(0L) }
    var jump by remember { mutableStateOf<Pair<Int, Float>?>(null) }
    var anchor by remember { mutableStateOf<String?>(null) }
    var focusSheet by remember { mutableStateOf(false) }
    var panel by remember { mutableStateOf<ReaderPanelPage?>(null) }
    var addNote by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf<TextSelection?>(null) }
    var pageEntry by remember { mutableStateOf(false) }
    var searchingDialog by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var found by remember { mutableStateOf<List<Int>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var brightnessPreview by remember { mutableStateOf<Float?>(null) }
    val activity = LocalActivity.current ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(Triple(page, offset, portraitZoom.floatValue))
    val currentPdf by rememberUpdatedState(pdf)
    val annotations = allAnnotations.filter { it.bookId == id }
    val book = books.firstOrNull { it.id == id } ?: initial
    val ready = initial != null && page >= 0 && failure == null
    val isPdf = book?.format == "PDF"
    fun announce(message: String) { model.messages.tryEmit(message) }
    fun go(index: Int, fraction: Float = 0f, fragment: String? = null) {
        if (!ready) return
        val target = index.coerceIn(0,(book!!.total-1).coerceAtLeast(0))
        if (isPdf) { jump=target to fraction.coerceIn(0f,1f); navKey++ }
        else { page=target; offset=fraction.coerceIn(0f,1f); anchor=fragment; navKey++ }
    }
    fun relative(delta:Int){go(if(isPdf&&landscape)spreadStart(page)+delta*2 else page+delta)}
    fun annotate(kind: String, note: String = "", selected: TextSelection? = null) {
        if (!ready) return
        model.task { model.repo.dao.putAnnotation(Annotation(UUID.randomUUID().toString(),id,kind,page,offset,selected?.text ?: "${if(isPdf) "Halaman" else "Bab"} ${page+1}",note,selected?.start ?: -1,selected?.end ?: -1)) }
    }
    LaunchedEffect(id) {
        var opening: PdfDocument? = null
        try {
            var loaded=withContext(Dispatchers.IO) { model.repo.dao.book(id) } ?: error("Buku ini tidak ada lagi di pustaka.")
            withContext(Dispatchers.IO) { require(model.repo.documents.source(loaded).isFile) { "Berkas buku tidak tersedia. Impor kembali berkas aslinya dari Pustaka untuk menyambungkan posisi baca." } }
            val openedPublication=if(loaded.format!="PDF") withContext(Dispatchers.IO) { model.repo.documents.publication(loaded) } else null
            if(loaded.format=="PDF") opening=withContext(Dispatchers.IO) { PdfDocument(model.repo.documents.source(loaded)) }
            val count=opening?.count ?: openedPublication!!.chapters.size
            require(count>0) { "Buku ini tidak memiliki halaman yang dapat dibaca." }
            if(loaded.total!=count) { loaded=loaded.copy(total=count,position=loaded.position.coerceIn(0,count-1)); model.repo.dao.putBook(loaded) }
            initial=loaded
            if(page<0) { page=loaded.position; offset=loaded.offset; portraitZoom.floatValue=loaded.zoom } else page=page.coerceIn(0,count-1)
            pdf=opening; publication=openedPublication
            model.app.tracker.open(loaded)
            model.app.tracker.visibility(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)&&activity.hasWindowFocus())
        } catch(e: Exception) {
            if(pdf==null) withContext(NonCancellable) { opening?.shutdown() }
            if(e is CancellationException) throw e
            failure=e.message ?: "Buku tidak dapat dibuka."
        }
    }
    LaunchedEffect(page,offset,zoom,ready) { if(ready) { delay(350); model.app.tracker.updatePosition(id,page); model.repo.position(id,page,offset,portraitZoom.floatValue) } }
    LaunchedEffect(settings.brightness) { brightnessPreview=null }
    DisposableEffect(id,lifecycle) {
        val observer=LifecycleEventObserver { _,event -> when(event) {
            Lifecycle.Event.ON_RESUME -> model.app.scope.launch { model.app.tracker.visibility(activity.hasWindowFocus()) }
            Lifecycle.Event.ON_PAUSE -> model.app.scope.launch { model.app.tracker.visibility(false); if(current.first>=0) model.repo.position(id,current.first,current.second,current.third) }
            else -> Unit
        } }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); model.app.scope.launch {
            model.app.tracker.updatePosition(id,current.first.coerceAtLeast(0)); model.app.tracker.close(id)
            if(current.first>=0) model.repo.position(id,current.first,current.second,current.third)
            currentPdf?.shutdown(); Widgets.update(model.app)
        } }
    }
    ReaderWindow(activity,settings,controls,brightnessPreview,ready)
    val volumeTurn by rememberUpdatedState<(Boolean)->Unit> { next -> relative(if(next) 1 else -1) }
    DisposableEffect(activity,settings.volumeKeys,ready) {
        val host=activity as? MainActivity
        val handler: ((Boolean)->Unit)? = if(settings.volumeKeys&&ready) { next -> volumeTurn(next) } else null
        host?.readerPageKey=handler
        onDispose { if(host?.readerPageKey===handler) host?.readerPageKey=null }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            failure!=null -> Column(Modifier.align(Alignment.Center).padding(28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) { Text("Buku tidak dapat dibuka",style=MaterialTheme.typography.headlineMedium); Text(failure!!); Button(back) { Text("Kembali ke pustaka") } }
            pdf!=null&&ready -> PdfReader(pdf!!,initial!!.copy(position=page,offset=offset),jump,navKey,zoomState,settings,{index,fraction->page=index;offset=fraction},{controls=!controls},{delta->relative(delta)},::announce)
            publication!=null&&ready -> TextReader(File(model.repo.documents.directory(id),"content"),publication!!,page,offset,settings,annotations,navKey,anchor,
                {index,fraction->page=index;offset=fraction;anchor=null},{index,fraction,fragment->go(index,fraction,fragment)},
                {selected,note->if(note){selection=selected;addNote=true}else{annotate("Highlight",selected=selected);announce("Sorotan disimpan")}},{controls=!controls},::announce)
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        if(controls) {
            Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(),color=MaterialTheme.colorScheme.surface.copy(alpha=.97f),shadowElevation=1.dp) {
                Row(Modifier.statusBarsPadding().padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack,"Kembali ke pustaka") }
                    Text(book?.title ?: "Membuka buku…",Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.titleMedium)
                    val bookmark=annotations.firstOrNull { it.kind=="Bookmark"&&it.position==page }
                    IconButton({if(bookmark==null){annotate("Bookmark");announce("Penanda ditambahkan")}else model.task{model.repo.dao.deleteAnnotation(bookmark.id)}},enabled=ready) { Icon(if(bookmark==null) Icons.Outlined.BookmarkBorder else Icons.Outlined.Bookmark,if(bookmark==null) "Tambahkan penanda" else "Hapus penanda") }
                    IconButton({focusSheet=true},enabled=ready){Icon(Icons.Outlined.Timelapse,"Fokus membaca")}
                    if(isPdf&&landscape) {
                        IconButton({zoom=(zoom-.5f).coerceAtLeast(1f)},enabled=ready&&zoom>1f){Icon(Icons.Outlined.ZoomOut,"Perkecil PDF")}
                        IconButton({zoom=(zoom+.5f).coerceAtMost(5f)},enabled=ready&&zoom<5f){Icon(Icons.Outlined.ZoomIn,"Perbesar PDF")}
                    }
                    IconButton({panel=ReaderPanelPage.Brightness},enabled=ready) { Icon(Icons.Outlined.LightMode,"Atur kecerahan") }
                    IconButton({panel=ReaderPanelPage.Settings},enabled=ready) { Icon(Icons.Outlined.Tune,"Pengaturan baca") }
                }
            }
            if(ready) Surface(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),color=MaterialTheme.colorScheme.surface.copy(alpha=.97f),shadowElevation=1.dp) {
                Column(Modifier.navigationBarsPadding().padding(horizontal=16.dp)) {
                    var target by remember(page) { mutableFloatStateOf(page.toFloat()) }
                    Slider(target,{target=it},enabled=book!!.total>1,valueRange=0f..(book.total-1).coerceAtLeast(1).toFloat(),onValueChangeFinished={go(target.toInt())},modifier=Modifier.height(32.dp).semantics{contentDescription="Posisi baca"})
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                        IconButton({relative(-1)},enabled=if(isPdf&&landscape)page/2>0 else page>0) { Icon(Icons.Outlined.ChevronLeft,"Halaman sebelumnya") }
                        TextButton({pageEntry=true}) { Text(if(isPdf&&landscape)"Halaman ${spreadStart(page)+1}–${minOf(spreadStart(page)+2,book.total)} / ${book.total}"else "${if(isPdf) "Halaman" else "Bab"} ${page+1} / ${book.total}") }
                        IconButton({panel=ReaderPanelPage.Contents}) { Icon(Icons.Outlined.FormatListBulleted,"Isi buku") }
                        IconButton({relative(1)},enabled=if(isPdf&&landscape)page/2<(book.total-1)/2 else page<book.total-1) { Icon(Icons.Outlined.ChevronRight,"Halaman berikutnya") }
                    }
                }
            }
        } else if(ready) TextButton({controls=true},Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom=4.dp)) { Text("${page+1} / ${book!!.total}",style=MaterialTheme.typography.labelSmall) }
    }
    if(pageEntry&&ready) TextEntry(if(isPdf)"Ke halaman"else"Ke bab",(page+1).toString(),true,1..book!!.total,{pageEntry=false}){go(it.toInt()-1)}
    if(addNote) {
        var note by remember{mutableStateOf("")}
        AlertDialog(onDismissRequest={addNote=false;selection=null},title={Text("Tulis catatan")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){selection?.let{Text(it.text.take(240),style=MaterialTheme.typography.bodySmall)};OutlinedTextField(note,{note=it},label={Text("Catatan")},minLines=3)}},confirmButton={TextButton({annotate("Note",note.trim(),selection);addNote=false;selection=null},enabled=note.isNotBlank()&&note.length<=20_000){Text("Simpan")}},dismissButton={TextButton({addNote=false;selection=null}){Text("Batal")}})
    }
    if(focusSheet&&ready)ReaderFocusSheet(model,id){focusSheet=false}
    panel?.takeIf{ready}?.let { start -> ReaderPanel(start,book!!,page,settings,publication,annotations,model::preferences,
        {brightnessPreview=it},{index,fraction,fragment->go(index,fraction,fragment);panel=null},{panel=null;addNote=true},
        {note->model.task{model.repo.dao.deleteAnnotation(note.id)}},
        {if(Build.VERSION.SDK_INT>=35){panel=null;searchingDialog=true}else announce("Pencarian PDF memerlukan Android 15 atau lebih baru.")},
        {model.task{model.repo.status(id,if(book.status=="Finished")"Reading"else"Finished");Widgets.update(model.app)};panel=null},
        {panel=null;brightnessPreview=null}) }
    if(searchingDialog) AlertDialog(onDismissRequest={if(!searching)searchingDialog=false},title={Text("Cari dalam PDF")},text={Column{
        OutlinedTextField(query,{query=it;found=null},label={Text("Kata atau frasa")},singleLine=true)
        if(searching)CircularProgressIndicator(Modifier.padding(12.dp))
        if(found?.isEmpty()==true)Text("Tidak ada halaman yang cocok.",Modifier.padding(vertical=12.dp))
        Column(Modifier.heightIn(max=220.dp).verticalScroll(rememberScrollState())){found.orEmpty().forEach{index->TextButton({go(index);searchingDialog=false}){Text("Halaman ${index+1}")}}}
    }},confirmButton={TextButton({searching=true;model.task{try{found=pdf?.search(query.trim()).orEmpty()}finally{searching=false}}},enabled=query.isNotBlank()&&!searching){Text("Cari")}},dismissButton={TextButton({searchingDialog=false},enabled=!searching){Text("Tutup")}})
}
