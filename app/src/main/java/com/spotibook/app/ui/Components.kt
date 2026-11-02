package com.spotibook.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.spotibook.app.data.Book
import com.spotibook.app.reader.SafeFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.DateTimeFormatter

fun minutes(millis: Long): String = if (millis < 60_000) "${millis / 1000} dtk" else if (millis < 3_600_000) "${millis / 60_000} mnt" else "${millis / 3_600_000} j ${(millis / 60_000) % 60} mnt"
fun dateLabel(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM · HH:mm",Indonesian))

@Composable fun PageTitle(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
        if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun SectionTitle(title: String, action: String? = null, onClick: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onClick) { Text(action) }
    }
}
@Composable fun EmptyState(title: String, body: String, action: String? = null, onClick: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.AutoStories, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.secondary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 290.dp))
        if (action != null) Button(onClick) { Text(action) }
    }
}
@Composable fun Metric(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun BookCover(book: Book, model: AppState, modifier: Modifier = Modifier, thumbnail: Boolean = false) {
    val bitmap by produceState<ImageBitmap?>(null, book.id, book.cover, thumbnail) {
        value = withContext(Dispatchers.IO) { book.cover?.let { path -> runCatching {
            com.spotibook.app.reader.Images.decode(SafeFiles.child(model.repo.documents.directory(book.id), path), if (thumbnail) 160 else 600)?.asImageBitmap()
        }.getOrNull() } }
    }
    Box(modifier.clip(RoundedCornerShape(if (thumbnail) 3.dp else 10.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
        if (bitmap != null) Image(bitmap!!, "Sampul ${book.title}", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else if (thumbnail) Text(book.title.take(1), color = Color.White, style = MaterialTheme.typography.labelMedium)
        else Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(book.format, style = MaterialTheme.typography.labelSmall, color = Color(0xFFD1AD78))
            Text(book.title, color = Color(0xFFF7F5EF), fontFamily = AppFont, fontSize = 17.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            HorizontalDivider(color = Color(0xFFD1AD78), thickness = 2.dp)
        }
    }
}
@Composable fun BookRow(book: Book, model: AppState, open: () -> Unit, details: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clickable(onClick = open).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(15.dp)) {
        BookCover(book, model, Modifier.width(57.dp).height(82.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(book.author.labelId(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            LinearProgressIndicator(progress = { book.progress }, Modifier.fillMaxWidth().height(3.dp))
            Text("${book.status.labelId()} · ${(book.progress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (details != null) IconButton(details) { Icon(Icons.Outlined.MoreVert, "Rincian buku") }
    }
}
@Composable fun Confirm(title: String, text: String, confirm: String, dismiss: () -> Unit, accept: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) }, confirmButton = { TextButton(accept) { Text(confirm) } }, dismissButton = { TextButton(dismiss) { Text("Batal") } })
}
@Composable fun TextEntry(title: String, initial: String = "", numeric: Boolean = false, range: IntRange? = null, dismiss: () -> Unit, save: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    val valid = value.isNotBlank() && value.length <= 200 && (range == null || value.toIntOrNull()?.let { it in range } == true)
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Column {
        OutlinedTextField(value, { value = it }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text))
        if (range != null) Text("${range.first}–${range.last}", style = MaterialTheme.typography.bodySmall)
    } }, confirmButton = { TextButton({ save(value.trim()); dismiss() }, enabled = valid) { Text("Simpan") } }, dismissButton = { TextButton(dismiss) { Text("Batal") } })
}

@Composable fun SpotibookBottomBar(destinations: List<Triple<String, String, ImageVector>>, route: String, select: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shadowElevation = 10.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            destinations.forEach { (path, label, icon) ->
                val picked = route == path
                Column(Modifier.weight(1f).selectable(picked, role = Role.Tab, onClick = { select(path) }).padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, null, Modifier.size(27.dp), tint = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.width(22.dp).height(3.dp).background(if(picked)MaterialTheme.colorScheme.primary else Color.Transparent,CircleShape))
                }
            }
        }
    }
}
