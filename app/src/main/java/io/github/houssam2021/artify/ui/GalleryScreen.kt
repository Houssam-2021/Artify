package io.github.houssam2021.artify.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.houssam2021.artify.AppViewModel
import io.github.houssam2021.artify.io.DocumentMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun GalleryScreen(vm: AppViewModel) {
    var showNew by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentMeta?>(null) }
    var deleteTarget by remember { mutableStateOf<DocumentMeta?>(null) }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Artify", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("${vm.docs.size} artwork${if (vm.docs.size == 1) "" else "s"}", color = Muted, fontSize = 13.sp)
                }
                TextButton(onClick = { importer.launch(arrayOf("application/octet-stream", "*/*")) }) {
                    Icon(Icons.Rounded.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Import")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { showNew = true }) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("New")
                }
            }
            if (vm.docs.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Brush, null, Modifier.size(72.dp), tint = Accent)
                    Spacer(Modifier.height(16.dp))
                    Text("Your canvas is waiting", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("Infinite canvas · zoom forever · paint · animate", color = Muted)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showNew = true }) { Text("Create your first artwork") }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(vm.docs, key = { it.id }) { meta ->
                        ArtworkCard(vm, meta,
                            onRename = { renameTarget = meta },
                            onDelete = { deleteTarget = meta })
                    }
                }
            }
        }

        if (vm.loading) {
            Box(Modifier.fillMaxSize().background(Color(0x88000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        vm.message?.let { msg ->
            LaunchedEffect(msg) { delay(3000); vm.message = null }
            Toast(msg, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(24.dp))
        }
    }

    if (showNew) NewArtworkDialog(onDismiss = { showNew = false }) { name, bg -> showNew = false; vm.create(name, bg) }
    renameTarget?.let { t ->
        TextInputDialog("Rename artwork", t.name, onDismiss = { renameTarget = null }) { renameTarget = null; vm.rename(t.id, it) }
    }
    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete “${t.name}”?") },
            text = { Text("This permanently deletes the artwork from this device. It cannot be undone.") },
            confirmButton = { TextButton(onClick = { deleteTarget = null; vm.delete(t.id) }) { Text("Delete", color = Color(0xFFFF6B6B)) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ArtworkCard(vm: AppViewModel, meta: DocumentMeta, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val thumb by produceState<android.graphics.Bitmap?>(null, meta.id, vm.thumbVersion) {
        value = withContext(Dispatchers.IO) { vm.repo.loadThumb(meta.id) }
    }
    Column {
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2A2A30))
                .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                .combinedClickable(onClick = { vm.open(meta.id) }, onLongClick = { menu = true }),
            contentAlignment = Alignment.Center,
        ) {
            thumb?.let {
                Image(it.asImageBitmap(), meta.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } ?: Icon(Icons.Rounded.Brush, null, tint = Muted)
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Open") }, onClick = { menu = false; vm.open(meta.id) })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; vm.duplicate(meta.id) })
                DropdownMenuItem(text = { Text("Share .artify file") }, onClick = { menu = false; vm.share(meta.id, meta.name) })
                DropdownMenuItem(text = { Text("Delete", color = Color(0xFFFF6B6B)) }, onClick = { menu = false; onDelete() })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(meta.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(meta.modified)), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun NewArtworkDialog(onDismiss: () -> Unit, onCreate: (String, Int) -> Unit) {
    var name by remember { mutableStateOf("Untitled") }
    val papers = listOf(0xFFFFFFFF.toInt(), 0xFFF4EBD9.toInt(), 0xFFE8E8E8.toInt(), 0xFF2B2B2B.toInt(), 0xFF0E0E10.toInt())
    var bg by remember { mutableStateOf(papers[0]) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New artwork") },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("Name") }, singleLine = true)
                Spacer(Modifier.height(16.dp))
                Text("Paper", color = Muted, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    papers.forEach { c ->
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                                .border(if (c == bg) 3.dp else 1.dp, if (c == bg) Accent else Color(0x44FFFFFF), CircleShape)
                                .clickable { bg = c }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Every artwork is an infinite canvas — pan and zoom without limits.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, bg) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(title: String, initial: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it.take(60) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun Toast(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(22.dp)).background(Color(0xF02C2C31))
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) { Text(text, color = ChromeFg, fontSize = 14.sp) }
}
