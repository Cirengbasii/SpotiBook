package com.spotibook.app.background

import android.content.Context
import androidx.work.*
import com.spotibook.app.SpotibookApp
import java.util.concurrent.TimeUnit

class HistoryCleanup(context:Context,parameters:WorkerParameters):CoroutineWorker(context,parameters){
    override suspend fun doWork():Result{
        (applicationContext as SpotibookApp).repository.expireHistory()
        return Result.success()
    }
    companion object{
        fun schedule(context:Context){WorkManager.getInstance(context).enqueueUniquePeriodicWork("history-retention",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<HistoryCleanup>(1,TimeUnit.DAYS).build())}
    }
}
