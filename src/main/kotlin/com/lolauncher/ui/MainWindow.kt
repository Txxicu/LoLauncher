package com.lolauncher.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lolauncher.ui.components.*
import com.lolauncher.ui.screens.*
import com.lolauncher.ui.theme.LoLauncherTheme
import com.lolauncher.util.LogService
import com.lolauncher.viewmodel.LauncherViewModel

/** Экраны навигации */
enum class AppScreen(val title: String) {
    MAIN("Главная"),
    ACCOUNTS("Аккаунты"),
    MODS("Моды"),
    RESOURCE_PACKS("Ресурс-паки"),
    SETTINGS("Настройки"),
    ABOUT("О программе")
}

@Composable
fun MainWindow(viewModel: LauncherViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.MAIN) }

    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(viewModel) {
        val listener: () -> Unit = { tick++ }
        viewModel.addStateListener(listener)
        onDispose { viewModel.removeStateListener(listener) }
    }
    @Suppress("UNUSED_VARIABLE") val trigger = tick

    val animationsEnabled = !viewModel.settings.disableAnimations

    ErrorDialog(viewModel.errorDialogMessage, onDismiss = { viewModel.dismissError() })
    LaunchConsoleDialog(
        visible = viewModel.launchConsoleVisible,
        entries = viewModel.launchLogEntries,
        errorHighlight = viewModel.launchConsoleError,
        onDismiss = { viewModel.dismissLaunchConsole() }
    )

    LoLauncherTheme(darkTheme = viewModel.settings.isDarkTheme) {
        Row(Modifier.fillMaxSize()) {
            Sidebar(
                currentScreen = currentScreen,
                onScreenSelected = { currentScreen = it },
                onRefresh = { viewModel.refreshAll() },
                isLoading = viewModel.isLoading,
                animationsEnabled = animationsEnabled,
                modifier = Modifier.width(230.dp)
            )

            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (animationsEnabled) {
                        AnimatedContent(
                            targetState = currentScreen,
                            transitionSpec = {
                                fadeIn(tween(250)) + slideInHorizontally { it / 4 } togetherWith
                                        fadeOut(tween(200)) + slideOutHorizontally { -it / 4 }
                            },
                            label = "screen"
                        ) { screen -> ScreenContent(screen, viewModel, animationsEnabled) { currentScreen = it } }
                    } else {
                        ScreenContent(currentScreen, viewModel, animationsEnabled) { currentScreen = it }
                    }
                }

                StatusBar(
                    message = viewModel.statusMessage,
                    launchStatus = viewModel.launchStatus,
                    launchMessage = viewModel.launchMessage
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    screen: AppScreen,
    viewModel: LauncherViewModel,
    animationsEnabled: Boolean,
    onNavigate: (AppScreen) -> Unit
) {
    when (screen) {
        AppScreen.MAIN -> MainScreen(viewModel, onOpenAccounts = { onNavigate(AppScreen.ACCOUNTS) })
        AppScreen.ACCOUNTS -> AccountsScreen(viewModel)
        AppScreen.MODS -> ModsScreen(viewModel)
        AppScreen.RESOURCE_PACKS -> ResourcePacksScreen(viewModel)
        AppScreen.SETTINGS -> SettingsScreen(viewModel)
        AppScreen.ABOUT -> AboutScreen(animationsEnabled = animationsEnabled)
    }
}

@Composable
private fun Sidebar(
    currentScreen: AppScreen,
    onScreenSelected: (AppScreen) -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    animationsEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            LauncherLogo(
                modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                animationsEnabled = animationsEnabled
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(0.25f))

            NavButton(Icons.Default.Home, "Главная", currentScreen == AppScreen.MAIN, onClick = { onScreenSelected(AppScreen.MAIN) })
            NavButton(Icons.Default.AccountCircle, "Аккаунты", currentScreen == AppScreen.ACCOUNTS,
                onClick = { onScreenSelected(AppScreen.ACCOUNTS) })
            NavButton(Icons.Default.Extension, "Моды", currentScreen == AppScreen.MODS, onClick = { onScreenSelected(AppScreen.MODS) })
            NavButton(Icons.Default.Image, "Ресурс-паки", currentScreen == AppScreen.RESOURCE_PACKS,
                onClick = { onScreenSelected(AppScreen.RESOURCE_PACKS) })
            NavButton(Icons.Default.Settings, "Настройки", currentScreen == AppScreen.SETTINGS, onClick = { onScreenSelected(AppScreen.SETTINGS) })

            Spacer(Modifier.weight(1f))

            HorizontalDivider(Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outline.copy(0.25f))

            NavButton(Icons.Default.Refresh, if (isLoading) "Загрузка..." else "Обновить", onClick = onRefresh)
            NavButton(Icons.Default.Info, "О программе", currentScreen == AppScreen.ABOUT, onClick = { onScreenSelected(AppScreen.ABOUT) })

            Spacer(Modifier.height(8.dp))
        }
    }
}
