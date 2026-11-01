package com.spotibook.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spotibook.app.data.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun HistoryRow(session:Session,model:AppState,open:(String)->Unit,remove:()->Unit) {
    val books by model.books.collectAsStateWithLifecycle();val book=books.firstOrNull{it.id==session.bookId}
    ListItem(headlineContent={Text(session.bookTitle,maxLines=2,overflow=TextOverflow.Ellipsis)},
        leadingContent={if(book!=null)BookCover(book,model,Modifier.width(46.dp).height(68.dp),thumbnail=true)else Icon(Icons.Outlined.MenuBook,null,Modifier.size(40.dp),tint=MaterialTheme.colorScheme.primary)},
        supportingContent={Text("${dateLabel(session.start)} · ${minutes(session.millis)}${if(book==null)" · Buku dihapus"else""}",style=MaterialTheme.typography.bodySmall)},
        trailingContent={IconButton(remove){Icon(Icons.Outlined.DeleteOutline,"Hapus riwayat ${session.bookTitle}")}},
        modifier=Modifier.fillMaxWidth().clickable(enabled=book!=null){model.task{model.repo.position(session.bookId,session.endPosition,0f);withContext(Dispatchers.Main){open(session.bookId)}}},
        colors=ListItemDefaults.colors(containerColor=MaterialTheme.colorScheme.surface))
}
