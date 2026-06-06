package com.lolauncher.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lolauncher.ui.theme.BrandYellow
import com.lolauncher.ui.theme.LocalBrandColors
import org.jetbrains.skia.Image as SkiaImage

data class AvatarOption(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageBytes: ByteArray?,
    val isSelected: Boolean = false,
    val isUpload: Boolean = false
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AvatarOption) return false
        return id == other.id && isSelected == other.isSelected
    }

    override fun hashCode(): Int = id.hashCode()
}

@Composable
fun AvatarPickerDialog(
    visible: Boolean,
    options: List<AvatarOption>,
    onOptionSelected: (AvatarOption) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Аватар профиля", fontWeight = FontWeight.SemiBold)
                Text(
                    "Выберите аватарку или загрузите своё фото",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 360.dp)
            ) {
                items(options, key = { it.id }) { option ->
                    AvatarOptionCard(option, onClick = { onOptionSelected(option) })
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

@Composable
private fun AvatarOptionCard(option: AvatarOption, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1f,
        animationSpec = tween(180),
        label = "avatarCardScale"
    )
    val brand = LocalBrandColors.current
    val borderColor = when {
        option.isSelected -> BrandYellow
        isHovered -> brand.primary.copy(0.7f)
        else -> MaterialTheme.colorScheme.outline.copy(0.25f)
    }

    val imageBitmap: ImageBitmap? = remember(option.imageBytes) {
        option.imageBytes?.let { bytes ->
            try {
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (option.isSelected) BrandYellow.copy(0.08f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (option.isSelected) 2.dp else 1.dp,
            borderColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .hoverable(interactionSource)
    ) {
        Column(
            Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(brand.primary.copy(0.15f), brand.primaryDark.copy(0.08f))
                        )
                    )
                    .border(2.dp, borderColor.copy(0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    imageBitmap != null -> {
                        Image(
                            imageBitmap,
                            option.title,
                            Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    option.isUpload -> {
                        Icon(Icons.Default.AddAPhoto, null, tint = BrandYellow, modifier = Modifier.size(32.dp))
                    }
                    else -> {
                        Icon(Icons.Default.Person, null, tint = brand.primary, modifier = Modifier.size(32.dp))
                    }
                }
                if (option.isSelected) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(BrandYellow),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                option.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                option.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}
