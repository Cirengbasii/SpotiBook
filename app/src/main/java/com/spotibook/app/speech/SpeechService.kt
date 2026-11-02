package com.spotibook.app.speech

import android.app.*
import android.content.*
import android.media.*
import android.media.session.*
import android.os.*
import android.speech.tts.*
import com.spotibook.app.*
import com.spotibook.app.data.Book
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow

data class SpeechState(val bookId:String="",val title:String="",val page:Int=0,val chunk:Int=0,val text:String="",val playing:Boolean=false,val loading:Boolean=false,val error:String="",val speed:Float=1f)

class SpeechService:Service(){
    companion object{
        val state=MutableStateFlow(SpeechState())
        fun command(context:Context,action:String,id:String="",page:Int=0,speed:Float=1f){
            val intent=Intent(context,SpeechService::class.java).setAction(action).putExtra("book",id).putExtra("page",page).putExtra("speed",speed)
            if(action=="OPEN"||action=="PLAY")context.startForegroundService(intent)else context.startService(intent)
        }
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val ready=CompletableDeferred<Boolean>()
    private lateinit var tts:TextToSpeech
    private lateinit var session:MediaSession
    private lateinit var audio:AudioManager
    private lateinit var focus:AudioFocusRequest
    private lateinit var wake:PowerManager.WakeLock
    private var book:Book?=null;private var chunks=emptyList<String>();private var generation=0
    private var task:Job?=null
    private val repo get()=(application as SpotibookApp).repository
    private val storage by lazy{getSharedPreferences("speech-position",MODE_PRIVATE)}
    private val noisy=object:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){pause()}}
    override fun onCreate(){
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("speech","Pembacaan buku",NotificationManager.IMPORTANCE_LOW).apply{setSound(null,null)})
        audio=getSystemService(AudioManager::class.java)
        focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setOnAudioFocusChangeListener{if(it<0)pause()}.build()
        wake=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Spotibook:speech")
        session=MediaSession(this,"Spotibook suara").apply{
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object:MediaSession.Callback(){override fun onPlay(){play()};override fun onPause(){pause()};override fun onStop(){finish()};override fun onSkipToNext(){move(1)};override fun onSkipToPrevious(){move(-1)}})
            isActive=true
        }
        if(Build.VERSION.SDK_INT>=33)registerReceiver(noisy,IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),RECEIVER_NOT_EXPORTED)else registerReceiver(noisy,IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
        tts=TextToSpeech(this){status->
            scope.launch{
                val voice=if(status==TextToSpeech.SUCCESS)tts.voices?.filter{it.locale.language in listOf("id","in")&&!it.isNetworkConnectionRequired&&!it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)}?.maxByOrNull{it.quality}else null
                if(voice!=null){tts.voice=voice;tts.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())}
                ready.complete(voice!=null)
            }
        }
        tts.setOnUtteranceProgressListener(object:UtteranceProgressListener(){
            override fun onStart(id:String?){}
            override fun onDone(id:String?){scope.launch{if(id==token()&&state.value.playing){val next=state.value.chunk+1;if(next<chunks.size){state.value=state.value.copy(chunk=next);speak()}else load(state.value.page+1)}}}
            @Deprecated("Platform callback") override fun onError(id:String?){scope.launch{if(id==token())fail("Suara tidak dapat diputar. Periksa paket suara Indonesia di pengaturan TTS HP.")}}
        })
    }
    private fun token()="$generation:${state.value.page}:${state.value.chunk}"
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        when(intent?.action){
            "OPEN"->{startForeground(202,notification());generation++;tts.stop();task?.cancel();val id=intent.getStringExtra("book").orEmpty();val page=intent.getIntExtra("page",0)
                state.value=SpeechState(bookId=id,page=page,loading=true,speed=storage.getFloat("speed",1f))
                task=scope.launch{try{book=withContext(Dispatchers.IO){repo.dao.book(id)}?:error("Buku sudah tidak tersedia.");state.value=state.value.copy(title=book!!.title)
                    require(withTimeout(15000){ready.await()}){"Suara Indonesia offline belum tersedia. Pasang data suara Indonesia melalui pengaturan TTS HP."}
                    load(page)
                }catch(e:CancellationException){throw e}catch(e:Exception){fail(e.message?:"Buku tidak dapat dibacakan.")}}
            }
            "PLAY"->{if(book==null){val id=intent.getStringExtra("book").orEmpty().ifBlank{state.value.bookId};if(id.isNotEmpty())return onStartCommand(Intent(this,SpeechService::class.java).setAction("OPEN").putExtra("book",id).putExtra("page",storage.getInt(id+"page",intent.getIntExtra("page",0))),flags,startId);startForeground(202,notification());finish()}else{startForeground(202,notification());play()}}
            "PAUSE"->pause()
            "STOP"->finish()
            "NEXT"->move(1)
            "PREVIOUS"->move(-1)
            "SPEED"->{val speed=intent.getFloatExtra("speed",1f).coerceIn(.75f,2f);storage.edit().putFloat("speed",speed).apply();state.value=state.value.copy(speed=speed);if(state.value.playing){generation++;tts.stop();speak()}}
        };return START_NOT_STICKY
    }
    private fun load(page:Int){
        task?.cancel();generation++;tts.stop();chunks=emptyList()
        val b=book?:return
        if(page>=b.total){state.value=state.value.copy(playing=false,loading=false,text="Pembacaan buku selesai.");pause();return}
        state.value=state.value.copy(page=page.coerceAtLeast(0),chunk=0,loading=true,playing=false,error="")
        val own=generation
        holdCpu()
        task=scope.launch{try{
            var index=page.coerceAtLeast(0);var found=emptyList<String>()
            while(index<b.total && index<page+12 && found.isEmpty()){ensureActive();found=withContext(Dispatchers.IO){extractSpeech(repo,b,index)};if(found.isEmpty())index++}
            if(own!=generation)return@launch
            require(found.isNotEmpty()){"Teks belum ditemukan setelah pemindaian OCR. Coba halaman isi yang lebih jelas; halaman sampul atau gambar tanpa tulisan tidak dapat dibacakan."}
            chunks=found
            val saved=if(storage.getInt(b.id+"page",-1)==index)storage.getInt(b.id+"chunk",0).coerceIn(0,chunks.lastIndex)else 0
            state.value=state.value.copy(page=index,chunk=saved,loading=false);play()
        }catch(e:CancellationException){throw e}catch(e:Exception){fail(e.message?:"Teks tidak dapat dibaca.")}}
    }
    private fun play(){if(chunks.isEmpty())return
        startForeground(202,notification())
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED){fail("Audio sedang digunakan. Coba putar kembali.");return}
        state.value=state.value.copy(playing=true,error="");speak()
    }
    private fun speak(){
        val s=state.value;val text=chunks.getOrNull(s.chunk)?:return
        generation++
        holdCpu()
        state.value=s.copy(text=text);save();tts.setSpeechRate(s.speed)
        if(tts.speak(text,TextToSpeech.QUEUE_FLUSH,Bundle(),token())==TextToSpeech.ERROR){fail("Mesin suara gagal membaca teks.");return};update()
    }
    private fun save(){val s=state.value;if(s.bookId.isNotEmpty())storage.edit().putInt(s.bookId+"page",s.page).putInt(s.bookId+"chunk",s.chunk).apply()}
    private fun holdCpu(){if(wake.isHeld)wake.release();wake.acquire(10*60*1000L)}
    private fun pause(){generation++;tts.stop();task?.cancel();state.value=state.value.copy(playing=false,loading=false);save();if(wake.isHeld)wake.release();audio.abandonAudioFocusRequest(focus);update();stopForeground(STOP_FOREGROUND_DETACH)}
    private fun move(delta:Int){val next=state.value.chunk+delta;if(next in chunks.indices){generation++;tts.stop();state.value=state.value.copy(chunk=next);play()}else load(state.value.page+delta)}
    private fun fail(message:String){pause();state.value=state.value.copy(error=message);update()}
    private fun finish(){pause();state.value=SpeechState();stopForeground(STOP_FOREGROUND_REMOVE);getSystemService(NotificationManager::class.java).cancel(202);stopSelf()}
    private fun update(){val s=state.value
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,s.title).putString(MediaMetadata.METADATA_KEY_ARTIST,"Spotibook · Bahasa Indonesia").build())
        session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS).setState(if(s.playing)PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,PlaybackState.PLAYBACK_POSITION_UNKNOWN,s.speed).build())
        getSystemService(NotificationManager::class.java).notify(202,notification())
    }
    private fun notification():Notification{
        fun action(name:String)=PendingIntent.getService(this,name.hashCode(),Intent(this,SpeechService::class.java).setAction(name).putExtra("book",state.value.bookId).putExtra("page",state.value.page),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val s=state.value
        val open=PendingIntent.getActivity(this,202,Intent(this,MainActivity::class.java).putExtra("destination",if(s.bookId.isNotEmpty())"reader/${s.bookId}"else"home"),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session.setSessionActivity(open)
        return Notification.Builder(this,"speech").setSmallIcon(R.drawable.ic_launcher).setContentTitle(s.title.ifBlank{"Spotibook · Dengarkan buku"}).setContentText(if(s.loading)"Menyiapkan suara Indonesia…"else"Halaman / bab ${s.page+1} · ${if(s.playing)"Sedang dibacakan"else"Dijeda"}").setContentIntent(open).setOnlyAlertOnce(true).setOngoing(s.playing).setDeleteIntent(action("STOP"))
            .addAction(android.R.drawable.ic_media_previous,"Sebelumnya",action("PREVIOUS"))
            .addAction(if(s.playing)android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,if(s.playing)"Jeda"else"Putar",action(if(s.playing)"PAUSE"else"PLAY"))
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Berhenti",action("STOP"))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0,1,2)).build()
    }
    override fun onDestroy(){task?.cancel();generation++;tts.stop();tts.shutdown();state.value=state.value.copy(playing=false,loading=false);if(wake.isHeld)wake.release();audio.abandonAudioFocusRequest(focus);session.release();unregisterReceiver(noisy);scope.cancel();super.onDestroy()}
    override fun onBind(intent:Intent?)=null
}
