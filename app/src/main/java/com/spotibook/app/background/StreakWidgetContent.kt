package com.spotibook.app.background

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.*
import androidx.datastore.preferences.core.*
import androidx.glance.*
import androidx.glance.action.*
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.*
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.glance.unit.ColorProvider
import com.spotibook.app.MainActivity
import com.spotibook.app.R
import com.spotibook.app.ui.Indonesian
import java.time.*
import java.time.format.DateTimeFormatter

internal val StreakWeekOffset = intPreferencesKey("streak_week_offset")
private val WeekDelta = ActionParameters.Key<Int>("week_delta")

class ChangeStreakWeek : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val delta=parameters[WeekDelta]?.takeIf{it in -1..1}?:return
        updateAppWidgetState(context,glanceId) { state ->
            val next=(state[StreakWeekOffset]?:0).toLong()+delta
            state[StreakWeekOffset]=next.coerceIn(Int.MIN_VALUE.toLong(),0L).toInt()
        }
        StreakWidget().update(context,glanceId)
    }
}

@Composable internal fun StreakWidgetContent(context:Context,today:LocalDate,daily:Map<LocalDate,Long>,minimum:Int,current:Int,longest:Int) {
    val white=ColorProvider(Color(0xFFFFFCF6));val gold=ColorProvider(Color(0xFFF3C64B))
    val small=LocalSize.current.height<160.dp||LocalSize.current.width<240.dp
    val compact=LocalSize.current.height<210.dp||small
    val offset=(currentState<Preferences>()[StreakWeekOffset]?:0).coerceAtMost(0)
    val monday=today.minusDays((today.dayOfWeek.value-1).toLong()).plusWeeks(offset.toLong())
    val home=actionStartActivity(Intent(context,MainActivity::class.java).putExtra("destination","home"))
    Column(GlanceModifier.fillMaxSize().background(Color(0xFF2F7299)).cornerRadius(24.dp).padding(if(small)8.dp else 12.dp)) {
        if(!compact)Text("SPOTIBOOK",GlanceModifier.clickable(home),style=TextStyle(color=gold,fontSize=11.sp,fontWeight=FontWeight.Bold))
        Row(verticalAlignment=Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_streak_flame),null,GlanceModifier.size(if(small)20.dp else 28.dp))
            Spacer(GlanceModifier.width(6.dp))
            Text("$current hari beruntun",GlanceModifier.clickable(home),style=TextStyle(color=white,fontSize=if(small)16.sp else if(compact)20.sp else 24.sp,fontWeight=FontWeight.Bold))
        }
        if(!compact)Text("Terpanjang $longest hari",style=TextStyle(color=white,fontSize=12.sp))
        Row(GlanceModifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.ic_week_previous),"Minggu sebelumnya",GlanceModifier.size(if(small)22.dp else 32.dp).padding(if(small)3.dp else 7.dp).clickable(actionRunCallback<ChangeStreakWeek>(actionParametersOf(WeekDelta to -1))))
            Text("${monday.format(DateTimeFormatter.ofPattern("d MMM",Indonesian))} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM",Indonesian))}",GlanceModifier.defaultWeight(),style=TextStyle(color=white,fontSize=if(small)10.sp else 12.sp,textAlign=TextAlign.Center))
            Image(ImageProvider(R.drawable.ic_week_next),"Minggu berikutnya",GlanceModifier.size(if(small)22.dp else 32.dp).padding(if(small)3.dp else 7.dp).clickable(actionRunCallback<ChangeStreakWeek>(actionParametersOf(WeekDelta to 1))))
        }
        Row(GlanceModifier.fillMaxWidth()) {
            repeat(7){ day ->
                val date=monday.plusDays(day.toLong());val qualified=date<=today&&(daily[date]?:0)>=minimum*60_000L
                Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(Intent(context,MainActivity::class.java).putExtra("destination","calendar?day=$date"))),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(date.format(DateTimeFormatter.ofPattern("EEE",Indonesian)),style=TextStyle(color=white,fontSize=if(small)8.sp else 10.sp))
                    Spacer(GlanceModifier.height(3.dp))
                    Text("${date.dayOfMonth}",style=TextStyle(color=if(date==today)gold else white,fontSize=if(small)11.sp else 13.sp,fontWeight=FontWeight.Bold))
                    if(qualified)Image(ImageProvider(R.drawable.ic_streak_flame),"Api membaca $date",GlanceModifier.size(if(small)15.dp else 22.dp))
                    else Spacer(GlanceModifier.height(if(small)15.dp else 22.dp))
                }
            }
        }
    }
}
