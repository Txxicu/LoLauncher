package com.lolauncher.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lolauncher.data.models.LaunchLogEntry
import com.lolauncher.ui.theme.AccentRed
import com.lolauncher.ui.theme.BrandYellow
import com.lolauncher.util.LaunchLogBuffer
import com.lolauncher.util.LogService
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser

@Composable
fun LaunchConsoleDialog(
    visible: Boolean,
    entries: List<LaunchLogEntry>,
    errorHighlight: String? = null,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollToItem(entries.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.width(900.dp).height(520.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Консоль запуска Minecraft", style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, "Закрыть")
                    }
                }

                if (errorHighlight != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorHighlight, color = AccentRed, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { LaunchLogBuffer.clear() }) {
                        Icon(Icons.Default.DeleteSweep, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Очистить")
                    }
                    FilledTonalButton(onClick = { copyToClipboard(LaunchLogBuffer.asText()) }) {
                        Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Скопировать")
                    }
                    FilledTonalButton(onClick = { saveLogToFile(LaunchLogBuffer.asText()) }) {
                        Icon(Icons.Default.Save, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Сохранить")
                    }
                    FilledTonalButton(onClick = { openLogsFolder() }) {
                        Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Папка логов")
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(entries, key = { "${it.timestamp}_${it.message.hashCode()}" }) { entry ->
                        val color = when (entry.level) {
                            "ERROR" -> AccentRed
                            "WARN" -> BrandYellow
                            "GAME" -> MaterialTheme.colorScheme.onSurface.copy(0.85f)
                            else -> MaterialTheme.colorScheme.onSurface.copy(0.7f)
                        }
                        Text(
                            "[${entry.timestamp}] [${entry.level}] ${entry.message}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = color
                        )
                    }
                }
            }
        }
    }
}

private fun copyToClipboard(text: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
}

private fun saveLogToFile(text: String) {
    val chooser = JFileChooser()
    chooser.selectedFile = File("minecraft-launch.log")
    if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
        chooser.selectedFile.writeText(text)
    }
}

private fun openLogsFolder() {
    try {
        Desktop.getDesktop().open(File(LogService.getLogsDirectory()))
    } catch (_: Exception) {
    }
}
