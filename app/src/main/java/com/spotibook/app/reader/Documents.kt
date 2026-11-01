package com.spotibook.app.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.spotibook.app.data.Book
import com.spotibook.app.data.LibraryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

@Serializable data class Chapter(val title: String, val path: String)
@Serializable data class Publication(val title: String, val author: String, val chapters: List<Chapter>, val toc: List<Chapter> = chapters, val cover: String? = null)

object SafeFiles {
    fun child(root: File, name: String): File {
        require(name.isNotBlank() && !name.contains('\\') && !name.startsWith('/') && !name.contains(':')) { "Lokasi berkas dalam arsip tidak valid." }
        val result = File(root, name).canonicalFile
        require(result.toPath().startsWith(root.canonicalFile.toPath()) && result != root.canonicalFile) { "Arsip berisi lokasi berkas yang tidak aman." }
        return result
    }
    fun unzip(file: File, target: File, maxBytes: Long = 256L * 1024 * 1024, maxEntries: Int = 5000) {
        target.mkdirs(); var bytes = 0L; var entries = 0; val paths = mutableSetOf<String>()
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= maxEntries) { "Arsip berisi terlalu banyak berkas." }
                val output = child(target, entry.name)
                require(paths.add(output.path.lowercase())) { "Arsip berisi nama berkas duplikat." }
                if (entry.isDirectory) output.mkdirs() else {
                    output.parentFile?.mkdirs()
                    output.outputStream().buffered().use { stream ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) { val n = zip.read(buffer); if (n < 0) break; bytes += n
                            require(bytes <= maxBytes) { "Ukuran isi arsip melebihi batas aman." }; stream.write(buffer, 0, n) }
                    }
                }
                zip.closeEntry()
            }
        }
    }
}

class Documents(private val context: Context, private val dao: LibraryDao) {
    val booksRoot = File(context.filesDir, "books").apply { mkdirs() }
    fun directory(id: String) = SafeFiles.child(booksRoot, id)
    fun source(book: Book) = SafeFiles.child(directory(book.id), book.fileName)
    fun publication(book: Book): Publication = Json.decodeFromString(File(directory(book.id), "publication.json").readText())

    suspend fun import(uri: Uri): Book = withContext(Dispatchers.IO) {
        val name = runCatching { context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            val column = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && column >= 0) c.getString(column) else null
        } }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment ?: "Untitled"
        val extension = name.substringAfterLast('.', "").lowercase()
        val ext = if (extension in setOf("pdf", "epub", "txt", "html", "htm")) extension else when (context.contentResolver.getType(uri)) {
            "application/pdf" -> "pdf"; "application/epub+zip" -> "epub"; "text/plain" -> "txt"; "text/html", "application/xhtml+xml" -> "html"; else -> extension
        }
        require(ext in setOf("pdf", "epub", "txt", "html", "htm")) { "Format yang didukung: PDF, EPUB, TXT, dan HTML." }
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val id = UUID.randomUUID().toString(); val dir = directory(id).apply { mkdirs() }
        try {
            val file = File(dir, "book.$ext"); val digest = MessageDigest.getInstance("SHA-256"); var size = 0L
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().buffered().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) { val n = input.read(buffer); if (n < 0) break; size += n
                    require(size <= 512L * 1024 * 1024) { "Berkas lebih dari 512 MB belum didukung." }; digest.update(buffer, 0, n); out.write(buffer, 0, n) }
            } } ?: error("Penyedia dokumen tidak dapat membuka berkas ini.")
            require(size > 0) { "Berkas ini kosong." }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val existing = dao.byHash(hash)
            require(existing == null || !source(existing).isFile) { "Buku ini sudah ada di pustaka." }
            var title = name.substringBeforeLast('.'); var author = "Unknown author"; var total = 1; var cover: String? = null
            when (ext) {
                "pdf" -> {
                    PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                        total = renderer.pageCount; require(total > 0) { "PDF ini tidak memiliki halaman." }
                        renderer.openPage(0).use { page ->
                            val bitmap = Bitmap.createBitmap(360, (360f * page.height / page.width).toInt().coerceIn(1, 720), Bitmap.Config.ARGB_8888)
                            bitmap.eraseColor(Color.WHITE); page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            File(dir, "cover.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 90, it) }; bitmap.recycle(); cover = "cover.png"
                        }
                    }
                }
                "epub" -> {
                    val pub = prepareEpub(file, File(dir, "content"))
                    title = pub.title.ifBlank { title }; author = pub.author.ifBlank { author }; total = pub.chapters.size
                    File(dir, "publication.json").writeText(Json.encodeToString(pub)); cover = pub.cover?.let { "content/$it" }
                }
                else -> {
                    require(size <= 16L * 1024 * 1024) { "Berkas TXT dan HTML harus lebih kecil dari 16 MB." }
                    val content = File(dir, "content").apply { mkdirs() }
                    if (ext == "txt") {
                        val text = file.readText().removePrefix("\uFEFF")
                        // Chapters keep WebView memory bounded and make long text navigation useful.
                        val chunks = text.chunked(60_000)
                        val chapters = chunks.mapIndexed { i, chunk ->
                            val path = "part-$i.html"
                            File(content, path).writeText("<!doctype html><html><head><meta charset='utf-8'></head><body><pre style='white-space:pre-wrap;font:inherit'>${org.jsoup.nodes.Entities.escape(chunk)}</pre></body></html>")
                            Chapter(if (chunks.size == 1) title else "Bagian ${i + 1}", path)
                        }
                        total = chapters.size; File(dir, "publication.json").writeText(Json.encodeToString(Publication(title, author, chapters)))
                    } else {
                        val doc = Jsoup.parse(file, "UTF-8")
                        title = doc.title().ifBlank { title }; author = doc.selectFirst("meta[name=author]")?.attr("content")?.ifBlank { author } ?: author
                        sanitize(doc); File(content, "index.html").writeText(doc.outerHtml())
                        File(dir, "publication.json").writeText(Json.encodeToString(Publication(title, author, listOf(Chapter(title, "index.html")))))
                    }
                }
            }
            val book = Book(id, title, author, if (ext == "htm") "HTML" else ext.uppercase(), file.name, uri.toString(), hash, cover, total)
            if (existing != null) {
                val target = directory(existing.id)
                require(!target.exists()) { "Folder buku tidak lengkap. Pulihkan cadangan yang menyertakan berkas buku." }
                check(dir.renameTo(target)) { "Buku ini tidak dapat disambungkan kembali." }
                return@withContext existing.copy(fileName = file.name, sourceUri = uri.toString(), cover = cover, total = total).also { dao.putBook(it) }
            }
            dao.putBook(book); book
        } catch (e: Exception) {
            withContext(NonCancellable) {
                if (runCatching { dao.book(id) == null }.getOrDefault(false)) dir.deleteRecursively()
            }
            throw e
        }
    }

    fun prepareEpub(file: File, content: File): Publication {
        SafeFiles.unzip(file, content)
        require(!File(content, "META-INF/encryption.xml").exists()) { "EPUB dengan enkripsi/DRM belum didukung. Gunakan EPUB tanpa enkripsi." }
        val container = Jsoup.parse(File(content, "META-INF/container.xml").readText(), "", Parser.xmlParser())
        val opfPath = container.getElementsByTag("rootfile").firstOrNull()?.attr("full-path") ?: error("Struktur EPUB tidak lengkap.")
        val opf = SafeFiles.child(content, opfPath); val packageDoc = Jsoup.parse(opf.readText(), "", Parser.xmlParser())
        require(packageDoc.getElementsByTag("meta").none { it.attr("property") == "rendition:layout" && it.text() == "pre-paginated" }) { "EPUB bertata letak tetap belum didukung. Gunakan EPUB mengalir atau PDF." }
        val items = packageDoc.getElementsByTag("item").associateBy { it.id() }
        fun path(href: String): String {
            val decoded = Uri.decode(href.substringBefore('#'))
            val target = File(opf.parentFile, decoded).canonicalFile
            require(target.toPath().startsWith(content.canonicalFile.toPath())) { "EPUB mencoba mengakses berkas di luar buku." }
            return target.relativeTo(content).invariantSeparatorsPath
        }
        val chapters = packageDoc.getElementsByTag("itemref").mapNotNull { ref -> items[ref.attr("idref")]?.let { item ->
            val relative = path(item.attr("href")); val chapter = SafeFiles.child(content, relative)
            require(chapter.isFile) { "Bab EPUB tidak ditemukan: $relative" }
            require(chapter.length() <= 4L * 1024 * 1024) { "EPUB ini memiliki bab lebih dari 4 MB. Gunakan buku dengan bab lebih kecil atau format PDF." }
            val doc = Jsoup.parse(chapter, "UTF-8"); val title = doc.selectFirst("h1,h2,title")?.text()?.ifBlank { null } ?: "Bab"
            sanitize(doc); chapter.writeText(doc.outerHtml()); Chapter(title, relative)
        } }
        require(chapters.isNotEmpty()) { "EPUB ini tidak memiliki bab yang dapat dibaca." }
        val coverItem = items.values.firstOrNull { "cover-image" in it.attr("properties").split(' ') }
            ?: packageDoc.getElementsByTag("meta").firstOrNull { it.attr("name") == "cover" }?.let { items[it.attr("content")] }
        val cover = coverItem?.attr("href")?.let(::path)?.takeIf { SafeFiles.child(content, it).isFile }
        val nav = items.values.firstOrNull { "nav" in it.attr("properties").split(' ') }
        val toc = nav?.let { item ->
            val navFile = SafeFiles.child(content, path(item.attr("href")))
            if (!navFile.exists()) emptyList() else Jsoup.parse(navFile, "UTF-8").select("nav a[href]").mapNotNull { a ->
                val resource = File(navFile.parentFile, Uri.decode(a.attr("href").substringBefore('#'))).canonicalFile
                chapters.firstOrNull { SafeFiles.child(content, it.path) == resource }?.let { Chapter(a.text(), it.path + a.attr("href").substringAfter('#', "").let { f -> if (f.isBlank()) "" else "#$f" }) }
            }
        } ?: emptyList()
        fun metadata(local: String) = packageDoc.getAllElements().firstOrNull { it.tagName().substringAfter(':') == local }?.text().orEmpty()
        return Publication(metadata("title"), metadata("creator"), chapters, toc.ifEmpty { chapters }, cover)
    }
    companion object {
        fun sanitize(doc: org.jsoup.nodes.Document) {
            doc.select("script,iframe,frame,frameset,object,embed,form,input,button,textarea,base,meta[http-equiv],svg script").remove()
            doc.allElements.forEach { e -> e.attributes().asList().forEach { a ->
                if (a.key.startsWith("on", true) || a.key.equals("srcdoc", true) || (a.key in setOf("href", "src", "xlink:href") && a.value.trim().startsWith("javascript:", true))) e.removeAttr(a.key)
            } }
        }
    }
}
