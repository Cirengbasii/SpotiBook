package com.spotibook.app.reader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.spotibook.app.data.*
import com.spotibook.app.data.Annotation
import com.spotibook.app.ui.*

enum class ReaderPanelPage(val title: String) {
    Settings("Pengaturan baca"), Contents("Isi buku"), Modes("Perpindahan halaman"),
    Orientation("Orientasi layar"), Brightness("Kecerahan"), Notes("Penanda & catatan")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderPanel(
    initial: ReaderPanelPage, book: Book, page: Int, settings: Settings, publication: Publication?,
    annotations: List<Annotation>, update: ((Settings) -> Settings) -> Unit,
    previewBrightness: (Float) -> Unit, navigate: (Int, Float, String?) -> Unit,
    addNote: () -> Unit, remove: (Annotation) -> Unit, search: () -> Unit,
    finish: () -> Unit, dismiss: () -> Unit
) {
    var panel by remember { mutableStateOf(initial) }
    val isPdf = book.format == "PDF"
    ModalBottomSheet(onDismissRequest = dismiss, sheetMaxWidth = 720.dp, containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * .78f).dp
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (panel == ReaderPanelPage.Settings || panel == ReaderPanelPage.Contents) {
                TabRow(if (panel == ReaderPanelPage.Contents) 0 else 1, containerColor = Color.Transparent) {
                    Tab(panel == ReaderPanelPage.Contents, { panel = ReaderPanelPage.Contents }, text = { Text("Isi buku") })
                    Tab(panel == ReaderPanelPage.Settings, { panel = ReaderPanelPage.Settings }, text = { Text("Pengaturan") })
                }
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton({ panel = ReaderPanelPage.Settings }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Kembali ke pengaturan") }
                Text(panel.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                IconButton(dismiss) { Icon(Icons.Outlined.Close, "Tutup panel") }
            }
            when (panel) {
                ReaderPanelPage.Settings -> {
                    ThemeSwatches(settings.readerTheme) { value -> update { it.copy(readerTheme = value) } }
                    if (isPdf) Text("PDF mempertahankan tata letak buku. Tema hanya mengubah warna tampilan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BoxWithConstraints {
                        val columns = if (maxWidth < 400.dp) 2 else 4
                        val tools = listOf(
                            Triple(Icons.Outlined.ScreenRotation, "Orientasi", settings.readerOrientation.title),
                            Triple(Icons.Outlined.Swipe, "Ganti halaman", settings.pageTurn.title),
                            Triple(Icons.Outlined.LightMode, "Kecerahan", if (settings.brightness < 0) "Ikuti sistem" else "${(settings.brightness*100).toInt()}%"),
                            Triple(Icons.Outlined.VolumeUp, "Tombol volume", if (settings.volumeKeys) "Aktif" else "Nonaktif")
                        ).filterIndexed { index, _ -> isPdf || index != 1 }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            tools.chunked(columns).forEachIndexed { row, group -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                group.forEachIndexed { col, (icon, title, value) ->
                                    ToolTile(icon, title, value, Modifier.weight(1f)) {
                                        when (title) {
                                            "Orientasi" -> panel = ReaderPanelPage.Orientation
                                            "Ganti halaman" -> panel = ReaderPanelPage.Modes
                                            "Kecerahan" -> panel = ReaderPanelPage.Brightness
                                            else -> update { it.copy(volumeKeys = !it.volumeKeys) }
                                        }
                                    }
                                }
                                repeat(columns-group.size) { Spacer(Modifier.weight(1f)) }
                            } }
                        }
                    }
                    HorizontalDivider()
                    BrightnessControl(settings, update, previewBrightness)
                    if (isPdf) {
                        Text("Ukuran halaman potret", style = MaterialTheme.typography.titleMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PdfFit.entries.forEach { fit -> FilterChip(settings.pdfFit == fit, { update { it.copy(pdfFit = fit) } }, label = { Text(fit.title) }) } }
                        Text("Tepi halaman · ${settings.pdfMargin}", style = MaterialTheme.typography.bodySmall)
                        PreferenceSlider(settings.pdfMargin.toFloat(), 0f..32f, "Tepi halaman PDF") { value -> update { it.copy(pdfMargin = value.toInt()) } }
                    } else {
                        Choices("Huruf", listOf("Serif", "Sans", "Mono"), settings.fontFamily) { value -> update { it.copy(fontFamily = value) } }
                        Text("Ukuran huruf · ${settings.fontSize}")
                        PreferenceSlider(settings.fontSize.toFloat(), 12f..36f, "Ukuran huruf") { value -> update { it.copy(fontSize = value.toInt()) } }
                        Text("Jarak baris · ${"%.1f".format(settings.lineSpacing)}")
                        PreferenceSlider(settings.lineSpacing, 1f..2.5f, "Jarak baris") { value -> update { it.copy(lineSpacing = value) } }
                        Text("Tepi teks · ${settings.margin}")
                        PreferenceSlider(settings.margin.toFloat(), 4f..48f, "Tepi teks") { value -> update { it.copy(margin = value.toInt()) } }
                    }
                    PreferenceSwitch("Layar tetap menyala", "Selama pembaca terbuka.", settings.keepScreenOn) { value -> update { it.copy(keepScreenOn = value) } }
                    PreferenceSwitch("Tombol volume untuk halaman", "Turun: berikutnya. Naik: sebelumnya.", settings.volumeKeys) { value -> update { it.copy(volumeKeys = value) } }
                    if (isPdf) PreferenceSwitch("Ketuk tepi untuk pindah", "Kiri untuk kembali, kanan untuk lanjut. Ketuk tengah untuk menu.", settings.tapToTurn) { value -> update { it.copy(tapToTurn = value) } }
                }
                ReaderPanelPage.Modes -> {
                    Text("Pilih cara membuka halaman PDF.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PageModeChoices(settings.pageTurn) { mode -> update { it.copy(pageTurn = mode) } }
                    Text("Cubitan memperbesar halaman. Saat diperbesar, geser untuk melihat bagian halaman; perkecil untuk berganti halaman.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ReaderPanelPage.Orientation -> {
                    ReaderOrientation.entries.forEach { orientation ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).selectable(settings.readerOrientation == orientation, role = Role.RadioButton, onClick = { update { it.copy(readerOrientation = orientation) } }).padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(when(orientation) { ReaderOrientation.Auto -> Icons.Outlined.ScreenRotation; ReaderOrientation.Portrait -> Icons.Outlined.StayCurrentPortrait; ReaderOrientation.Landscape -> Icons.Outlined.StayCurrentLandscape }, null)
                            Column(Modifier.weight(1f)) { Text(orientation.title); Text(when(orientation) { ReaderOrientation.Auto -> "Mengikuti arah kemiringan ponsel"; ReaderOrientation.Portrait -> "Halaman tegak"; ReaderOrientation.Landscape -> "Halaman melebar" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            RadioButton(settings.readerOrientation == orientation, null)
                        }
                    }
                }
                ReaderPanelPage.Brightness -> {
                    BrightnessControl(settings, update, previewBrightness)
                    Text("Pengaturan ini hanya berlaku saat membaca. Kecerahan sistem tetap seperti semula.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                ReaderPanelPage.Contents -> {
                    Text(book.title, style = MaterialTheme.typography.titleLarge)
                    Text("${page+1} dari ${book.total} ${if (isPdf) "halaman" else "bab"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var target by remember(page) { mutableFloatStateOf(page.toFloat()) }
                    Slider(target, { target = it }, enabled = book.total > 1, valueRange = 0f..(book.total-1).coerceAtLeast(1).toFloat(), onValueChangeFinished = { navigate(target.toInt(), 0f, null) }, modifier = Modifier.semantics { contentDescription = "Posisi baca" })
                    SettingRow("Penanda & catatan", "${annotations.size} tersimpan") { panel = ReaderPanelPage.Notes }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(addNote) { Icon(Icons.Outlined.EditNote, null); Text("Tulis catatan") }
                        if (isPdf) OutlinedButton(search) { Icon(Icons.Outlined.Search, null); Text("Cari teks") }
                    }
                    if (publication != null) LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(publication.toc) { chapter ->
                            TextButton({ val index = publication.chapters.indexOfFirst { it.path == chapter.path.substringBefore('#') }; if (index >= 0) navigate(index, 0f, chapter.path.substringAfter('#', "").ifBlank { null }) }) { Text(chapter.title) }
                        }
                    }
                    TextButton(finish) { Text(if (book.status == "Finished") "Tandai sedang dibaca" else "Tandai selesai dibaca") }
                }
                ReaderPanelPage.Notes -> {
                    if (annotations.isEmpty()) Text("Belum ada penanda atau catatan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) { items(annotations, key = { it.id }) { note ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { navigate(note.position, note.offset, null) }.padding(vertical = 14.dp)) {
                                Text("${note.kind.labelId()} · ${if (isPdf) "Halaman" else "Bab"} ${note.position+1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(note.note.ifBlank { note.text }, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton({ remove(note) }) { Icon(Icons.Outlined.DeleteOutline, "Hapus ${note.kind.labelId()}") }
                        }
                    } }
                }
            }
        }
    }
}

@Composable fun PageModeChoices(selected: PageTurn, select: (PageTurn) -> Unit) {
    BoxWithConstraints {
        val columns = if (maxWidth >= 520.dp) 3 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { PageTurn.available.chunked(columns).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min),horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { mode ->
                val active = mode == selected
                Surface(Modifier.weight(1f).fillMaxHeight().testTag("reader-mode-${mode.name}").selectable(active, role = Role.RadioButton, onClick = { select(mode) }), shape = RoundedCornerShape(12.dp), color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow, border = BorderStroke(if (active) 1.5.dp else .5.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Icon(when(mode) { PageTurn.Withdrawal -> Icons.Outlined.AutoStories; PageTurn.Overlay -> Icons.Outlined.Layers; PageTurn.Shift, PageTurn.HorizontalScrolling -> Icons.Outlined.SwapHoriz; PageTurn.Scroll -> Icons.Outlined.SwapVert; PageTurn.NoAnimation -> Icons.Outlined.Description; PageTurn.Scrolling -> Icons.Outlined.UnfoldMore }, null, tint = MaterialTheme.colorScheme.primary)
                            if (active) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Text(mode.title, style = MaterialTheme.typography.titleMedium)
                        Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } }
        } }
    }
}

@Composable private fun ToolTile(icon: ImageVector, title: String, value: String, modifier: Modifier, click: () -> Unit) {
    Surface(onClick = click, modifier = modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable fun ThemeSwatches(selected: String, select: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf("Light" to Color(0xFFF8F9F6), "Sepia" to Color(0xFFF1E5CB), "Dark" to Color(0xFF272C30)).forEach { (key, color) ->
            Column(Modifier.widthIn(min = 56.dp).selectable(selected == key, role = Role.RadioButton, onClick = { select(key) }).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(color).border(if (selected == key) 2.dp else .5.dp, if (selected == key) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape), contentAlignment = Alignment.Center) {
                    if (selected == key) Icon(Icons.Outlined.Check, null, tint = if (key == "Dark") Color.White else MaterialTheme.colorScheme.primary)
                }
                Text(key.labelId(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable private fun BrightnessControl(settings: Settings, update: ((Settings) -> Settings) -> Unit, preview: (Float) -> Unit) {
    PreferenceSwitch("Kecerahan otomatis", "Ikuti pengaturan ponsel.", settings.brightness < 0) { automatic -> update { it.copy(brightness = if (automatic) -1f else .5f) } }
    var value by remember(settings.brightness) { mutableFloatStateOf(settings.brightness.coerceIn(.02f, 1f)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Outlined.BrightnessLow, null)
        Slider(value, { value = it; preview(it) }, Modifier.weight(1f).semantics { contentDescription = "Kecerahan layar baca" }, enabled = settings.brightness >= 0, valueRange = .02f..1f, onValueChangeFinished = { val brightness = value; update { it.copy(brightness = brightness) } })
        Icon(Icons.Outlined.BrightnessHigh, null)
    }
}

@Composable private fun PreferenceSlider(value: Float, range: ClosedFloatingPointRange<Float>, label: String, finish: (Float) -> Unit) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Slider(draft, { draft = it }, valueRange = range, onValueChangeFinished = { finish(draft) }, modifier = Modifier.semantics { contentDescription = label })
}
