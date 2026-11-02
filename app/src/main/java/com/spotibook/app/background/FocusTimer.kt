package com.spotibook.app.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.*
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import androidx.work.*
import com.spotibook.app.SpotibookApp
import com.spotibook.app.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.TimeUnit

class FocusTimer(private val context:Context,private val repo:Repository,private val scope:CoroutineScope,private val tracker:SessionTracker){
    private val gate=Mutex();private var ticking:Job?=null
    @Volatile private var delivery:Job?=null
    suspend fun awaitAlert(){delivery?.join()}
    private val alarms get()=context.getSystemService(AlarmManager::class.java)
    private fun boot()=Settings.Global.getInt(context.contentResolver,Settings.Global.BOOT_COUNT,-1)
    fun remaining(state:FocusState):Long=if(state.running&&state.boot==boot())(state.deadlineElapsed-SystemClock.elapsedRealtime()).coerceAtLeast(0)else state.remainingMillis
    fun exactAllowed()=Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms()
    private fun pending(id:String)=PendingIntent.getBroadcast(context,101,Intent(context,FocusAlarmReceiver::class.java).setAction("com.spotibook.app.FOCUS_END").putExtra("focusId",id),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun start(minutes:Int,bookId:String?){
        FocusAudio.stop()
        gate.withLock{
            require(minutes in 1..240)
            val millis=minutes*60_000L
            val s=FocusState(UUID.randomUUID().toString(),bookId,millis,millis,SystemClock.elapsedRealtime()+millis,boot(),true,started=System.currentTimeMillis())
            tracker.focusBoundary(s.id);repo.preferences.setFocus(s);schedule(s)
        }
    }
    suspend fun pause(){
        val finished=gate.withLock{
            val s=repo.preferences.focus.first();if(!s.running)return@withLock false
            val left=remaining(s)
            if(left==0L)finish(s)else{tracker.focusBoundary("");repo.preferences.setFocus(s.copy(running=false,remainingMillis=left));cancel(s.id);false}
        }
        if(finished)delivery=scope.launch { alert() }
    }
    suspend fun resume()=gate.withLock{
        val s=repo.preferences.focus.first();if(s.done||s.remainingMillis<=0||s.running)return@withLock
        val next=s.copy(running=true,boot=boot(),deadlineElapsed=SystemClock.elapsedRealtime()+s.remainingMillis)
        tracker.focusBoundary(next.id);repo.preferences.setFocus(next);schedule(next)
    }
    suspend fun stop(){FocusAudio.stop();gate.withLock{val s=repo.preferences.focus.first();tracker.focusBoundary("");cancel(s.id);repo.preferences.setFocus(FocusState())}}
    suspend fun recover(expectedId:String?=null){
        val completed=gate.withLock{
            val s=repo.preferences.focus.first()
            if(expectedId!=null&&s.id!=expectedId)return@withLock false
            if(!s.running)return@withLock false
            if(s.boot!=boot()){tracker.focusBoundary("");repo.preferences.setFocus(s.copy(running=false));cancel(s.id);false}
            else if(remaining(s)==0L)finish(s)
            else{schedule(s);false}
        }
        if(completed)delivery=scope.launch { alert() }
    }
    private suspend fun finish(s:FocusState):Boolean{
        if(s.done||s.id.isEmpty())return false
        tracker.focusBoundary("")
        val time=System.currentTimeMillis()
        repo.dao.putFocus(FocusRecord(s.id,s.bookId,s.plannedMillis,time))
        repo.preferences.setFocus(s.copy(done=true,running=false,remainingMillis=0,finished=time))
        alarms.cancel(pending(s.id))
        repo.unlockAchievements();return true
    }
    private suspend fun alert(){
        val prefs=repo.preferences.settings.first()
        if(prefs.focusNotification&&Notifications.allowed(context)){
            Notifications.show(context,101,"focus_complete","Sesi fokus selesai","Waktunya beristirahat. Waktu membaca Anda sudah tercatat saat buku terbuka.","focus")

        }
        // In-app sound is independent of Android notification permission.
        FocusAudio.play(context,prefs)
        Widgets.update(context)
    }
    private fun cancel(id:String){ticking?.cancel();ticking=null;alarms.cancel(pending(id));WorkManager.getInstance(context).cancelUniqueWork("focus-timer")}
    private fun schedule(s:FocusState){
        val left=remaining(s)
        ticking?.cancel();ticking=scope.launch{delay(left);recover(s.id)}
        val action=pending(s.id)
        try{
            if(exactAllowed())alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,s.deadlineElapsed,action)
            else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,s.deadlineElapsed,action)
        }catch(_:SecurityException){alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,s.deadlineElapsed,action)}
        WorkManager.getInstance(context).enqueueUniqueWork("focus-timer",ExistingWorkPolicy.REPLACE,OneTimeWorkRequestBuilder<FocusWorker>().setInitialDelay(left,TimeUnit.MILLISECONDS).build())
    }
}
class FocusAlarmReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        val result=goAsync();val app=context.applicationContext as SpotibookApp
        app.scope.launch{try{app.focus.recover(intent.getStringExtra("focusId"));app.focus.awaitAlert()}finally{result.finish()}}
    }
}
class FocusWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){
    override suspend fun doWork():Result{val focus=(applicationContext as SpotibookApp).focus;focus.recover();focus.awaitAlert();return Result.success()}
}
