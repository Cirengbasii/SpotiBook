package com.spotibook.app.reader

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlin.math.abs

/** Coordinates are in the original page layout, independent of display magnification. */
internal fun zoomScrollDelta(old: Float, new: Float, y: Float, panY: Float, height: Float, pageExtent: Float?): Float {
    fun inset(scale: Float) = pageExtent?.let { ((height / scale - it) / 2).coerceAtLeast(0f) } ?: 0f
    return y / old - y / new - panY / new + inset(new) - inset(old)
}
internal fun zoomPan(old: Float, new: Float, x: Float, pan: Float, oldPan: Float, width: Float, paperWidth: Float): Float {
    val limit = panLimit(new,width,paperWidth)
    return (x + pan - width / 2 - (x - width / 2 - oldPan) / old * new).coerceIn(-limit, limit)
}

internal fun panLimit(zoom:Float,width:Float,paperWidth:Float):Float = if(zoom<=1f)0f else ((paperWidth*zoom-width)/2).coerceAtLeast(0f)+width/2*((zoom-1).coerceAtMost(1f))
internal fun blankPanLimit(zoom:Float,height:Float):Float = height/2*((zoom-1).coerceIn(0f,1f))

private class PdfZoomState(zoom: MutableFloatState, val scroll: ScrollableState, val horizontal: ScrollableState?) {
    var scale by zoom
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)
    var transforming by mutableStateOf(false)
    var width = 1f; var height = 1f; var paperWidth = 1f; var pageExtent: Float? = null
    var animation: Job? = null
    private var pending = 0f
    private var awaitingLayout = false
    fun bounds(width: Float, height: Float, paperWidth: Float, extent: Float?) {
        if (this.width != width) panX *= width / this.width
        if (this.height != height) panY *= height / this.height
        this.width = width; this.height = height; this.paperWidth = paperWidth; pageExtent = extent
        clampPan()
    }
    private fun clampPan() {
        val limit = panLimit(scale,width,paperWidth)
        panX = panX.coerceIn(-limit, limit)
        val vertical=blankPanLimit(scale,height)
        panY=panY.coerceIn(-vertical,vertical)
    }
    fun transform(next: Float, center: Offset, pan: Offset = Offset.Zero) {
        val target = next.coerceIn(1f, 5f)
        if (target != scale) awaitingLayout = true
        pending += zoomScrollDelta(scale, target, center.y-panY, pan.y, height, pageExtent)
        if(horizontal==null) panX = zoomPan(scale, target, center.x, pan.x, panX, width, paperWidth)
        else {
            var delta=(center.x-width/2-panX)*(1/scale-1/target)-pan.x/target
            if(panX*delta>0){val restore=kotlin.math.sign(delta)*minOf(abs(delta),abs(panX)/target);panX-=restore*target;delta-=restore}
            val remaining=delta-horizontal.dispatchRawDelta(delta)
            panX=(panX-remaining*target).coerceIn(-blankPanLimit(target,width),blankPanLimit(target,width))
        }
        scale = target
        consumePending()
        if (!awaitingLayout) revealBlank()
    }
    private fun consumePending(){
        // Pull a displaced edge back first; then scroll normally through the document.
        if(panY*pending>0){
            val restore=kotlin.math.sign(pending)*minOf(kotlin.math.abs(pending),kotlin.math.abs(panY)/scale)
            panY-=restore*scale;pending-=restore
        }
        pending-=scroll.dispatchRawDelta(pending)
    }
    private fun revealBlank(){
        val limit=blankPanLimit(scale,height)
        panY=(panY-pending*scale).coerceIn(-limit,limit);pending=0f
    }
    fun laidOut() {
        awaitingLayout = false
        consumePending();revealBlank();clampPan()
    }
}

/** Both LazyListState and ScrollState use the same native scroll/zoom coordinate system. */
@Composable
internal fun PdfZoomViewport(
    zoom: MutableFloatState, scroll: ScrollableState,
    paperWidth: Float, pageExtent: Float? = null, active: Boolean = true,
    interaction: (Boolean) -> Unit, tap: (Float) -> Unit,
    horizontal: ScrollableState? = null,
    content: @Composable (Dp, Boolean) -> Unit
) {
    val state = remember(scroll, zoom, horizontal) { PdfZoomState(zoom, scroll, horizontal) }
    val scope = rememberCoroutineScope()
    val fling = ScrollableDefaults.flingBehavior()
    val latestInteraction by rememberUpdatedState(interaction)
    val latestTap by rememberUpdatedState(tap)
    fun animateZoom(target: Float, center: Offset) {
        state.animation?.cancel()
        state.animation = scope.launch {
            scroll.stopScroll(); horizontal?.stopScroll()
            latestInteraction(true)
            try { AnimationState(state.scale).animateTo(target, tween(220)) { state.transform(value, center) } }
            finally { latestInteraction(false) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds().testTag(if (active) "pdf-viewport" else "pdf-viewport-inactive")) {
        val realHeight = maxHeight
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }; val heightPx = with(density) { maxHeight.toPx() }
        SideEffect {
            state.bounds(widthPx, heightPx, paperWidth, pageExtent)
        }
        DisposableEffect(Unit) { onDispose { latestInteraction(false) } }
        val gestures = Modifier.pointerInput(state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.animation?.cancel()
                scope.launch { scroll.stopScroll();horizontal?.stopScroll() }
                var owned = false
                var multi = false
                var traveled = Offset.Zero
                val velocity = VelocityTracker()
                try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val count = event.changes.count { it.pressed }
                    if (count > 0) {
                        val pan = event.calculatePan()
                        traveled += pan
                        if (!owned && (count > 1 || (state.scale > 1f && traveled.getDistance() > viewConfiguration.touchSlop))) {
                            owned = true; state.transforming = true; state.animation?.cancel()
                            latestInteraction(true)
                        }
                        if (owned) {
                            multi = multi || count > 1
                            val center = event.calculateCentroid(useCurrent = false)
                            if (center != Offset.Unspecified) state.transform(state.scale * event.calculateZoom(), center, pan)
                            if (!multi) velocity.addPosition(event.changes.first().uptimeMillis, event.calculateCentroid())
                            event.changes.forEach { it.consume() }
                        }
                    }
                } while (event.changes.any { it.pressed })
                } finally {
                    if (owned) { state.transforming = false; latestInteraction(false) }
                }
                if (owned) {
                    if (!multi && kotlin.math.abs(state.panY)<.5f) {
                        val speed = -velocity.calculateVelocity().y / state.scale
                        val horizontalSpeed=-velocity.calculateVelocity().x/state.scale
                        if(horizontal!=null && abs(horizontalSpeed)>100f) scope.launch { horizontal.scroll { with(fling) { performFling(horizontalSpeed) } } }
                        if (abs(speed) > 100f) scope.launch { scroll.scroll { with(fling) { performFling(speed) } } }
                    }
                }
            }
        }.pointerInput(state) {
            detectTapGestures(onTap = { latestTap(it.x / size.width.coerceAtLeast(1)) }, onDoubleTap = {
                animateZoom(if (state.scale > 1.05f) 1f else 2f, it)
            })
        }.semantics {
            stateDescription = "Pembesaran ${(state.scale * 100).toInt()} persen"
            contentDescription = if(state.panY>1f)"Ruang di atas halaman ${state.panY.toInt()} piksel"else if(state.panY< -1f)"Ruang di bawah halaman ${(-state.panY).toInt()} piksel"else"Area baca PDF"
            customActions = listOf(
                CustomAccessibilityAction("Perbesar") { animateZoom((state.scale + .5f).coerceAtMost(5f), Offset(widthPx/2, heightPx/2)); true },
                CustomAccessibilityAction("Perkecil") { animateZoom((state.scale - .5f).coerceAtLeast(1f), Offset(widthPx/2, heightPx/2)); true }
            )
        }
        Box(Modifier.fillMaxSize().then(gestures)) {
            // Only the visible window changes height. Page sizes and list offsets never scale.
            Box(Modifier.fillMaxWidth().height(realHeight / state.scale)
                .graphicsLayer { scaleX = state.scale; scaleY = state.scale; translationX = state.panX; translationY=state.panY; transformOrigin = TransformOrigin(.5f, 0f) }
                .onGloballyPositioned { state.laidOut() }) {
                content(realHeight / state.scale, state.scale <= 1f && !state.transforming)
            }
        }
    }
}
