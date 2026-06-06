package com.lolauncher.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lolauncher.ui.components.FeatureRow
import com.lolauncher.ui.components.LauncherLogo
import com.lolauncher.BuildConfig
import com.lolauncher.ui.theme.BrandYellow

/**
 * Экран «О программе».
 */
@Composable
fun AboutScreen(animationsEnabled: Boolean = true) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LauncherLogo(animationsEnabled = animationsEnabled)

        Spacer(Modifier.height(8.dp))

        Text(
            "Современный Minecraft Launcher",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(0.6f)
        )

        Spacer(Modifier.height(4.dp))

        Text(
            "Версия ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodyMedium,
            color = BrandYellow,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(32.dp))

        LauncherCard(Modifier.fillMaxWidth(0.85f)) {
            Text("Возможности", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground)
            Spacer(Modifier.height(12.dp))

            FeatureRow(Icons.Default.SportsEsports, "Запуск всех версий Minecraft (Release, Snapshot)")
            FeatureRow(Icons.Default.Build, "Поддержка Forge, Fabric, OptiFine")
            FeatureRow(Icons.Default.AccountCircle, "Офлайн и Microsoft авторизация")
            FeatureRow(Icons.Default.Face, "Автоматическая загрузка скинов")
            FeatureRow(Icons.Default.Extension, "Управление модами")
            FeatureRow(Icons.Default.Coffee, "Автоустановка Java")
            FeatureRow(Icons.Default.DarkMode, "Тёмная и светлая тема")
            FeatureRow(Icons.Default.CloudDownload, "Загрузка с официальных серверов Mojang")
        }

        Spacer(Modifier.height(24.dp))

        LauncherCard(Modifier.fillMaxWidth(0.85f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null, tint = BrandYellow, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Создатель", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(0.6f))
                    Text("Txxicu Team", style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            "${BuildConfig.APP_NAME} не связан с Mojang Studios или Microsoft.\n" +
                    "Minecraft является торговой маркой Mojang Studios.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(0.4f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LauncherCard(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    com.lolauncher.ui.components.LauncherCard(modifier, content = content)
}
