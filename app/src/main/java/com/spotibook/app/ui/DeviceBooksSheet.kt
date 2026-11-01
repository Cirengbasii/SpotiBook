package com.spotibook.app.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.background.Widgets
import com.spotibook.app.reader.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun DeviceBooksSheet(model: AppState, dismiss: () -> Unit, imported: (List<String>) -> Unit) {
    val books by model.books.collectAsStateWithLifecycle()
    val context = model.app
    val scope = rememberCoroutineScope()
    var permitted by remember { mutableStateOf(DeviceBooks.allowed(context)) }
    var results by remember { mutableStateOf<List<DeviceBook>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var found by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun scan() {
        permitted = DeviceBooks.allowed(context)
        if(!permitted) return
        job?.cancel(); scanning = true; failure = null; found = 0; selected = emptySet()
        job = scope.launch {
            try { results = DeviceBooks.scan(DeviceBooks.roots(context)) { found = it } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = "Pemindaian belum berhasil. Periksa izin berkas lalu coba lagi." }
            finally { scanning = false }
        }
    }
    val access = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { permitted = DeviceBooks.allowed(context); if(permitted) scan() }
    val legacyAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it; if(it) scan() }
    fun permission() {
        if (Build.VERSION.SDK_INT >= 30) {
            try { access.launch(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))) }
            catch (_: android.content.ActivityNotFoundException) { access.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else legacyAccess.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    LaunchedEffect(Unit) { if (permitted) scan() }
    DisposableEffect(Unit) { onDispose { job?.cancel() } }
    val sources = remember(books) { books.map { it.sourceUri }.toSet() }
    val available = results.filter { Uri.fromFile(it.file).toString() !in sources }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = (LocalConfiguration.current.screenHeightDp * .87f).dp).padding(horizontal = 20.dp)) {
            Text("Buku di perangkat", style = MaterialTheme.typography.headlineMedium)
            Text("Temukan PDF dan EPUB tanpa mencari satu per satu.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            if (!permitted) {
                Text("Izinkan akses berkas untuk memindai penyimpanan bersama, termasuk Unduhan. Berkas asli tidak diubah.", style = MaterialTheme.typography.bodyMedium)
                Button(::permission, Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 54.dp)) { Text("Izinkan pemindaian") }
            } else if (scanning) {
                Row(Modifier.padding(vertical = 28.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(28.dp)); Text("Memindai… $found buku ditemukan") }
                TextButton({ job?.cancel(); dismiss() }) { Text("Batal") }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${results.size} ditemukan · ${selected.size} dipilih", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    TextButton({ selected = if(selected.size == available.size) emptySet() else available.map { it.file.absolutePath }.toSet() }, enabled = available.isNotEmpty()) { Text(if(selected.size == available.size && selected.isNotEmpty()) "Batal semua" else "Pilih semua") }
                }
                if (failure != null) Text(failure!!, color = MaterialTheme.colorScheme.error)
                if(results.isEmpty()) Text("Belum menemukan PDF atau EPUB di penyimpanan yang dapat dibaca.", Modifier.padding(vertical = 16.dp))
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(min = 40.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(results, key = { it.file.absolutePath }) { book ->
                        val already = Uri.fromFile(book.file).toString() in sources
                        ListItem(headlineContent = { Text(book.name, maxLines = 2) }, supportingContent = { Text("${book.format} · ${android.text.format.Formatter.formatShortFileSize(context,book.size)} · ${if(already) "Sudah di pustaka" else book.file.parentFile?.name.orEmpty()}", maxLines = 2) },
                            leadingContent = { Icon(Icons.Outlined.Description, null, tint = MaterialTheme.colorScheme.primary) }, trailingContent = { Checkbox(already || book.file.absolutePath in selected, null, enabled = !already) },
                            modifier = Modifier.clickable(enabled = !already) { selected = if(book.file.absolutePath in selected) selected - book.file.absolutePath else selected + book.file.absolutePath }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
                    }
                }
                Button({
                    val batch = available.filter { it.file.absolutePath in selected }
                    model.task("Mengimpor ${batch.size} buku…") {
                        val ids = mutableListOf<String>(); var failed = 0
                        model.repo.dataGate.withLock {
                            for(book in batch) {
                                currentCoroutineContext().ensureActive()
                                try { ids += model.repo.documents.import(Uri.fromFile(book.file)).id }
                                catch(e: CancellationException) { throw e }
                                catch(_: Exception) { failed++ }
                            }
                        }
                        Widgets.update(context)
                        withContext(Dispatchers.Main) { if(ids.isNotEmpty()) imported(ids) }
                        model.messages.emit("${ids.size} buku diimpor${if(failed>0) "; $failed berkas sudah ada, tidak dapat dibaca, atau belum didukung" else ""}.")
                    }
                }, Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 54.dp), enabled = selected.isNotEmpty()) { Text("Impor ${selected.size} buku") }
                TextButton(::scan, Modifier.align(Alignment.CenterHorizontally)) { Icon(Icons.Outlined.Refresh, null); Spacer(Modifier.width(6.dp)); Text("Pindai ulang") }
            }
            Spacer(Modifier.navigationBarsPadding().height(12.dp))
        }
    }
}
