package com.spotibook.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.spotibook.app.domain.Reading
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal fun weekMonday(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value-1).toLong())

@Composable internal fun StreakCalendar(daily:Map<LocalDate,Long>,today:LocalDate,minimum:Int,tap:(LocalDate)->Unit,pin:()->Unit) {
    val streak=Reading.streak(daily,today,minimum)
    val current=weekMonday(today)
    val first=weekMonday(minOf(daily.keys.filter { it<=today }.minOrNull()?:today,today.minusWeeks(52)))
    val weeks=ChronoUnit.WEEKS.between(first,current).toInt()+1
    var visible by rememberSaveable { mutableStateOf(current.toString()) }
    Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.LocalFireDepartment,null,Modifier.size(40.dp),tint=ReadingCoral)
                Column { Text("${streak.current} hari beruntun",style=MaterialTheme.typography.titleLarge);Text("Terpanjang ${streak.longest} hari",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            key(first) {
                val pager=rememberPagerState(initialPage=ChronoUnit.WEEKS.between(first,LocalDate.parse(visible)).toInt().coerceIn(0,weeks-1)){weeks}
                val scope=rememberCoroutineScope()
                LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.distinctUntilChanged().collect { visible=first.plusWeeks(it.toLong()).toString() } }
                val monday=first.plusWeeks(pager.currentPage.toLong())
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    IconButton({scope.launch{pager.animateScrollToPage((pager.currentPage-1).coerceAtLeast(0))}},enabled=pager.currentPage>0){Icon(Icons.Outlined.ChevronLeft,"Minggu sebelumnya")}
                    Text("${monday.format(DateTimeFormatter.ofPattern("d MMM",Indonesian))} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM yyyy",Indonesian))}",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton({scope.launch{pager.animateScrollToPage((pager.currentPage+1).coerceAtMost(weeks-1))}},enabled=pager.currentPage<weeks-1){Icon(Icons.Outlined.ChevronRight,"Minggu berikutnya")}
                }
                HorizontalPager(pager,modifier=Modifier.fillMaxWidth().testTag("streak-pager"),key={first.plusWeeks(it.toLong()).toString()}) { index->
                    val start=first.plusWeeks(index.toLong())
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { day->
                            val date=start.plusDays(day.toLong());val qualified=date<=today&&(daily[date]?:0)>=minimum*60_000L
                            Column(Modifier.weight(1f).clickable{tap(date)}.padding(horizontal=2.dp).semantics(mergeDescendants=true){contentDescription="${date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy",Indonesian))}: ${if(qualified)"target tercapai"else"target belum tercapai"}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(date.format(DateTimeFormatter.ofPattern("EEE",Indonesian)),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Surface(shape=RoundedCornerShape(16.dp),color=if(qualified)MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,border=if(date==today)BorderStroke(1.5.dp,MaterialTheme.colorScheme.primary)else null,modifier=Modifier.fillMaxWidth().height(63.dp)) {
                                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                        Text("${date.dayOfMonth}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurface)
                                        if(qualified) Icon(Icons.Outlined.LocalFireDepartment,"Api membaca",Modifier.size(26.dp),tint=ReadingCoral)
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
                    TextButton({scope.launch{pager.animateScrollToPage(weeks-1)}}){Text("Hari ini")}
                    FilledTonalButton(pin,Modifier.heightIn(min=48.dp)){Icon(Icons.Outlined.Widgets,null,Modifier.size(20.dp));Spacer(Modifier.width(6.dp));Text("Pasang widget")}
                }
            }
        }
    }
}
