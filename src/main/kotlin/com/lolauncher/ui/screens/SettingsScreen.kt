package com.lolauncher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lolauncher.ui.components.LauncherCard
import com.lolauncher.ui.components.SectionTitle
import com.lolauncher.ui.components.ThemeChangeDialog
import com.lolauncher.ui.theme.AccentGreen
import com.lolauncher.ui.theme.AccentRed
import com.lolauncher.ui.theme.BrandYellow
import com.lolauncher.data.models.LauncherOnGameLaunch
import com.lolauncher.viewmodel.LauncherViewModel

/**
 * Экран настроек лаунчера.
 */
@Composable
fun SettingsScreen(viewModel: LauncherViewModel) {
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(viewModel) {
        val listener: () -> Unit = { tick++ }
        viewModel.addStateListener(listener)
        onDispose { viewModel.removeStateListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE") val trigger = tick

    val settings = viewModel.settings
    val javaInfo = viewModel.javaInfo

    var showThemeDialog by remember { mutableStateOf(false) }
    var pendingDarkTheme by remember { mutableStateOf<Boolean?>(null) }

    ThemeChangeDialog(
        visible = showThemeDialog,
        onConfirm = {
            showThemeDialog = false
            pendingDarkTheme?.let { viewModel.confirmThemeChange(it) }
            pendingDarkTheme = null
        },
        onDismiss = {
            showThemeDialog = false
            pendingDarkTheme = null
        }
    )

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTitle("Настройки")

        LauncherCard(Modifier.fillMaxWidth()) {
            Text("Папка Minecraft", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.minecraftDir,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandYellow),
                trailingIcon = {
                    IconButton(onClick = { viewModel.selectMinecraftFolder() }) {
                        Icon(Icons.Default.FolderOpen, "Выбрать папку", tint = BrandYellow)
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(
                onClick = { viewModel.selectMinecraftFolder() },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandYellow.copy(0.15f))
            ) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp), tint = BrandYellow)
                Spacer(Modifier.width(6.dp))
                Text("Выбрать папку Minecraft")
            }
        }

        LauncherCard(Modifier.fillMaxWidth()) {
            Text("Оперативная память (RAM)", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))
            Text("Минимум: ${settings.ramMinMb} МБ", color = MaterialTheme.colorScheme.onBackground.copy(0.7f))
            Slider(
                value = settings.ramMinMb.toFloat(),
                onValueChange = { viewModel.updateSettings(settings.copy(ramMinMb = it.toInt())) },
                valueRange = 512f..settings.ramMaxMb.toFloat(),
                steps = 10,
                colors = SliderDefaults.colors(thumbColor = BrandYellow, activeTrackColor = BrandYellow)
            )
            Text("Максимум: ${settings.ramMaxMb} МБ", color = MaterialTheme.colorScheme.onBackground.copy(0.7f))
            Slider(
                value = settings.ramMaxMb.toFloat(),
                onValueChange = { viewModel.updateSettings(settings.copy(ramMaxMb = it.toInt())) },
                valueRange = settings.ramMinMb.toFloat()..16384f,
                steps = 20,
                colors = SliderDefaults.colors(thumbColor = BrandYellow, activeTrackColor = BrandYellow)
            )
        }

        LauncherCard(Modifier.fillMaxWidth()) {
            Text("Java", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            if (javaInfo != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Java ${javaInfo.version}", fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text(javaInfo.path, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.55f))
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = AccentRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Java не найдена", color = AccentRed)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.checkJava() },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandYellow.copy(0.15f))) {
                    Icon(Icons.Default.Search, null, Modifier.size(18.dp), tint = BrandYellow)
                    Spacer(Modifier.width(4.dp))
                    Text("Проверить")
                }
                FilledTonalButton(onClick = { viewModel.installJava() },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandYellow.copy(0.15f))) {
                    Icon(Icons.Default.Download, null, Modifier.size(18.dp), tint = BrandYellow)
                    Spacer(Modifier.width(4.dp))
                    Text("Установить Java 17")
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = settings.javaPath,
                onValueChange = { viewModel.updateSettings(settings.copy(javaPath = it)) },
                label = { Text("Путь к Java (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandYellow)
            )
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow("Автоматическая установка Java", settings.autoJavaInstall) {
                viewModel.updateSettings(settings.copy(autoJavaInstall = it))
            }
        }

        LauncherCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (settings.isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                        null, tint = BrandYellow, modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Тема оформления", style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            if (settings.isDarkTheme) "Тёмная" else "Светлая",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.55f)
                        )
                    }
                }
                Switch(
                    checked = settings.isDarkTheme,
                    onCheckedChange = { newValue ->
                        if (newValue != settings.isDarkTheme) {
                            pendingDarkTheme = newValue
                            showThemeDialog = true
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BrandYellow,
                        checkedTrackColor = BrandYellow.copy(0.4f)
                    )
                )
            }
        }

        LauncherCard(Modifier.fillMaxWidth()) {
            SettingSwitchRow("Отключить анимации", settings.disableAnimations) {
                viewModel.updateSettings(settings.copy(disableAnimations = it))
            }
        }

        LauncherCard(Modifier.fillMaxWidth()) {
            Text("Список версий", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow("Показывать снапшоты", settings.showSnapshots) {
                viewModel.updateSettings(settings.copy(showSnapshots = it))
            }
            Text(
                "Снапшоты скрываются во вкладках «Все» и «Release». Вкладка Snapshot всегда доступна.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(0.55f)
            )
        }

        LauncherCard(Modifier.fillMaxWidth()) {
            Text("Размер окна лаунчера", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))
            Text("Ширина: ${settings.windowWidth} px", color = MaterialTheme.colorScheme.onBackground.copy(0.7f))
            Slider(
                value = settings.windowWidth.toFloat(),
                onValueChange = {
                    val w = it.toInt()
                    viewModel.updateSettings(settings.copy(windowWidth = w))
                    viewModel.applyWindowSize(w, settings.windowHeight)
                },
                valueRange = 900f..1920f,
                steps = 20,
                colors = SliderDefaults.colors(thumbColor = BrandYellow, activeTrackColor = BrandYellow)
            )
            Text("Высота: ${settings.windowHeight} px", color = MaterialTheme.colorScheme.onBackground.copy(0.7f))
            Slider(
                value = settings.windowHeight.toFloat(),
                onValueChange = {
                    val h = it.toInt()
                    viewModel.updateSettings(settings.copy(windowHeight = h))
                    viewModel.applyWindowSize(settings.windowWidth, h)
                },
                valueRange = 600f..1080f,
                steps = 16,
                colors = SliderDefaults.colors(thumbColor = BrandYellow, activeTrackColor = BrandYellow)
            )
        }

        LauncherCard(Modifier.fillMaxWidth()) {
            Text("Консоль и логи", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow("Показывать консоль Minecraft при запуске", settings.showLaunchConsole) {
                viewModel.updateSettings(settings.copy(showLaunchConsole = it))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { viewModel.openLastLaunchLog() },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandYellow.copy(0.15f))) {
                    Icon(Icons.Default.Terminal, null, Modifier.size(18.dp), tint = BrandYellow)
                    Spacer(Modifier.width(4.dp))
                    Text("Открыть последний лог")
                }
                FilledTonalButton(onClick = { viewModel.openLogsFolder() },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = BrandYellow.copy(0.15f))) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(18.dp), tint = BrandYellow)
                    Spacer(Modifier.width(4.dp))
                    Text("Папка логов")
                }
            }
        }

        LauncherCard(Modifier.fillMaxWidth()) {
            Text("При запуске Minecraft", style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(8.dp))
            LauncherOnGameLaunch.entries.forEach { action ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = settings.launcherOnGameLaunch == action.name,
                        onClick = {
                            viewModel.updateSettings(settings.copy(launcherOnGameLaunch = action.name))
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = BrandYellow)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(action.displayName, color = MaterialTheme.colorScheme.onBackground)
                }
            }
            Spacer(Modifier.height(8.dp))
            SettingSwitchRow("Спрашивать при каждом запуске", settings.askLauncherOnGameLaunch) {
                viewModel.updateSettings(settings.copy(askLauncherOnGameLaunch = it))
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onBackground)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BrandYellow,
                checkedTrackColor = BrandYellow.copy(0.4f)
            )
        )
    }
}
