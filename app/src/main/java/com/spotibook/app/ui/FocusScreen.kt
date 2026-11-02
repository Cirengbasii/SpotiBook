package com.spotibook.app.ui

import androidx.compose.foundation.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.*
import com.spotibook.app.background.FocusAudio
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

@Composable fun FocusScreen(model: AppState, open: (String) -> Unit, permission: () -> Unit) {
    FocusContent(model,open,permission,null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ReaderFocusSheet(model:AppState,bookId:String,dismiss:()->Unit){
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){}
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Box(Modifier.fillMaxWidth().height((LocalConfiguration.current.screenHeightDp*.86f).dp)){
            FocusContent(model,{dismiss()},{if(Build.VERSION.SDK_INT>=33)permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)},bookId)
            IconButton(dismiss,Modifier.align(Alignment.TopEnd).padding(end=8.dp).background(MaterialTheme.colorScheme.surface,CircleShape)) { Icon(Icons.Outlined.Close,"Tutup Fokus") }
        }
    }
}

@Composable private fun FocusContent(model: AppState, open: (String) -> Unit, permission: () -> Unit,initialBook:String?) {
    val state by model.focus.collectAsStateWithLifecycle(); val books by model.books.collectAsStateWithLifecycle(); val sessions by model.sessions.collectAsStateWithLifecycle()
    var duration by rememberSaveable { mutableIntStateOf(25) }; var custom by remember { mutableStateOf(false) }; var bookId by rememberSaveable { mutableStateOf<String?>(initialBook) }; var picker by remember { mutableStateOf(false) }
    var left by remember { mutableLongStateOf(0) }
    LaunchedEffect(state) { while (true) { left = model.app.focus.remaining(state); kotlinx.coroutines.delay(500) } }
    val selected = books.firstOrNull { it.id == (if (state.id.isEmpty()) bookId else state.bookId) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PageTitle("Pengatur waktu", "Fokus membaca", if (state.done) "Sesi fokus selesai." else "Pilih durasi dan buku yang ingin dibaca.")
        Box(Modifier.size(240.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).padding(15.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(progress = { if (state.plannedMillis > 0) (1f - left.toFloat() / state.plannedMillis).coerceIn(0f, 1f) else 0f }, Modifier.fillMaxSize(), strokeWidth = 5.dp, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeCap = StrokeCap.Round)
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (state.id.isEmpty()) "$duration:00" else String.format(Locale.ROOT, "%02d:%02d", left / 60_000, left / 1000 % 60), style = MaterialTheme.typography.displaySmall); Text(if (state.done) "Selesai" else if (state.running) "Sedang berjalan" else "Siap dimulai", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (selected != null) Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BookCover(selected, model, Modifier.width(62.dp).height(90.dp)); Column(Modifier.weight(1f)) { Text(selected.title, style = MaterialTheme.typography.titleMedium); Text(selected.author.labelId(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (state.id.isEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(10, 15, 25, 30, 45, 60).forEach { m -> FilterChip(duration == m, { duration = m }, label = { Text("$m") }) }; FilterChip(duration !in listOf(10,15,25,30,45,60), { custom = true }, label = { Text("Lainnya") }) }
            Box { OutlinedButton({ picker = true }) { Text(selected?.title ?: "Pilih buku (opsional)") }; DropdownMenu(picker, { picker = false }, Modifier.heightIn(max = 320.dp)) { DropdownMenuItem(text = { Text("Tanpa buku") }, onClick = { bookId = null; picker = false }); books.forEach { b -> DropdownMenuItem(text = { Text(b.title) }, onClick = { bookId = b.id; picker = false }) } } }
            Button({ if(model.settings.value.focusNotification)permission(); model.task { model.app.focus.start(duration, bookId) } }, Modifier.fillMaxWidth().heightIn(min = 60.dp)) { Text("Mulai fokus") }
        } else if (state.done) {
            Text("Pengatur waktu ${state.plannedMillis / 60_000} menit selesai", style = MaterialTheme.typography.titleLarge)
            val actual = sessions.filter { it.focusId == state.id || (it.focusId.isEmpty() && it.bookId == state.bookId && it.start >= state.started && it.end <= state.finished) }
            Text("${minutes(actual.sumOf { it.millis })} membaca aktif · ${actual.sumOf { it.pages }} halaman PDF maju", color = MaterialTheme.colorScheme.primary)
            Text("Target membaca dihitung dari waktu saat buku terbuka dan aktif, bukan dari pengatur waktu.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button({ model.task { model.app.focus.stop() } }) { Text("Sesi baru") }
        } else {
            Text(selected?.title ?: "Sesi fokus", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button({ model.task { if (state.running) model.app.focus.pause() else model.app.focus.resume() } }) { Text(if (state.running) "Jeda" else "Lanjutkan") }; OutlinedButton({ model.task { model.app.focus.stop() } }) { Text("Akhiri") } }
            if (selected != null) Button({ open(selected.id) }, Modifier.fillMaxWidth()) { Text(if(initialBook!=null)"Kembali membaca"else"Buka buku") }
        }
        FocusSoundSettings(model)
        Text("Pengatur waktu tetap berjalan di latar belakang. Pencatatan membaca berhenti saat pembaca ditutup, layar terkunci, atau tidak disentuh selama lima menit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (custom) TextEntry("Durasi fokus (menit)", duration.toString(), true, 1..240, { custom = false }) { duration = it.toInt() }
}

@Composable private fun FocusSoundSettings(model:AppState){
    val prefs by model.settings.collectAsStateWithLifecycle()
    var exact by remember{mutableStateOf(model.app.focus.exactAllowed())}
    val alarmPermission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {exact=model.app.focus.exactAllowed();model.task{model.app.focus.recover()}}
    val audio=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)model.task("Menyimpan bunyi Fokus…"){
        val name=FocusAudio.import(model.app,uri);model.repo.preferences.update{it.copy(focusSound="Custom",focusSoundName=name)}
    }}
    Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surface){Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("Saat Fokus selesai",style=MaterialTheme.typography.titleLarge)
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(prefs.focusNotification,{checked->model.preferences{it.copy(focusNotification=checked)}});Text("Tampilkan notifikasi selesai",Modifier.weight(1f))}
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            listOf("Gentle" to "Nada lembut","Custom" to "MP3 sendiri","Silent" to "Senyap").forEach{(key,label)->FilterChip(prefs.focusSound==key,{if(key=="Custom")audio.launch(arrayOf("audio/mpeg","audio/*"))else model.preferences{it.copy(focusSound=key)}},label={Text(label)})}
        }
        if(prefs.focusSound=="Custom")Text(prefs.focusSoundName.ifBlank{"Bunyi pilihan"},style=MaterialTheme.typography.bodySmall,maxLines=2)
        Text("Volume bunyi",style=MaterialTheme.typography.titleMedium)
        Slider(prefs.focusVolume,{value->model.preferences{it.copy(focusVolume=value)}},valueRange=0f..1f,enabled=prefs.focusSound!="Silent",modifier=Modifier.semantics{contentDescription="Volume bunyi Fokus"})
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            TextButton({model.task{FocusAudio.play(model.app,prefs)}},enabled=prefs.focusSound!="Silent"){Text("Coba bunyi")}
            TextButton({model.task{FocusAudio.stop()}}){Text("Hentikan bunyi")}
        }
        Text("Bunyi memakai volume alarm HP, diputar sekali, maksimal 8 detik. Mode senyap dan Jangan Ganggu tetap dihormati.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(Build.VERSION.SDK_INT>=31&&!exact){
            OutlinedButton({alarmPermission.launch(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${model.app.packageName}")))},Modifier.fillMaxWidth()){Text("Izinkan alarm tepat waktu")}
            Text("Agar penanda selesai tetap tepat saat layar mati.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }}
}
