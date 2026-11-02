package com.spotibook.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay

@Composable internal fun FocusCompletionBanner(model:AppState) {
    val focus by model.focus.collectAsStateWithLifecycle()
    var dismissed by rememberSaveable { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    // Keep completion live while any screen is open, including the PDF reader.
    LaunchedEffect(focus.id,focus.running) {
        if(focus.running) {
            delay(model.app.focus.remaining(focus).coerceAtLeast(1))
            model.app.focus.recover(focus.id)
        }
    }
    LaunchedEffect(focus.id,focus.done,dismissed) {
        visible=focus.done && focus.id!=dismissed && System.currentTimeMillis()-focus.finished in 0..60_000
        if(visible){delay(8000);dismissed=focus.id;visible=false}
    }
    Box(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),contentAlignment=Alignment.TopCenter){
        AnimatedVisibility(visible,enter=slideInVertically(spring(dampingRatio=.85f,stiffness=Spring.StiffnessLow)){ -it }+fadeIn(),exit=slideOutVertically{ -it }+fadeOut()){
            Surface(Modifier.widthIn(max=520.dp).fillMaxWidth().semantics{liveRegion=LiveRegionMode.Polite},shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.primaryContainer,shadowElevation=8.dp){
                Row(Modifier.padding(start=18.dp,top=12.dp,bottom=12.dp,end=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    Icon(Icons.Outlined.TaskAlt,null,Modifier.size(30.dp),tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)){
                        Text("Fokus selesai",style=MaterialTheme.typography.titleMedium)
                        Text("${focus.plannedMillis/60_000} menit selesai. Waktunya istirahat sebentar.",style=MaterialTheme.typography.bodyMedium)
                    }
                    IconButton({dismissed=focus.id;model.task{com.spotibook.app.background.FocusAudio.stop()}}){Icon(Icons.Outlined.Close,"Tutup penanda Fokus")}
                }
            }
        }
    }
}
