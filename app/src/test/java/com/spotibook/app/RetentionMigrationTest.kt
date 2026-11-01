package com.spotibook.app

import android.app.Application
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.spotibook.app.data.*
import com.spotibook.app.domain.recentHistory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class RetentionMigrationTest {
    @Test fun oldDatabaseUpgradesWithoutLosingBooksOrReadingTime()=runBlocking {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val file=app.getDatabasePath("spotibook.db");file.parentFile!!.mkdirs()
        val schema=JSONObject(File("schemas/com.spotibook.app.data.SpotibookDatabase/1.json").readText()).getJSONObject("database")
        val old=SQLiteDatabase.openOrCreateDatabase(file,null)
        val entities=schema.getJSONArray("entities")
        for(i in 0 until entities.length()){
            val e=entities.getJSONObject(i)
            old.execSQL(e.getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")))
            val indices=e.optJSONArray("indices")?:org.json.JSONArray()
            for(j in 0 until indices.length())old.execSQL(indices.getJSONObject(j).getString("createSql").replace("${'$'}{TABLE_NAME}",e.getString("tableName")))
        }
        val setup=schema.getJSONArray("setupQueries");for(i in 0 until setup.length())old.execSQL(setup.getString(i))
        old.execSQL("INSERT INTO Book VALUES ('book','Buku lama','Penulis','PDF','book.pdf','','hash',NULL,100,17,0.4,2.0,0.17,'Reading',1,2,NULL)")
        old.execSQL("INSERT INTO Session VALUES ('run','book','Buku lama','2026-10-31',1,600001,600000,1,17,16,'Asia/Jakarta')")
        old.version=1;old.close()
        val db=SpotibookDatabase.open(app)
        try {
            val book=db.library().book("book")!!;assertEquals(17,book.position);assertEquals(2f,book.zoom)
            val record=db.library().allSessions().single();assertEquals(600000L,record.millis);assertFalse(record.historyHidden);assertEquals("",record.focusId)
        }finally{db.close()}
    }
    @Test fun historyExpiresAndCanBeRemovedWithoutChangingStatistics()=runBlocking{
        val app=ApplicationProvider.getApplicationContext<Application>();val db=SpotibookDatabase.open(app);val repo=Repository(app,db,Preferences(app));val today=LocalDate.of(2026,11,1)
        try{
            val records=(0..40).map{day->Session("s$day","book","Buku",today.minusDays(day.toLong()).toString(),1,1001,1000,0,1,zone="Asia/Jakarta")}
            records.forEach{repo.dao.putSession(it)}
            repo.expireHistory(today)
            assertEquals(30,recentHistory(repo.dao.allSessions(),today).size)
            repo.hideHistory(setOf("s0","s1"))
            val all=repo.dao.allSessions();assertEquals(28,recentHistory(all,today).size);assertEquals(41000L,all.sumOf{it.millis})
            assertTrue(all.filter{it.date<today.minusDays(29).toString()}.all{it.historyHidden})
        }finally{db.close()}
    }
}
