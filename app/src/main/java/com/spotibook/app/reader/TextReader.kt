package com.spotibook.app.reader

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.*
import android.webkit.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import com.spotibook.app.data.Annotation
import com.spotibook.app.data.Settings
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File

data class TextSelection(val text: String, val start: Int, val end: Int)

private fun jsString(value: String): String = JsonPrimitive(value).toString().replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")

@SuppressLint("SetJavaScriptEnabled")
@Composable fun TextReader(root: File, publication: Publication, chapter: Int, offset: Float, settings: Settings, annotations: List<Annotation>,
    navigationKey: Long, anchor: String?, location: (Int, Float) -> Unit, navigate: (Int, Float, String?) -> Unit, selected: (TextSelection, Boolean) -> Unit, toggle: () -> Unit, error: (String) -> Unit) {
    val latestLocation by rememberUpdatedState(location); val latestSelection by rememberUpdatedState(selected); val latestToggle by rememberUpdatedState(toggle)
    val latestNavigate by rememberUpdatedState(navigate); val latestChapter by rememberUpdatedState(chapter); val latestError by rememberUpdatedState(error)
    val latestNavigationKey by rememberUpdatedState(navigationKey)
    var loadedKey by remember { mutableStateOf("") }
    val marks = annotations.filter { it.position == chapter && it.textStart >= 0 && it.textEnd > it.textStart }
    val key = "$chapter|$navigationKey|${settings.readerTheme}|${settings.fontSize}|${settings.lineSpacing}|${settings.margin}|${settings.fontFamily}|${marks.hashCode()}"
    fun composeHtml(): String {
        val chapterFile = SafeFiles.child(root, publication.chapters[chapter].path)
        val document = Jsoup.parse(chapterFile, "UTF-8"); Documents.sanitize(document)
        val background = when(settings.readerTheme) { "Dark", "Black" -> "#171C19"; "Sepia" -> "#F3E9D2"; else -> "#F7F5EF" }
        val foreground = if (settings.readerTheme in listOf("Dark","Black")) "#F3F0E8" else "#292B28"
        val font = when(settings.fontFamily) { "Mono" -> "monospace"; "Sans" -> "sans-serif"; else -> "serif" }
        document.head().prependElement("meta").attr("name", "viewport").attr("content", "width=device-width, initial-scale=1")
        document.head().prependElement("meta").attr("http-equiv", "Content-Security-Policy").attr("content", "default-src 'none'; img-src https://appassets.androidplatform.net data:; style-src 'unsafe-inline' https://appassets.androidplatform.net; font-src https://appassets.androidplatform.net; script-src 'nonce-spotibook'; connect-src 'none'; frame-src 'none'; form-action 'none'")
        document.head().appendElement("style").appendText("html{background:$background!important;color:$foreground!important;overflow-wrap:anywhere}body{box-sizing:border-box;max-width:850px;margin:auto!important;padding:${settings.margin}px!important;font-family:$font!important;font-size:${settings.fontSize}px!important;line-height:${settings.lineSpacing}!important;color:$foreground!important;background:$background!important}body *{max-width:100%;}p,div,span,li{font-family:inherit!important;font-size:inherit!important;line-height:inherit!important;color:inherit}img,svg{height:auto;max-width:100%}a{color:${if(settings.readerTheme == "Dark") "#A9BBA5" else "#244C3B"}}mark{color:#292B28!important;background:#E8C97D!important}pre{white-space:pre-wrap}table{max-width:100%;display:block;overflow:auto}")
        val highlights = JsonArray(marks.map { a -> buildJsonObject { put("start", a.textStart); put("end", a.textEnd); put("text", a.text) } }).toString().replace("<", "\\u003c").replace(">", "\\u003e").replace("&", "\\u0026")
        val script = """
            (function(){
              function nodes(){var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT),a=[],n;while(n=w.nextNode()){if(!['SCRIPT','STYLE'].includes(n.parentElement.tagName))a.push(n);}return a;}
              function mark(m){var ns=nodes(),at=0,s=null,e=null;for(var n of ns){var next=at+n.length;if(s===null&&m.start>=at&&m.start<next)s=[n,m.start-at];if(m.end>at&&m.end<=next){e=[n,m.end-at];break;}at=next;}if(s&&e){var r=document.createRange();r.setStart(s[0],s[1]);r.setEnd(e[0],e[1]);if(r.toString()!==m.text)return;var tag=document.createElement('mark');tag.appendChild(r.extractContents());r.insertNode(tag);}}
              $highlights.sort((a,b)=>b.start-a.start).forEach(mark);
              window.spotiSelect=function(note){var sel=window.getSelection();if(!sel.rangeCount||sel.isCollapsed)return;var r=sel.getRangeAt(0),prior=document.createRange();prior.selectNodeContents(document.body);prior.setEnd(r.startContainer,r.startOffset);var start=prior.toString().length;Spotibook.selection(sel.toString(),start,start+sel.toString().length,note);sel.removeAllRanges();};
              var loaded=false,last=0;
              function report(){if(loaded){var range=Math.max(1,document.documentElement.scrollHeight-innerHeight);Spotibook.position($chapter,$navigationKey,Math.min(1,scrollY/range));}}
              addEventListener('scroll',()=>{clearTimeout(last);last=setTimeout(report,120);});
              addEventListener('load',()=>{setTimeout(()=>{scrollTo(0,${offset.coerceIn(0f,1f)}*Math.max(0,document.documentElement.scrollHeight-innerHeight));${anchor?.let { "document.getElementById(${jsString(it)})?.scrollIntoView();" } ?: ""}loaded=true;report();},100);});
              document.addEventListener('click',e=>{if(e.target.closest('a'))return;if(!window.getSelection().toString())Spotibook.toggle();});
            })();
        """.trimIndent()
        document.body().appendElement("script").attr("nonce", "spotibook").appendText(script)
        return document.outerHtml()
    }
    var renderedHtml by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(key) {
        try { renderedHtml = key to withContext(Dispatchers.IO) { composeHtml() } }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { latestError(e.message ?: "Bab tidak dapat dimuat.") }
    }
    fun load(view: WebView) {
        val rendered = renderedHtml ?: return
        if (rendered.first != key || loadedKey == key) return
        loadedKey = key
        view.setBackgroundColor(Color.parseColor(when(settings.readerTheme) { "Dark", "Black" -> "#171C19"; "Sepia" -> "#F3E9D2"; else -> "#F7F5EF" }))
        val base = "https://appassets.androidplatform.net/book/${publication.chapters[chapter].path}"
        view.loadDataWithBaseURL(base, rendered.second, "text/html", "UTF-8", null)
    }
    AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
        val loader = WebViewAssetLoader.Builder().addPathHandler("/book/", WebViewAssetLoader.InternalStoragePathHandler(context, root)).build()
        object : WebView(context) {
            override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
                val wrapped = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        callback.onCreateActionMode(mode, menu)
                        menu.add(0, 931, 0, "Sorot").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                        menu.add(0, 932, 1, "Catatan").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM); return true
                    }
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = callback.onPrepareActionMode(mode, menu)
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = if (item.itemId in 931..932) { evaluateJavascript("window.spotiSelect(${item.itemId == 932})", null); mode.finish(); true } else callback.onActionItemClicked(mode, item)
                    override fun onDestroyActionMode(mode: ActionMode) { callback.onDestroyActionMode(mode) }
                }
                return super.startActionMode(wrapped, type)
            }
        }.apply {
            this.settings.javaScriptEnabled = true; this.settings.allowFileAccess = false; this.settings.allowContentAccess = false
            this.settings.blockNetworkLoads = true; this.settings.domStorageEnabled = false; this.settings.setSupportMultipleWindows(false)
            this.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            isVerticalScrollBarEnabled = false
            addJavascriptInterface(object {
                @JavascriptInterface fun position(chapterIndex: Int, key: Long, fraction: Float) { post { if (chapterIndex == latestChapter && key == latestNavigationKey && fraction.isFinite()) latestLocation(chapterIndex, fraction.coerceIn(0f,1f)) } }
                @JavascriptInterface fun toggle() { post { latestToggle() } }
                @JavascriptInterface fun selection(text: String, start: Int, end: Int, note: Boolean) { post { if (text.isNotBlank() && text.length <= 20_000 && start >= 0 && end > start) latestSelection(TextSelection(text, start, end), note) } }
            }, "Spotibook")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                    loader.shouldInterceptRequest(request.url) ?: WebResourceResponse("text/plain", "UTF-8", 403, "Offline only", emptyMap(), "".byteInputStream())
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val uri = request.url
                    if (uri.scheme != "https" || uri.host != "appassets.androidplatform.net") { latestError("Tautan luar tidak tersedia dalam pembaca offline."); return true }
                    val relative = uri.path?.removePrefix("/book/").orEmpty()
                    val index = publication.chapters.indexOfFirst { it.path == relative }
                    if (index >= 0) {
                        if (index == latestChapter && !uri.fragment.isNullOrEmpty()) view.evaluateJavascript("document.getElementById(${jsString(uri.fragment!!)})?.scrollIntoView()", null)
                        else latestNavigate(index, 0f, uri.fragment)
                    } else latestError("Tautan itu tidak mengarah ke bab yang dapat dibaca.")
                    return true
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) { if (request.isForMainFrame) latestError("Bab tidak dapat ditampilkan: ${error.description}") }
            }
            load(this)
        }
    }, update = { view -> runCatching { load(view) }.onFailure { latestError(it.message ?: "Bab tidak dapat ditampilkan.") } }, onRelease = { view -> view.stopLoading(); view.removeJavascriptInterface("Spotibook"); view.destroy() })
}
