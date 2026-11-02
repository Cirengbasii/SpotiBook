package com.spotibook.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.domain.Reading
import com.spotibook.app.domain.recentHistory
import java.time.*

@Composable fun HomeScreen(model:AppState,open:(String)->Unit,import:()->Unit,navigate:(String)->Unit){
    val books by model.books.collectAsStateWithLifecycle();val sessions by model.sessions.collectAsStateWithLifecycle()
    val settings by model.settings.collectAsStateWithLifecycle();val focus by model.focus.collectAsStateWithLifecycle()
    var today by remember{mutableStateOf(LocalDate.now())};var hour by remember{mutableIntStateOf(LocalTime.now().hour)}
    var selected by rememberSaveable{mutableStateOf(LocalDate.now().toString())};var remove by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(Unit){while(true){today=LocalDate.now();hour=LocalTime.now().hour;kotlinx.coroutines.delay(30_000)}}
    val daily=remember(sessions){sessions.groupBy{LocalDate.parse(it.date)}.mapValues{(_,r)->r.sumOf{it.millis}}}
    val streak=Reading.streak(daily,today,settings.qualifyingMinutes)
    val current=books.firstOrNull{it.status=="Reading"}?:books.firstOrNull{it.status=="To read"}?:books.firstOrNull()
    val dark=MaterialTheme.colorScheme.background.luminance()<.2
    val navy=if(dark)Color(0xFFB7CBFF)else Color(0xFF173E82)
    LazyColumn(Modifier.fillMaxSize().testTag("home-list"),contentPadding=PaddingValues(horizontal=20.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
        item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text(buildAnnotatedString{withStyle(SpanStyle(color=ReadingCoral)){append("Spoti")};withStyle(SpanStyle(color=navy)){append("book")}},Modifier.weight(1f),style=MaterialTheme.typography.headlineLarge)
            IconButton(import){Icon(Icons.Outlined.Add,"Impor buku")}
            FilledIconButton({navigate("settings")},colors=IconButtonDefaults.filledIconButtonColors(containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=navy)){Icon(Icons.Outlined.Settings,"Pengaturan")}
        }}
        item{Box(Modifier.fillMaxWidth()){
            SunriseArt(Modifier.align(Alignment.BottomEnd).size(110.dp,72.dp))
            Column(Modifier.fillMaxWidth().padding(top=4.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text(when(hour){in 5..10->"Selamat pagi";in 11..14->"Selamat siang";in 15..17->"Selamat sore";else->"Selamat malam"},style=MaterialTheme.typography.headlineMedium)
                Text("Semoga hari ini jadi halaman yang baik.",Modifier.fillMaxWidth(.72f),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }}
        item{Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(24.dp),color=if(dark)Color(0xFF8B3D3B)else Color(0xFFF65D53),contentColor=Color.White){Box{
            PetalArt(Modifier.matchParentSize())
            Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
                Text("Lanjut membaca",style=MaterialTheme.typography.titleLarge)
                if(current==null){
                    Text("Tambahkan buku pertama",style=MaterialTheme.typography.titleMedium)
                    Text("Pilih PDF, EPUB, TXT, atau HTML dari perangkat.")
                    Button(import,Modifier.fillMaxWidth().heightIn(min=52.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF173E82),contentColor=Color.White)){Icon(Icons.Outlined.Add,null);Spacer(Modifier.width(8.dp));Text("Impor buku")}
                }else BoxWithConstraints(Modifier.fillMaxWidth()){
                    val availableWidth=maxWidth
                    val compact=maxWidth<280.dp||LocalDensity.current.fontScale>1.15f
                    val action:@Composable ()->Unit={Button({open(current.id)},Modifier.fillMaxWidth().heightIn(min=52.dp),shape=RoundedCornerShape(17.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF173E82),contentColor=Color.White)){
                        Icon(Icons.Outlined.MenuBook,null,Modifier.size(21.dp));Spacer(Modifier.width(7.dp));Text("Lanjutkan",Modifier.weight(1f),style=MaterialTheme.typography.labelLarge);Icon(Icons.Outlined.ChevronRight,null,Modifier.size(20.dp))
                    }}
                    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(15.dp)){
                            val coverWidth=minOf(156.dp,availableWidth*.34f)
                            Box(Modifier.width(coverWidth).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha=.8f)).padding(3.dp)){
                                BookCover(current,model,Modifier.fillMaxWidth().height(coverWidth*1.5f).clickable{open(current.id)})
                            }
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(9.dp)){
                                Text(current.title,style=MaterialTheme.typography.titleLarge,maxLines=4,overflow=TextOverflow.Ellipsis)
                                Text(current.author.labelId(),style=MaterialTheme.typography.bodyMedium,maxLines=2)
                                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)){
                                    LinearProgressIndicator({current.progress},Modifier.weight(1f).height(7.dp),color=Color.White,trackColor=Color.White.copy(alpha=.28f))
                                    Text("${(current.progress*100).toInt()}%",style=MaterialTheme.typography.bodyMedium)
                                }
                                Text("${current.position+1} / ${current.total} ${if(current.format=="PDF")"halaman"else"bab"}",style=MaterialTheme.typography.bodyMedium)
                                if(!compact)action()
                            }
                        }
                        if(compact)action()
                    }
                }
            }
        }}}
        item{Surface(onClick={navigate("focus")},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.primaryContainer){
            Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(13.dp)){
                Box(Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha=.09f),CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Timer,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(27.dp))}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
                    Text("Fokus ${if(focus.plannedMillis>0)focus.plannedMillis/60_000 else 25} menit",style=MaterialTheme.typography.titleMedium)
                    Text(if(focus.running)"Sesi fokus sedang berjalan"else"Baca lebih konsisten setiap hari.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.ChevronRight,"Waktu fokus")
            }
        }}
        item{Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))){Column{
            Row(Modifier.padding(start=18.dp,end=18.dp,top=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer,CircleShape),contentAlignment=Alignment.Center){Icon(Icons.Outlined.LocalFireDepartment,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(29.dp))}
                Column{Text("${streak.current} hari beruntun",style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary);Text("Kebiasaan baik, satu halaman setiap hari.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            ReadingCalendar(model,open,LocalDate.parse(selected)){selected=it.toString()}
        }}}
        item{Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerLow){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("Bacaan hari ini",style=MaterialTheme.typography.titleMedium);val millis=daily[today]?:0L
            Text("${millis/60_000} dari ${settings.dailyMinutes} menit",color=MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator({Reading.goal(millis,settings.dailyMinutes)},Modifier.fillMaxWidth())
        }}}
        item{StreakCalendar(daily,today,settings.qualifyingMinutes,{navigate("calendar?day=$it")}){if(!com.spotibook.app.background.Widgets.pinStreak(model.app))model.messages.tryEmit("Tambahkan dari menu Widget di layar utama, lalu pilih Spotibook · Hari beruntun.")}}
        val queue=books.filter{it.status=="To read"&&it.id!=current?.id}.take(8)
        if(queue.isNotEmpty()){
            item{SectionTitle("Buku untuk nanti","Pustaka"){navigate("library")}}
            item{LazyRow(horizontalArrangement=Arrangement.spacedBy(14.dp)){items(queue,key={it.id}){book->Column(Modifier.width(100.dp).clickable{open(book.id)},verticalArrangement=Arrangement.spacedBy(6.dp)){BookCover(book,model,Modifier.size(100.dp,145.dp));Text(book.title,maxLines=2,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.labelMedium)}}}}
        }
        item{Box(Modifier.clickable{navigate("history")}){SectionTitle("Riwayat terbaru","Lihat semua"){navigate("history")}}}
        val recent=recentHistory(sessions,today).take(3)
        if(recent.isEmpty())item{Text("Sesi membaca Anda akan tercatat di sini.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        else items(recent,key={it.id}){record->HistoryRow(record,model,open){remove=record.id}}
    }
    remove?.let{id->Confirm("Hapus dari riwayat?","Entri ini hilang dari daftar riwayat. Statistik dan kalender membaca tetap tersimpan.","Hapus",{remove=null}){remove=null;model.task{model.repo.hideHistory(setOf(id))}}}
}

@Composable private fun SunriseArt(modifier:Modifier){Canvas(modifier){
    val gold=Color(0xFFFFD174);val peach=Color(0xFFFFE8D8)
    drawCircle(gold,size.height*.43f,Offset(size.width*.61f,size.height*.72f))
    drawOval(peach,Offset(size.width*.05f,size.height*.56f),Size(size.width*.58f,size.height*.7f))
    drawOval(Color(0xFFFFEFE7),Offset(size.width*.61f,size.height*.36f),Size(size.width*.39f,size.height*.9f))
    for(i in 0..2){val x=size.width*(.37f+i*.19f);drawLine(gold,Offset(x,2f),Offset(x+4f,12f),strokeWidth=4f,cap=StrokeCap.Round)}
}}
@Composable private fun PetalArt(modifier:Modifier){Canvas(modifier){
    for(side in 0..1){val x=if(side==0)size.width*.93f else size.width*.04f;val base=size.height*(if(side==0).55f else .86f)
        val stem=Path().apply{moveTo(x,base+80f);quadraticTo(x-16f,base+20f,x+5f,base-80f)}
        drawPath(stem,Color(0xFFB73631).copy(alpha=.2f),style=androidx.compose.ui.graphics.drawscope.Stroke(2f))
        for(i in 0..3){val y=base+45f-i*32f;val direction=if(i%2==0)-1 else 1
            val leaf=Path().apply{moveTo(x,y);quadraticTo(x+direction*45f,y-4f,x+direction*30f,y-48f);quadraticTo(x+direction*2f,y-42f,x,y);close()}
            drawPath(leaf,if(side==0)Color(0xFFAD302D).copy(alpha=.2f)else Color.White.copy(alpha=.12f))
        }
    }
}}
