package com.lolauncher.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.lolauncher.ui.theme.AccentRed

data class ContextMenuItem(
    val label: String,
    val icon: ImageVector,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit
)

@Composable
fun ContextMenuDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    items: List<ContextMenuItem>,
    offset: DpOffset = DpOffset.Zero
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        offset = offset
    ) {
        items.forEach { item ->
            DropdownMenuItem(
                text = {
                    Text(
                        item.label,
                        color = if (item.destructive) AccentRed else MaterialTheme.colorScheme.onSurface
                    )
                },
                onClick = {
                    onDismiss()
                    item.onClick()
                },
                enabled = item.enabled,
                leadingIcon = {
                    Icon(
                        item.icon,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = if (item.destructive) AccentRed else MaterialTheme.colorScheme.onSurface.copy(0.7f)
                    )
                }
            )
        }
    }
}
