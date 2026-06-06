package com.lolauncher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lolauncher.data.api.MojangApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream

/**
 * Компонент для отображения анимированного GIF.
 * Поддерживает отключение анимации через настройку.
 */
data class GifFrame(
    val bitmap: ImageBitmap,
    val delayMs: Int
)

/**
 * Загружает и отображает GIF по URL.
 */
@Composable
fun AnimatedGifImage(
    url: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    contentDescription: String? = null,
    animationsEnabled: Boolean = true
) {
    var frames by remember(url) { mutableStateOf<List<GifFrame>>(emptyList()) }
    var frameIndex by remember(url) { mutableIntStateOf(0) }
    var isLoading by remember(url) { mutableStateOf(true) }
    var hasError by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        isLoading = true
        hasError = false
        frames = emptyList()
        try {
            val decoded = withContext(Dispatchers.IO) { decodeGif(url) }
            frames = decoded
            if (decoded.isEmpty()) hasError = true
        } catch (_: Exception) {
            hasError = true
        }
        isLoading = false
    }

    LaunchedEffect(frames, animationsEnabled) {
        if (!animationsEnabled || frames.size <= 1) {
            frameIndex = 0
            return@LaunchedEffect
        }
        while (true) {
            val delayMs = frames.getOrNull(frameIndex)?.delayMs ?: 100
            delay(delayMs.coerceAtLeast(20).toLong())
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(size * 0.6f),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
            hasError || frames.isEmpty() -> Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.8f)
            )
            else -> {
                val index = if (animationsEnabled) frameIndex else 0
                Image(
                    bitmap = frames[index].bitmap,
                    contentDescription = contentDescription,
                    modifier = Modifier.size(size),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

/**
 * Декодирует GIF из URL в список кадров.
 */
private fun decodeGif(url: String): List<GifFrame> {
    val bytes = MojangApi.downloadFile(url)
    return decodeGifBytes(bytes)
}

/**
 * Декодирует GIF из байтов в список кадров.
 */
fun decodeGifBytes(bytes: ByteArray): List<GifFrame> {
    return try {
        val input: ImageInputStream = ImageIO.createImageInputStream(ByteArrayInputStream(bytes))
        val readers: Iterator<ImageReader> = ImageIO.getImageReadersByFormatName("gif")
        if (!readers.hasNext()) {
            val bitmap = SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            return listOf(GifFrame(bitmap, 100))
        }

        val reader = readers.next()
        reader.input = input
        val frames = mutableListOf<GifFrame>()
        val count = reader.getNumImages(true)

        for (i in 0 until count) {
            val image = reader.read(i)
            val stream = java.io.ByteArrayOutputStream()
            ImageIO.write(image, "png", stream)
            val bitmap = SkiaImage.makeFromEncoded(stream.toByteArray()).toComposeImageBitmap()
            frames.add(GifFrame(bitmap, 100))
            image.flush()
        }

        reader.dispose()
        input.close()
        frames.ifEmpty { listOf(GifFrame(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap(), 100)) }
    } catch (_: Exception) {
        listOf(GifFrame(SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap(), 100))
    }
}

