package com.lolauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lolauncher.ui.theme.BrandYellow
import com.lolauncher.ui.theme.LocalBrandColors
import org.jetbrains.skia.Image as SkiaImage

@Composable
fun CharacterPreview(
    previewBytes: ByteArray?,
    skinBytes: ByteArray?,
    cloakBytes: ByteArray?,
    username: String,
    modifier: Modifier = Modifier,
    label: String = "Предпросмотр"
) {
    val previewBitmap = remember(previewBytes, skinBytes) {
        toBitmap(previewBytes ?: skinBytes)
    }
    val cloakBitmap = remember(cloakBytes) { toBitmap(cloakBytes) }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                if (previewBitmap != null) {
                    Image(previewBitmap, "Персонаж $username", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else if (cloakBitmap != null) {
                    Image(cloakBitmap, "Плащ $username", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                } else {
                    Icon(Icons.Default.Person, null, tint = LocalBrandColors.current.primary, modifier = Modifier.size(64.dp))
                }
            }
            if (cloakBytes != null) {
                Spacer(Modifier.height(8.dp))
                Text("Плащ загружен", style = MaterialTheme.typography.bodySmall, color = BrandYellow)
            }
        }
    }
}

@Composable
fun SkinThumb(bytes: ByteArray?, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) { toBitmap(bytes) }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Person, null, tint = BrandYellow)
        }
    }
}

private fun toBitmap(bytes: ByteArray?): ImageBitmap? {
    if (bytes == null || bytes.size < 32) return null
    return try {
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (_: Exception) {
        null
    }
}
