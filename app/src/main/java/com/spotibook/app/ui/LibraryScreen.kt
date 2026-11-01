package com.spotibook.app.ui

import androidx.activity.compose.BackHandler
import androidx.room.withTransaction
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.background.Widgets
import com.spotibook.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun LibraryScreen(model: AppState, open: (String) -> Unit, import: () -> Unit) {
    val books by model.books.collectAsStateWithLifecycle(); val shelves by model.shelves.collectAsStateWithLifecycle()
    val links by model.memberships.collectAsStateWithLifecycle(); val settings by model.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }; var sort by rememberSaveable { mutableStateOf("Recently opened") }
    var status by rememberSaveable { mutableStateOf("All books") }; var shelfId by rememberSaveable { mutableStateOf<String?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }; var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }; var filterMenu by remember { mutableStateOf(false) }
    var detailId by remember { mutableStateOf<String?>(null) }; var scanner by remember { mutableStateOf(false) }
    var editingShelf by remember { mutableStateOf<Shelf?>(null) }; var newShelf by remember { mutableStateOf(false) }
    var deleteShelf by remember { mutableStateOf(false) }; var assign by remember { mutableStateOf(false) }
    var deleteChoice by remember { mutableStateOf(false) };var deleteScope by remember{mutableStateOf<String?>(null)}
    val selectedShelf = shelves.firstOrNull { it.id == shelfId }
    val memberIds = remember(links, shelfId) { links.filter { it.shelfId == shelfId }.map { it.bookId }.toSet() }
    val filtered = books.filter { b -> (search.isBlank() || b.title.contains(search, true) || b.author.contains(search, true)) && (status == "All books" || b.status == status) && (shelfId == null || b.id in memberIds) }.let { list -> when(sort) {
        "Recently added" -> list.sortedByDescending { it.added }; "Title A–Z" -> list.sortedBy { it.title.lowercase() }; "Author" -> list.sortedBy { it.author.lowercase() }
        "Progress" -> list.sortedByDescending { it.progress }; "Finished first" -> list.sortedByDescending { it.status == "Finished" }; else -> list.sortedByDescending { it.opened }
    } }
    val shelfView = tab == 1 && shelfId == null && !selecting
    val grid = rememberLazyGridState()
    LaunchedEffect(tab, shelfId, selecting) { grid.scrollToItem(0) }
    LaunchedEffect(books) { selected = selected.filter { id -> books.any { it.id == id } } }
    BackHandler(selecting || shelfId != null) { if(selecting) { selecting=false; selected=emptyList() } else { shelfId=null;tab=1 } }
    fun choose(id: String) { selecting=true; selected=if(id in selected) selected-id else selected+id }
    Column(Modifier.fillMaxSize()) {
        if(selecting) Surface(color=MaterialTheme.colorScheme.primaryContainer, shadowElevation=3.dp) {
            Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    IconButton({selecting=false;selected=emptyList()}) { Icon(Icons.Outlined.Close,"Batal memilih") }
                    Text("${selected.size} dipilih",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    val all = filtered.isNotEmpty() && filtered.all { it.id in selected }
                    TextButton({ val ids=filtered.map { it.id }.toSet(); selected=if(all)selected.filterNot { it in ids }else(selected+ids).distinct() }, enabled=filtered.isNotEmpty()) { Text(if(all)"Batal semua"else"Pilih semua") }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Button({assign=true},Modifier.weight(1f).heightIn(min=52.dp),enabled=selected.isNotEmpty(),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp)) { Icon(Icons.Outlined.LibraryBooks,null);Spacer(Modifier.width(8.dp));Text("Masukkan ke rak") }
                    OutlinedButton({deleteChoice=true},Modifier.heightIn(min=52.dp),enabled=selected.isNotEmpty(),contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp)) { Icon(Icons.Outlined.DeleteOutline,null);Text("Hapus") }
                }
            }
        }
        LazyVerticalGrid(if(shelfView)GridCells.Adaptive(300.dp)else if(settings.grid)GridCells.Adaptive(145.dp)else GridCells.Fixed(1),
            Modifier.weight(1f).testTag("library-grid"),state=grid,contentPadding=PaddingValues(20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(!selecting) {
                item(span={GridItemSpan(maxLineSpan)}) {
                    if(selectedShelf!=null) {
                        TextButton({shelfId=null;tab=1}) { Icon(Icons.Outlined.ChevronLeft,null);Text("Semua rak") }
                        Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){PageTitle("Rak buku",selectedShelf.name,"${memberIds.size} buku")}; IconButton({editingShelf=selectedShelf}){Icon(Icons.Outlined.MoreHoriz,"Ubah rak ${selectedShelf.name}")} }
                    } else Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)){Text("Pustaka",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary);Text("${books.size} buku tersimpan",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        FilledIconButton(import,modifier=Modifier.size(52.dp)) { Icon(Icons.Outlined.Add,"Impor buku") }
                    }
                }
                if(selectedShelf==null) {
                    item(span={GridItemSpan(maxLineSpan)}) { OutlinedButton({scanner=true},Modifier.fillMaxWidth().heightIn(min=52.dp)) { Icon(Icons.Outlined.FindInPage,null);Spacer(Modifier.width(8.dp));Text("Cari buku di perangkat") } }
                    val recent=books.firstOrNull { it.status=="Reading" }
                    if(recent!=null) item(span={GridItemSpan(maxLineSpan)}) { LibraryResume(recent,model){open(recent.id)} }
                    item(span={GridItemSpan(maxLineSpan)}) { TabRow(tab,containerColor=Color.Transparent) {
                        Tab(tab==0,{tab=0;search=""},text={Text("Buku",style=MaterialTheme.typography.titleMedium)})
                        Tab(tab==1,{tab=1;search=""},text={Text("Rak buku",style=MaterialTheme.typography.titleMedium)})
                    } }
                }
            }
            if(shelfView) {
                item(span={GridItemSpan(maxLineSpan)}) { Row(verticalAlignment=Alignment.CenterVertically) { Text("${shelves.size} rak buku",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);FilledTonalButton({newShelf=true},Modifier.heightIn(min=48.dp)){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text("Buat rak")} } }
                if(shelves.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { EmptyState("Buat rak pertama","Kelompokkan buku dengan deretan sampul yang mudah dikenali.","Buat rak"){newShelf=true} }
                items(shelves,key={it.id}) { shelf ->
                    val ids=links.filter { it.shelfId==shelf.id }.map { it.bookId }.toSet()
                    val content=books.filter { it.id in ids }
                    ShelfCard(shelf,content,model,{shelfId=shelf.id;search="";status="All books"},{editingShelf=shelf})
                }
            } else {
                item(span={GridItemSpan(maxLineSpan)}) { OutlinedTextField(search,{search=it},Modifier.fillMaxWidth(),placeholder={Text("Cari judul atau penulis")},leadingIcon={Icon(Icons.Outlined.Search,null)},singleLine=true) }
                item(span={GridItemSpan(maxLineSpan)}) { Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) { Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    Box { FilledTonalButton({filterMenu=true},contentPadding=PaddingValues(horizontal=12.dp,vertical=10.dp)){Icon(Icons.Outlined.FilterList,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("Filter")}
                        DropdownMenu(filterMenu,{filterMenu=false}){listOf("All books","To read","Reading","Finished").forEach{option->DropdownMenuItem(text={Text(option.labelId())},onClick={status=option;filterMenu=false},trailingIcon={if(status==option)Icon(Icons.Outlined.Check,null)})}}
                    }
                    Box { IconButton({sortMenu=true}){Icon(Icons.Outlined.Sort,"Urutkan buku")};DropdownMenu(sortMenu,{sortMenu=false}){listOf("Recently opened","Recently added","Title A–Z","Author","Progress","Finished first").forEach{option->DropdownMenuItem(text={Text(option.labelId())},onClick={sort=option;sortMenu=false})}} }
                    Spacer(Modifier.weight(1f))
                    if(!selecting) TextButton({selecting=true}) { Icon(Icons.Outlined.Checklist,null,Modifier.size(20.dp));Spacer(Modifier.width(5.dp));Text("Pilih") }
                    IconButton({model.preferences{it.copy(grid=!it.grid)}}){Icon(if(settings.grid)Icons.Outlined.ViewList else Icons.Outlined.GridView,if(settings.grid)"Tampilan daftar"else"Tampilan sampul")}
                }
                Text("${filtered.size} buku · ${status.labelId()}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                if(filtered.isEmpty()) item(span={GridItemSpan(maxLineSpan)}) { EmptyState(if(books.isEmpty())"Belum ada buku"else"Buku tidak ditemukan",if(books.isEmpty())"Impor buku atau pindai PDF dan EPUB di perangkat."else"Coba judul atau filter lain.",if(selectedShelf!=null)"Pilih buku untuk rak"else if(books.isEmpty())"Impor buku"else null) { if(selectedShelf!=null){shelfId=null;tab=0;selecting=true}else import() } }
                items(filtered,key={it.id}) { book -> LibraryBookCard(book,model,!settings.grid,if(selecting)book.id in selected else null,{if(selecting)choose(book.id)else open(book.id)},{choose(book.id)},{detailId=book.id}) }
            }
        }
    }
    books.firstOrNull { it.id==detailId }?.let { book->BookDetails(book,model,{detailId=null},{detailId=null;open(book.id)}) }
    if(scanner) DeviceBooksSheet(model,{scanner=false}) { ids->scanner=false;shelfId=null;tab=0;search="";status="All books";sort="Recently added";selecting=true;selected=ids }
    if(deleteChoice) AlertDialog(onDismissRequest={deleteChoice=false},title={Text("Hapus ${selected.size} buku")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        if(selectedShelf!=null) OutlinedButton({deleteChoice=false;deleteScope="shelf"},Modifier.fillMaxWidth()) {Text("Keluarkan dari rak ${selectedShelf.name}")}
        OutlinedButton({deleteChoice=false;deleteScope="library"},Modifier.fillMaxWidth(),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("Hapus dari pustaka aplikasi")}
    }},confirmButton={TextButton({deleteChoice=false}){Text("Batal")}})
    deleteScope?.let { scope -> Confirm(if(scope=="shelf")"Keluarkan dari rak?"else"Hapus dari pustaka?",if(scope=="shelf")"Buku tetap tersedia di pustaka dan rak lain."else"Salinan buku, penanda, dan catatan dalam aplikasi dihapus. Berkas asli dan statistik tetap tersimpan.","Hapus",{deleteScope=null}) {
        val ids=selected.toSet();val target=shelfId;deleteScope=null
        model.task("Menghapus pilihan…") {
            if(scope=="shelf"&&target!=null)model.repo.removeFromShelf(ids,target)else model.repo.deleteBooks(ids)
            Widgets.update(model.app)
            withContext(Dispatchers.Main){selecting=false;selected=emptyList()}
        }
    } }
    if(assign) ShelfAssignment(model,selected.toSet(),{assign=false},{assign=false;selecting=false;selected=emptyList()})
    if(newShelf||editingShelf!=null) {
        var name by remember(editingShelf) { mutableStateOf(editingShelf?.name?:"") }
        AlertDialog(onDismissRequest={newShelf=false;editingShelf=null},title={Text(if(newShelf)"Buat rak"else"Ubah rak")},text={Column {
            OutlinedTextField(name,{name=it},label={Text("Nama rak")},singleLine=true)
            if(editingShelf!=null)TextButton({deleteShelf=true}){Text("Hapus rak",color=MaterialTheme.colorScheme.error)}
        }},confirmButton={TextButton({val shelf=Shelf(editingShelf?.id?:UUID.randomUUID().toString(),name.trim(),editingShelf?.icon?:"Library");model.task{model.repo.dao.putShelf(shelf)};newShelf=false;editingShelf=null},enabled=name.isNotBlank()&&name.length<=80){Text("Simpan")}},dismissButton={TextButton({newShelf=false;editingShelf=null}){Text("Batal")}})
    }
    if(deleteShelf) Confirm("Hapus rak ini?","Buku tetap tersimpan di pustaka.","Hapus",{deleteShelf=false}){val id=editingShelf?.id;if(id!=null)model.task{model.repo.dao.deleteShelf(id)};editingShelf=null;shelfId=null;deleteShelf=false}
}

@Composable private fun LibraryResume(book:Book,model:AppState,open:()->Unit) {
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
            BookCover(book,model,Modifier.width(80.dp).height(118.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text("LANJUT MEMBACA",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
                Text(book.title,style=MaterialTheme.typography.titleLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
                LinearProgressIndicator({book.progress},Modifier.fillMaxWidth().height(5.dp))
                TextButton(open,contentPadding=PaddingValues(0.dp)){Text("Lanjutkan · ${(book.progress*100).toInt()}%");Icon(Icons.Outlined.ChevronRight,null)}
            }
        }
    }
}

@Composable private fun ShelfCard(shelf:Shelf,books:List<Book>,model:AppState,open:()->Unit,edit:()->Unit) {
    val progress=if(books.isEmpty())0f else books.sumOf{it.progress.toDouble()}.toFloat()/books.size
    Surface(onClick=open,shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp,modifier=Modifier.fillMaxWidth().testTag("shelf-${shelf.id}")) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) { Column(Modifier.weight(1f)){Text(shelf.name,style=MaterialTheme.typography.titleLarge);Text("${books.size} buku · ${books.count{it.status=="Finished"}} selesai",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};IconButton(edit){Icon(Icons.Outlined.MoreHoriz,"Ubah rak ${shelf.name}")} }
            BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(14.dp)).padding(top=14.dp,start=10.dp,end=10.dp,bottom=8.dp)) {
                val slots=((maxWidth.value+8)/66).toInt().coerceIn(1,6)
                Column {
                    Row(Modifier.fillMaxWidth().height(100.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.Bottom) {
                        if(books.isEmpty()) Box(Modifier.fillMaxWidth().height(90.dp),contentAlignment=Alignment.Center){Text("Rak ini belum berisi buku",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onPrimaryContainer)}
                        books.take(slots).forEach { book-> BookCover(book,model,Modifier.width(58.dp).height(88.dp)) }
                    }
                    Box(Modifier.fillMaxWidth().height(7.dp).background(MaterialTheme.colorScheme.primary,RoundedCornerShape(4.dp)))
                    if(books.size>slots) Text("+${books.size-slots} buku lainnya",Modifier.padding(top=6.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("Rata-rata bacaan",style=MaterialTheme.typography.bodySmall);Text("${(progress*100).toInt()}%",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)}
            LinearProgressIndicator({progress},Modifier.fillMaxWidth().height(6.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun LibraryBookCard(book:Book,model:AppState,list:Boolean,checked:Boolean?,open:()->Unit,choose:()->Unit,details:()->Unit) {
    val interaction=Modifier.combinedClickable(onClick=open,onLongClick=choose,onLongClickLabel="Pilih buku").testTag("library-book-${book.id}").semantics { if(checked!=null){selected=checked;stateDescription=if(checked)"Dipilih"else"Belum dipilih"} }
    Surface(modifier=interaction,shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface,border=if(checked==true)BorderStroke(2.dp,MaterialTheme.colorScheme.primary)else null) {
        val cover: @Composable (Modifier) -> Unit={ modifier-> Box(modifier) {
            BookCover(book,model,Modifier.fillMaxSize())
            Surface(Modifier.align(Alignment.BottomStart),color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(topEnd=8.dp,bottomStart=8.dp)){Text(book.format,Modifier.padding(horizontal=10.dp,vertical=3.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onPrimary)}
            if(checked!=null) Surface(Modifier.align(Alignment.TopEnd).padding(5.dp),shape=CircleShape,color=MaterialTheme.colorScheme.surface){Icon(if(checked)Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary)}
        } }
        if(list) Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically) {
            cover(Modifier.width(78.dp).height(114.dp))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) { Text(book.title,style=MaterialTheme.typography.titleMedium,maxLines=3,overflow=TextOverflow.Ellipsis);Text(book.author.labelId(),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis);LinearProgressIndicator({book.progress},Modifier.fillMaxWidth());Text("${book.status.labelId()} · ${(book.progress*100).toInt()}%",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary) }
            if(checked==null)IconButton(details,Modifier.size(36.dp)){Icon(Icons.Outlined.MoreVert,"Rincian ${book.title}")}
        } else Column(Modifier.padding(10.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            cover(Modifier.fillMaxWidth().aspectRatio(.69f))
            Row(verticalAlignment=Alignment.CenterVertically){Text(book.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);if(checked==null)IconButton(details,Modifier.size(32.dp)){Icon(Icons.Outlined.MoreVert,"Rincian ${book.title}")}}
            Text(book.author.labelId(),maxLines=1,style=MaterialTheme.typography.bodySmall,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator({book.progress},Modifier.fillMaxWidth().height(4.dp))
            Text("${book.status.labelId()} · ${(book.progress*100).toInt()}%",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun ShelfAssignment(model:AppState,selected:Set<String>,dismiss:()->Unit,done:()->Unit) {
    val shelves by model.shelves.collectAsStateWithLifecycle()
    var target by remember { mutableStateOf<String?>(null) };var name by remember { mutableStateOf("") };var creating by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest=dismiss,title={Text("Masukkan ${selected.size} buku ke rak")},text={Column(Modifier.heightIn(max=330.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Rak lain dan posisi baca tetap tersimpan.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f,fill=false).heightIn(max=210.dp).selectableGroup()) {
            items(shelves,key={it.id}) { shelf->Row(Modifier.fillMaxWidth().heightIn(min=54.dp).selectable(target==shelf.id,role=Role.RadioButton){target=shelf.id;name="";creating=false},verticalAlignment=Alignment.CenterVertically){RadioButton(target==shelf.id,null);Text(shelf.name,style=MaterialTheme.typography.titleMedium)} }
        }
        if(creating) OutlinedTextField(name,{name=it},label={Text("Nama rak baru")},singleLine=true,modifier=Modifier.fillMaxWidth())
        else TextButton({creating=true;target=null}) { Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(6.dp));Text("Buat rak baru") }
    }},confirmButton={TextButton({ val ids=selected;val existing=target;val newName=name.trim()
        model.task("Menyusun rak buku…") {
            val id=existing?:UUID.randomUUID().toString()
            model.repo.database.withTransaction {
                if(existing==null)model.repo.dao.putShelf(Shelf(id,newName))
                model.repo.addToShelf(ids,id)
            }
            withContext(Dispatchers.Main){done()};model.messages.emit("${ids.size} buku dimasukkan ke rak.")
        }
    },enabled=target!=null||(creating&&name.isNotBlank()&&name.length<=80)){Text("Simpan")}},dismissButton={TextButton(dismiss){Text("Batal")}})
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BookDetails(book: Book, model: AppState, dismiss: () -> Unit, open: () -> Unit) {
    val shelves by model.shelves.collectAsStateWithLifecycle(); val links by model.memberships.collectAsStateWithLifecycle(); val annotations by model.annotations.collectAsStateWithLifecycle()
    var remove by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(book.title, style = MaterialTheme.typography.headlineMedium); Text(book.author.labelId(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${book.format} · ${(book.progress * 100).toInt()}% · ${if (book.opened > 0) "Dibuka ${dateLabel(book.opened)}" else "Belum dibuka"}")
            Button(open, Modifier.fillMaxWidth()) { Text("Buka buku") }
            Text("Status baca", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("To read", "Reading", "Finished").forEach { status -> FilterChip(book.status == status, { model.task { model.repo.status(book.id, status); Widgets.update(model.app) } }, label = { Text(status.labelId()) }) } }
            Text("Rak buku", style = MaterialTheme.typography.titleMedium)
            if (shelves.isEmpty()) Text("Buat rak dari tombol tambah rak di Pustaka.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            shelves.forEach { shelf -> val checked = links.any { it.bookId == book.id && it.shelfId == shelf.id }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(checked, { selected -> model.task { if (selected) model.repo.dao.putMembership(BookShelf(book.id, shelf.id)) else model.repo.dao.unlink(book.id, shelf.id) } }); Text(shelf.name) }
            }
            val notes = annotations.filter { it.bookId == book.id }
            Text("${notes.count { it.kind == "Bookmark" }} penanda · ${notes.count { it.kind == "Highlight" }} sorotan · ${notes.count { it.kind == "Note" }} catatan", style = MaterialTheme.typography.bodySmall)
            OutlinedButton({ remove = true },Modifier.fillMaxWidth().heightIn(min=52.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)) { Icon(Icons.Outlined.DeleteOutline, null);Spacer(Modifier.width(8.dp));Text("Hapus dari pustaka") }
            Spacer(Modifier.height(28.dp))
        }
    }
    if (remove) Confirm("Hapus buku ini?", "Salinan buku, penanda, dan catatan di Spotibook akan dihapus. Berkas asli dan riwayat membaca tetap disimpan.", "Hapus", { remove = false }) { remove = false; dismiss(); model.task("Menghapus buku…") { model.repo.deleteBook(book); Widgets.update(model.app) } }
}
