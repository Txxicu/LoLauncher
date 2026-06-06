package com.lolauncher.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lolauncher.data.models.DownloadProgress
import com.lolauncher.data.models.DownloadStatus
import com.lolauncher.data.models.LauncherOnGameLaunch
import com.lolauncher.data.models.LaunchStatus
import com.lolauncher.BuildConfig
import com.lolauncher.ui.theme.*
import com.lolauncher.util.LogService
import org.jetbrains.skia.Image as SkiaImage

/** URL GIF-иконки лаунчера */
const val LAUNCHER_GIF_URL = "https://super-web.42web.io/emoji/happy-cat.gif"

/**
 * Переиспользуемые UI-компоненты лаунчера с жёлтой фирменной темой.
 */

/** Карточка с закруглёнными углами */
@Composable
fun LauncherCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    val cardModifier = modifier.shadow(6.dp, shape, ambientColor = BrandYellow.copy(alpha = 0.08f))

    if (onClick != null) {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            onClick = onClick
        ) {
            Column(Modifier.padding(18.dp), content = content)
        }
    } else {
        Card(
            modifier = cardModifier,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(18.dp), content = content)
        }
    }
}

/** Кнопка «Играть» */
@Composable
fun PlayButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    isRunning: Boolean = false,
    animationsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val brand = LocalBrandColors.current
    val scale = if (animationsEnabled && isRunning) {
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
            label = "scale"
        ).value
    } else 1f

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(56.dp)
            .then(if (scale != 1f) Modifier.graphicsLayer(scaleX = scale, scaleY = scale) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning) AccentOrange else brand.primary,
            contentColor = brand.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        Icon(
            if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            null,
            Modifier.size(28.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (isRunning) "ОСТАНОВИТЬ" else "ИГРАТЬ",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

/** Кнопка «Установить» */
@Composable
fun InstallButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    isInstalling: Boolean = false,
    modifier: Modifier = Modifier
) {
    val brand = LocalBrandColors.current
    Button(
        onClick = onClick,
        enabled = enabled && !isInstalling,
        modifier = modifier.height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = brand.primaryDark,
            contentColor = brand.onPrimary
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
    ) {
        if (isInstalling) {
            CircularProgressIndicator(
                Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = brand.onPrimary
            )
        } else {
            Icon(Icons.Default.Download, null, Modifier.size(28.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (isInstalling) "УСТАНОВКА..." else "УСТАНОВИТЬ",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

/** Боковая кнопка навигации */
@Composable
fun NavButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val brand = LocalBrandColors.current
    val bgColor = if (isSelected) brand.selectedBg else Color.Transparent

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = bgColor
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, label,
                tint = if (isSelected) brand.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isSelected) brand.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

/** Аватар игрока */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PlayerAvatar(
    skinBytes: ByteArray?,
    username: String,
    size: Int = 80,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onSecondaryClick: (() -> Unit)? = null
) {
    val imageBitmap: ImageBitmap? = remember(skinBytes) {
        skinBytes?.let { bytes ->
            try {
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    val shape = CircleShape
    val baseModifier = modifier
        .size(size.dp)
        .clip(shape)
        .background(Brush.linearGradient(listOf(
            LocalBrandColors.current.primary.copy(0.2f),
            LocalBrandColors.current.primaryDark.copy(0.1f)
        )))
        .border(2.dp, LocalBrandColors.current.primary.copy(0.5f), shape)

    val pointerModifier = if (onSecondaryClick != null) {
        baseModifier.onPointerEvent(PointerEventType.Press) { event ->
            if (event.button == PointerButton.Secondary) {
                onSecondaryClick()
            }
        }
    } else {
        baseModifier
    }

    val boxModifier = if (onClick != null) {
        pointerModifier.clickable(onClick = onClick)
    } else {
        pointerModifier
    }

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(imageBitmap, "Скин $username", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Person, null, tint = LocalBrandColors.current.primary,
                modifier = Modifier.size((size * 0.5f).dp))
        }
    }
}

/** Индикатор прогресса загрузки */
@Composable
fun DownloadProgressBar(
    progress: DownloadProgress,
    animationsEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val visible = progress.status != DownloadStatus.IDLE && progress.status != DownloadStatus.COMPLETE

    if (animationsEnabled) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            ProgressContent(progress, modifier)
        }
    } else if (visible) {
        ProgressContent(progress, modifier)
    }
}

@Composable
private fun ProgressContent(progress: DownloadProgress, modifier: Modifier) {
    val brand = LocalBrandColors.current
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                progress.message.ifBlank { progress.fileName },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
            )
            if (progress.totalBytes > 0) {
                Text("${progress.percent.toInt()}%", style = MaterialTheme.typography.bodyMedium, color = brand.primary)
            }
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { if (progress.totalBytes > 0) progress.percent / 100f else 0f },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = brand.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

/** Статус-бар */
@Composable
fun StatusBar(
    message: String,
    launchStatus: LaunchStatus,
    launchMessage: String,
    modifier: Modifier = Modifier
) {
    val brand = LocalBrandColors.current
    val statusColor = when (launchStatus) {
        LaunchStatus.RUNNING -> AccentGreen
        LaunchStatus.ERROR -> AccentRed
        LaunchStatus.DOWNLOADING, LaunchStatus.PREPARING, LaunchStatus.LAUNCHING, LaunchStatus.INSTALLING -> brand.primary
        else -> MaterialTheme.colorScheme.onSurface.copy(0.5f)
    }

    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(10.dp))
            Text(
                launchMessage.ifBlank { message },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.85f),
                maxLines = 2
            )
        }
    }
}

/** Заголовок секции */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(bottom = 12.dp)
    )
}

/** Логотип с GIF-иконкой */
@Composable
fun LauncherLogo(
    modifier: Modifier = Modifier,
    animationsEnabled: Boolean = true
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedGifImage(
            url = LAUNCHER_GIF_URL,
            size = 40.dp,
            contentDescription = BuildConfig.APP_NAME,
            animationsEnabled = animationsEnabled
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                BuildConfig.APP_NAME,
                style = MaterialTheme.typography.headlineMedium,
                color = LocalBrandColors.current.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Версия лаунчера: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f)
            )
        }
    }
}

/** Диалог ошибки */
@Composable
fun ErrorDialog(message: String?, onDismiss: () -> Unit) {
    if (message != null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.Error, null, tint = AccentRed) },
            title = { Text("Ошибка") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text("OK", color = LocalBrandColors.current.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(
                        java.awt.datatransfer.StringSelection(message), null
                    )
                }) {
                    Text("Копировать", color = LocalBrandColors.current.primary)
                }
            }
        )
    }
}

/** Диалог подтверждения смены темы */
@Composable
fun ThemeChangeDialog(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Default.DarkMode, null, tint = LocalBrandColors.current.primary) },
            title = { Text("Смена темы") },
            text = {
                Text(
                    "Вы уверены, что хотите сменить тему?\n" +
                            "Для применения изменений лаунчер будет перезапущен."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = LocalBrandColors.current.primary,
                        contentColor = LocalBrandColors.current.onPrimary
                    )
                ) { Text("Да") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Нет") }
            }
        )
    }
}

/** Диалог входа Microsoft (Device Code — microsoft.com/link) */
@Composable
fun MicrosoftAuthDialog(
    state: com.lolauncher.viewmodel.MicrosoftAuthUiState,
    onDismiss: () -> Unit
) {
    if (!state.visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.AccountCircle, null, tint = LocalBrandColors.current.primary) },
        title = { Text("Вход Microsoft") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.error != null) {
                    Text(state.error, color = AccentRed)
                } else {
                    Text(
                        "1. Откройте сайт:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        state.verificationUri,
                        color = LocalBrandColors.current.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text("2. Введите код:")
                    Text(
                        state.userCode,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (state.isPolling) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = LocalBrandColors.current.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Ожидание подтверждения...", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = LocalBrandColors.current.primary)
            }
        }
    )
}

/** Диалог выбора действия лаунчера при запуске Minecraft */
@Composable
fun LaunchActionDialog(
    visible: Boolean,
    selected: LauncherOnGameLaunch,
    rememberChoice: Boolean,
    onSelectedChange: (LauncherOnGameLaunch) -> Unit,
    onRememberChange: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.PlayArrow, null, tint = LocalBrandColors.current.primary) },
        title = { Text("Запуск Minecraft") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Что сделать с LoLauncher после запуска игры?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                LauncherOnGameLaunch.entries.forEach { action ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == action,
                            onClick = { onSelectedChange(action) },
                            colors = RadioButtonDefaults.colors(selectedColor = LocalBrandColors.current.primary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(action.displayName)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = rememberChoice,
                        onCheckedChange = onRememberChange,
                        colors = CheckboxDefaults.colors(checkedColor = LocalBrandColors.current.primary)
                    )
                    Text("Запомнить выбор", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalBrandColors.current.primary,
                    contentColor = LocalBrandColors.current.onPrimary
                )
            ) { Text("Играть") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

/** Строка возможности с SVG-иконкой */
@Composable
fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = LocalBrandColors.current.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
