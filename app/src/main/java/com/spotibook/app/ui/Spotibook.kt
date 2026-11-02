package com.spotibook.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.spotibook.app.background.*
import com.spotibook.app.data.*
import com.spotibook.app.reader.ReaderScreen
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@Composable fun Spotibook(model: AppState, destination: Pair<String, Long>?) {
    val nav = rememberNavController(); val stack by nav.currentBackStackEntryAsState(); val route = stack?.destination?.route ?: "home"
    val snackbar = remember { SnackbarHostState() }; val busy by model.busy.collectAsStateWithLifecycle()
    val backup = remember { Backup(model.repo) }; var includeBooks by rememberSaveable { mutableStateOf(false) }
    var exportChoice by remember { mutableStateOf(false) }; var restore by remember { mutableStateOf<Pair<android.net.Uri, BackupData>?>(null) }
    var restorePrefs by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) model.task("Mengimpor buku…") {
            var imported = 0; val failures = mutableListOf<String>()
            model.repo.dataGate.withLock { uris.forEach { uri -> try { model.repo.documents.import(uri); imported++ } catch (e: Exception) { failures += e.message ?: "Berkas tidak dapat diimpor" } } }
            Widgets.update(model.app)
            if(imported>0) kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { nav.navigate("library") { launchSingleTop=true } }
            model.messages.emit(if (failures.isEmpty()) "$imported buku berhasil diimpor" else "$imported berhasil diimpor. ${failures.distinct().joinToString(" · ")}")
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> if (uri != null) model.task("Membuat cadangan…") { backup.export(uri, includeBooks); model.messages.emit("Cadangan disimpan") } }
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) model.task("Mengekspor riwayat…") { backup.exportCsv(uri); model.messages.emit("Riwayat dan statistik diekspor") } }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) model.task("Memeriksa cadangan…") { val data = backup.inspect(uri); kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { restore = uri to data; restorePrefs = false } } }
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if (!allowed) model.task { model.messages.emit("Notifikasi belum diizinkan. Aktifkan dari pengaturan Android jika diperlukan.") } }
    fun permission() { if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
    fun open(id: String) { nav.navigate("reader/$id") { launchSingleTop = true } }
    fun importBooks() { import.launch(arrayOf("application/pdf", "application/epub+zip", "text/plain", "text/html", "application/xhtml+xml", "application/octet-stream")) }
    LaunchedEffect(Unit) { model.messages.collect { snackbar.showSnackbar(it, duration = SnackbarDuration.Long) } }
    LaunchedEffect(destination) { destination?.first?.let { target ->
        if (target in listOf("home", "library", "focus", "statistics", "settings") || target.matches(Regex("reader/[0-9a-fA-F-]{36}")) || (target.startsWith("calendar?day=") && runCatching { LocalDate.parse(target.removePrefix("calendar?day=")) }.isSuccess)) nav.navigate(target) { launchSingleTop = true }
    } }
    val reader = route.startsWith("reader")
    val speech by com.spotibook.app.speech.SpeechService.state.collectAsStateWithLifecycle()
    var speechPanel by remember { mutableStateOf(false) }
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    val destinations = listOf(Triple("home","Beranda",Icons.Outlined.Home),Triple("library","Pustaka",Icons.Outlined.AutoStories),Triple("focus","Fokus",Icons.Outlined.Timelapse),Triple("statistics","Statistik",Icons.Outlined.BarChart),Triple("settings","Profil",Icons.Outlined.PersonOutline))
    fun select(path: String) { nav.navigate(path) { popUpTo(nav.graph.startDestinationId) { saveState=true }; launchSingleTop=true; restoreState=path!="home" } }
    Box(Modifier.fillMaxSize()) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets=if(reader) WindowInsets(0,0,0,0) else WindowInsets.safeDrawing,
        bottomBar = { if (!reader && !wide) SpotibookBottomBar(destinations, route, ::select) }

    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding).padding(bottom=if(speech.bookId.isNotEmpty()&&!reader)80.dp else 0.dp)) {
        if(wide&&!reader) NavigationRail(containerColor=MaterialTheme.colorScheme.background) { destinations.forEach { (path,label,icon)->NavigationRailItem(route==path,{select(path)},icon={Icon(icon,label)},label={Text(label)}) } }
        NavHost(nav, startDestination = "home", modifier = Modifier.weight(1f).fillMaxHeight()) {
            composable("home") { Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Box(Modifier.widthIn(max=840.dp).fillMaxHeight()){HomeScreen(model, ::open, ::importBooks, { target -> if(destinations.any{it.first==target})select(target)else nav.navigate(target) })}} }
            composable("library") { LibraryScreen(model, ::open, ::importBooks) }
            composable("focus") { FocusScreen(model, ::open, ::permission) }
            composable("statistics") { StatisticsScreen(model, { nav.navigate(it) }) }
            composable("settings") { SettingsScreen(model, ::permission, { exportChoice = true }, { restorePicker.launch(arrayOf("application/zip", "application/octet-stream")) }, { csv.launch("spotibook-riwayat-${LocalDate.now()}.csv") }) }
            composable("history") { HistoryScreen(model, { nav.popBackStack() }, ::open) }
            composable("calendar?day={day}", arguments = listOf(navArgument("day") { defaultValue = "" })) { CalendarScreen(model, { nav.popBackStack() }, ::open, it.arguments?.getString("day").orEmpty()) }
            composable("reader/{bookId}") { entry -> ReaderScreen(model, entry.arguments?.getString("bookId").orEmpty(), { nav.popBackStack() }) }
        }
        }
    }
    FocusCompletionBanner(model)
    if(speech.bookId.isNotEmpty()&&!reader)Surface(onClick={speechPanel=true},modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=110.dp,start=16.dp,end=16.dp).widthIn(max=560.dp).fillMaxWidth(),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.primaryContainer,shadowElevation=4.dp){
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Outlined.Headphones,null);Text(speech.title.ifBlank{"Dengarkan buku"},Modifier.weight(1f).padding(horizontal=10.dp),maxLines=1);IconButton({com.spotibook.app.speech.SpeechService.command(model.app,if(speech.playing)"PAUSE"else"PLAY")}){Icon(if(speech.playing)Icons.Outlined.Pause else Icons.Outlined.PlayArrow,if(speech.playing)"Jeda suara"else"Lanjutkan suara")}}
    }
    }
    if(speechPanel)SpeechSheet(model,speech.bookId,speech.page){speechPanel=false}
    if (busy != null) AlertDialog(onDismissRequest = {}, title = { Text("Spotibook") }, text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { CircularProgressIndicator(Modifier.size(24.dp)); Text(busy!!) } }, confirmButton = {})
    if (exportChoice) AlertDialog(onDismissRequest = { exportChoice = false }, title = { Text("Cadangkan pustaka") }, text = { Column {
        Text("Cadangan ZIP berisi pustaka, riwayat, koleksi, catatan, dan pengaturan. Sertakan berkas buku agar bisa dibaca setelah dipulihkan di perangkat lain.")
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(includeBooks, { includeBooks = it }); Text("Sertakan berkas buku") }
    } }, confirmButton = { TextButton({ exportChoice = false; export.launch("spotibook-cadangan-${LocalDate.now()}.zip") }) { Text("Pilih lokasi") } }, dismissButton = { TextButton({ exportChoice = false }) { Text("Batal") } })
    restore?.let { (uri, data) -> AlertDialog(onDismissRequest = { restore = null }, title = { Text("Pulihkan cadangan ini?") }, text = { Column {
        Text("${data.books.size} buku · ${data.sessions.size} sesi. Data digabungkan; buku dan riwayat yang ada tetap disimpan.${if (!data.includesBooks) " Berkas buku tidak disertakan. Impor kembali berkas aslinya untuk menyambungkannya." else ""}")
        Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(restorePrefs, { restorePrefs = it }); Text("Gunakan pengaturan dari cadangan") }
    } }, confirmButton = { TextButton({ restore = null; model.task("Memulihkan pustaka…") { val count = backup.restore(uri, restorePrefs); Reminders.schedule(model.app, model.repo.preferences.settings.first()); Widgets.update(model.app); model.messages.emit("$count buku dipulihkan") } }) { Text("Pulihkan") } }, dismissButton = { TextButton({ restore = null }) { Text("Batal") } }) }
}
