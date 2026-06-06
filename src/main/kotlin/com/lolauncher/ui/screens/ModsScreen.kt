package com.lolauncher.ui.screens



import androidx.compose.foundation.Image

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.lazy.LazyColumn

import androidx.compose.foundation.lazy.items

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.ImageBitmap

import androidx.compose.ui.graphics.toComposeImageBitmap

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp

import com.lolauncher.data.models.ModInfo

import com.lolauncher.ui.components.FileDropZone

import com.lolauncher.ui.components.LauncherCard

import com.lolauncher.ui.components.SectionTitle

import com.lolauncher.ui.theme.BrandYellow

import com.lolauncher.viewmodel.LauncherViewModel

import org.jetbrains.skia.Image as SkiaImage



@Composable

fun ModsScreen(viewModel: LauncherViewModel) {

    var tick by remember { mutableIntStateOf(0) }

    DisposableEffect(viewModel) {

        val listener: () -> Unit = { tick++ }

        viewModel.addStateListener(listener)

        onDispose { viewModel.removeStateListener(listener) }

    }

    @Suppress("UNUSED_VARIABLE") val trigger = tick



    val mods = viewModel.mods



    Column(Modifier.fillMaxSize().padding(24.dp)) {

        Row(

            Modifier.fillMaxWidth(),

            horizontalArrangement = Arrangement.SpaceBetween,

            verticalAlignment = Alignment.CenterVertically

        ) {

            SectionTitle("Моды (${mods.size})")

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {

                FilledTonalButton(
                    onClick = { viewModel.openModrinthShaders() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = com.lolauncher.ui.theme.BrandYellow.copy(0.18f)
                    )
                ) {
                    Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp), tint = com.lolauncher.ui.theme.BrandYellow)
                    Spacer(Modifier.width(6.dp))
                    Text("Шейдеры", color = com.lolauncher.ui.theme.BrandYellow)
                }

                FilledTonalButton(onClick = { viewModel.openModrinth() }, shape = RoundedCornerShape(10.dp)) {

                    Icon(Icons.Default.Language, null, Modifier.size(18.dp))

                    Spacer(Modifier.width(6.dp))

                    Text("Modrinth")

                }

                FilledTonalButton(onClick = { viewModel.openModsFolder() }, shape = RoundedCornerShape(10.dp)) {

                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp))

                    Spacer(Modifier.width(6.dp))

                    Text("Папка mods")

                }

                FilledTonalButton(onClick = { viewModel.refreshAll() }, shape = RoundedCornerShape(10.dp)) {

                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))

                    Spacer(Modifier.width(6.dp))

                    Text("Обновить")

                }

            }

        }



        Spacer(Modifier.height(8.dp))

        Text(

            "Перетащите .jar в область ниже, скачайте моды или шейдеры на Modrinth, либо откройте папку mods.",

            style = MaterialTheme.typography.bodyMedium,

            color = MaterialTheme.colorScheme.onSurface.copy(0.6f)

        )

        Spacer(Modifier.height(16.dp))



        FileDropZone(

            modifier = Modifier.weight(1f).fillMaxWidth(),

            onFilesDropped = { viewModel.importMods(it) }

        ) {

            if (mods.isEmpty()) {

                LauncherCard(Modifier.fillMaxSize()) {

                    Column(

                        Modifier.fillMaxSize().padding(32.dp),

                        horizontalAlignment = Alignment.CenterHorizontally,

                        verticalArrangement = Arrangement.Center

                    ) {

                        Icon(Icons.Default.Extension, null, Modifier.size(48.dp),

                            tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))

                        Spacer(Modifier.height(12.dp))

                        Text("Моды не найдены", style = MaterialTheme.typography.titleMedium,

                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f), textAlign = TextAlign.Center)

                        Spacer(Modifier.height(4.dp))

                        Text("Перетащите .jar файл в эту область",

                            style = MaterialTheme.typography.bodyMedium, color = BrandYellow.copy(0.85f),

                            textAlign = TextAlign.Center)

                    }

                }

            } else {

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {

                    items(mods, key = { it.filePath }) { mod ->

                        ModListItem(mod) { viewModel.deleteMod(mod) }

                    }

                }

            }

        }

    }

}



@Composable

private fun ModListItem(mod: ModInfo, onDelete: () -> Unit) {

    var showDeleteDialog by remember { mutableStateOf(false) }

    val icon: ImageBitmap? = remember(mod.filePath, mod.iconBytes) {

        mod.iconBytes?.let { bytes ->

            try { SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap() } catch (_: Exception) { null }

        }

    }



    LauncherCard(Modifier.fillMaxWidth()) {

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {

            if (icon != null) {

                Image(icon, null, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)

            } else {

                Icon(Icons.Default.Extension, null, tint = BrandYellow, modifier = Modifier.size(48.dp))

            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {

                Text(

                    mod.displayName.ifBlank { mod.fileName },

                    style = MaterialTheme.typography.bodyLarge,

                    fontWeight = FontWeight.Medium,

                    maxLines = 1,

                    overflow = TextOverflow.Ellipsis

                )

                if (mod.version.isNotBlank()) {

                    Text("v${mod.version}", style = MaterialTheme.typography.bodySmall, color = BrandYellow)

                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                    Text(mod.modLoader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)

                    Text(formatFileSize(mod.fileSize), style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f))

                }

                if (mod.description.isNotBlank()) {

                    Text(mod.description, style = MaterialTheme.typography.bodySmall,

                        color = MaterialTheme.colorScheme.onSurface.copy(0.55f), maxLines = 2, overflow = TextOverflow.Ellipsis)

                }

            }

            IconButton(onClick = { showDeleteDialog = true }) {

                Icon(Icons.Default.Delete, "Удалить", tint = MaterialTheme.colorScheme.error.copy(0.7f))

            }

        }

    }



    if (showDeleteDialog) {

        AlertDialog(

            onDismissRequest = { showDeleteDialog = false },

            title = { Text("Удалить мод?") },

            text = { Text("Удалить ${mod.displayName}?") },

            confirmButton = {

                TextButton(onClick = { onDelete(); showDeleteDialog = false }) {

                    Text("Удалить", color = MaterialTheme.colorScheme.error)

                }

            },

            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Отмена") } }

        )

    }

}



private fun formatFileSize(bytes: Long): String = when {

    bytes < 1024 -> "$bytes Б"

    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"

    else -> String.format("%.1f МБ", bytes / (1024.0 * 1024.0))

}


