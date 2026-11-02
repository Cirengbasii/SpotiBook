package com.spotibook.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.speech.SpeechService

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SpeechSheet(model:AppState,id:String,page:Int,dismiss:()->Unit){
    val state by SpeechService.state.collectAsStateWithLifecycle()
    val same=state.bookId==id
    fun command(action:String,speed:Float=state.speed){try{SpeechService.command(model.app,action,id,page,speed)}catch(e:Exception){model.messages.tryEmit(e.message?:"Pemutaran tidak dapat dimulai.")}}
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){
        Column(Modifier.fillMaxWidth().heightIn(max=600.dp).verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){Text("Dengarkan buku",Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall);IconButton(dismiss){Icon(Icons.Outlined.Close,"Tutup pemutar")}}
            Text("Bahasa Indonesia · suara offline perangkat",color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tetap berbunyi saat layar mati. Kualitas suara mengikuti paket suara yang terpasang di HP.",style=MaterialTheme.typography.bodyMedium)
            if(same){
                Text(state.title,style=MaterialTheme.typography.titleLarge)
                if(state.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
                if(state.error.isNotEmpty())Text(state.error,color=MaterialTheme.colorScheme.error)
                if(state.text.isNotEmpty())Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.medium){Text(state.text,Modifier.padding(16.dp),maxLines=7)}
                Text("Halaman / bab ${state.page+1}")
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                    IconButton({command("PREVIOUS")},enabled=!state.loading&&state.error.isEmpty()){Icon(Icons.Outlined.SkipPrevious,"Bagian sebelumnya")}
                    FilledTonalButton({command(if(state.playing)"PAUSE"else"PLAY")},enabled=!state.loading&&state.error.isEmpty()){Text(if(state.playing)"Jeda"else"Lanjutkan")}
                    IconButton({command("NEXT")},enabled=!state.loading&&state.error.isEmpty()){Icon(Icons.Outlined.SkipNext,"Bagian berikutnya")}
                    IconButton({command("STOP")}){Icon(Icons.Outlined.Stop,"Berhenti membaca")}
                }
            }
            Button({command("OPEN")},Modifier.fillMaxWidth().heightIn(min=52.dp)){Text("Baca dari halaman ini")}
            Text("Kecepatan ${"%.2f".format(state.speed)}×")
            Slider(state.speed,{command("SPEED",it)},valueRange=.75f..2f,steps=4,enabled=same&&!state.loading)
            OutlinedButton({if(state.bookId.isNotEmpty())command("STOP");try{model.app.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}catch(_:Exception){model.messages.tryEmit("Buka pengaturan HP → Bahasa → Text-to-speech.")}},Modifier.fillMaxWidth()){Text("Pengaturan suara Indonesia")}
            Spacer(Modifier.height(12.dp))
        }
    }
}
