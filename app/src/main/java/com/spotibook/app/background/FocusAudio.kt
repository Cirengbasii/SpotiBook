package com.spotibook.app.background

import android.app.NotificationManager
import android.content.Context
import android.media.*
import android.net.Uri
import android.provider.OpenableColumns
import com.spotibook.app.data.Settings
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume

object FocusAudio {
    private var playing:Job?=null
    fun file(context:Context)=File(context.filesDir,"focus-sound.mp3")
    suspend fun import(context:Context,uri:Uri):String=withContext(Dispatchers.IO){
        val name=runCatching{context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{c->if(c.moveToFirst())c.getString(0)else null}}.getOrNull()?.take(200)?:"Bunyi pilihan"
        val staged=File(context.filesDir,"focus-sound.tmp")
        try {
            var count=0L
            context.contentResolver.openInputStream(uri)?.use{input->staged.outputStream().buffered().use{output->
                val buffer=ByteArray(16384)
                while(true){ensureActive();val n=input.read(buffer);if(n<0)break;count+=n;require(count<=20L*1024*1024){"Pilih berkas audio maksimal 20 MB."};output.write(buffer,0,n)}
            }}?:error("Berkas audio tidak dapat dibuka.")
            require(count>0){"Berkas audio kosong."}
            java.nio.file.Files.move(staged.toPath(),file(context).toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            name
        }finally{staged.delete()}
    }
    suspend fun stop()=withContext(Dispatchers.Main.immediate){playing?.cancelAndJoin();playing=null}
    suspend fun play(context:Context,settings:Settings)=withContext(Dispatchers.Main.immediate){
        playing?.cancelAndJoin()
        if(settings.focusSound=="Silent"||settings.focusVolume<=0)return@withContext
        val audio=context.getSystemService(AudioManager::class.java)
        val notices=context.getSystemService(NotificationManager::class.java)
        if(audio.ringerMode!=AudioManager.RINGER_MODE_NORMAL||notices.currentInterruptionFilter!=NotificationManager.INTERRUPTION_FILTER_ALL)return@withContext
        val own=currentCoroutineContext()[Job];playing=own
        try {
            if(settings.focusSound=="Custom"&&file(context).isFile){
                val media=MediaPlayer()
                try{
                    media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    media.setDataSource(file(context).absolutePath);media.isLooping=false;media.setVolume(settings.focusVolume,settings.focusVolume)
                    withTimeoutOrNull(8000){suspendCancellableCoroutine<Unit>{cont->
                        media.setOnCompletionListener{if(cont.isActive)cont.resume(Unit)}
                        media.setOnErrorListener{_,_,_->if(cont.isActive)cont.resume(Unit);true}
                        media.setOnPreparedListener{if(cont.isActive)it.start()}
                        media.prepareAsync()
                    }}
                }finally{media.release()}
            }else{
                val tone=ToneGenerator(AudioManager.STREAM_NOTIFICATION,(settings.focusVolume*100).toInt().coerceIn(0,100))
                try{tone.startTone(ToneGenerator.TONE_PROP_ACK,400);delay(600)}finally{tone.release()}
            }
        }catch(e:CancellationException){throw e}catch(_:Exception){/* A missing/unsupported sound must not prevent the completion notification. */}
        finally{if(playing===own)playing=null}
    }
}
