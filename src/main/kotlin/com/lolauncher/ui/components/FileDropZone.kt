package com.lolauncher.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolauncher.ui.LocalAwtWindow
import com.lolauncher.ui.theme.BrandYellow
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.swing.SwingUtilities

/**
 * Drag-and-drop файлов на окно лаунчера (без перекрытия контента).
 */
@Composable
fun FileDropZone(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onFilesDropped: (List<File>) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val isDragging = remember { mutableStateOf(false) }
    val awtWindow = LocalAwtWindow.current

    DisposableEffect(enabled, awtWindow) {
        if (!enabled || awtWindow == null) return@DisposableEffect onDispose {}
        val dropTarget = object : DropTarget() {
            override fun dragEnter(dtde: DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    SwingUtilities.invokeLater { isDragging.value = true }
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                }
            }
            override fun dragExit(dte: java.awt.dnd.DropTargetEvent) {
                SwingUtilities.invokeLater { isDragging.value = false }
            }
            override fun drop(dtde: DropTargetDropEvent) {
                SwingUtilities.invokeLater { isDragging.value = false }
                if (!dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.rejectDrop()
                    return
                }
                dtde.acceptDrop(DnDConstants.ACTION_COPY)
                @Suppress("UNCHECKED_CAST")
                val files = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
                if (!files.isNullOrEmpty()) onFilesDropped(files)
                dtde.dropComplete(true)
            }
        }
        awtWindow.dropTarget = dropTarget
        onDispose { awtWindow.dropTarget = null }
    }

    val borderColor = if (isDragging.value) BrandYellow else MaterialTheme.colorScheme.outline.copy(0.25f)

    Box(
        modifier.border(
            width = if (isDragging.value) 2.dp else 1.dp,
            color = borderColor,
            shape = RoundedCornerShape(16.dp)
        )
    ) {
        content()
    }
}
