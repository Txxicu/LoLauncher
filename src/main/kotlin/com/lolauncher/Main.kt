package com.lolauncher

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lolauncher.ui.LocalAwtWindow
import com.lolauncher.ui.MainWindow
import androidx.compose.runtime.CompositionLocalProvider
import com.lolauncher.util.LogService
import com.lolauncher.util.SettingsManager
import com.lolauncher.viewmodel.LauncherViewModel
import com.lolauncher.viewmodel.LauncherWindowActions

/**
 * Точка входа LoLauncher.
 */
fun main() = application {
    LogService.info("Запуск ${BuildConfig.APP_NAME} v${BuildConfig.VERSION_NAME}")

    val initialSettings = SettingsManager.load()
    val viewModel = LauncherViewModel()

    val windowState = rememberWindowState(
        width = initialSettings.windowWidth.dp,
        height = initialSettings.windowHeight.dp
    )

    Window(
        onCloseRequest = {
            viewModel.dispose()
            exitApplication()
        },
        title = BuildConfig.APP_NAME,
        state = windowState
    ) {
        DisposableEffect(window) {
            viewModel.windowActions = LauncherWindowActions(
                hide = { window.isVisible = false },
                show = { window.isVisible = true },
                close = {
                    viewModel.dispose()
                    exitApplication()
                },
                resize = { width, height ->
                    windowState.size = DpSize(width.dp, height.dp)
                }
            )
            onDispose { viewModel.windowActions = null }
        }

        LaunchedEffect(viewModel.settings.windowWidth, viewModel.settings.windowHeight) {
            windowState.size = DpSize(
                viewModel.settings.windowWidth.dp,
                viewModel.settings.windowHeight.dp
            )
        }

        CompositionLocalProvider(LocalAwtWindow provides window) {
            MainWindow(viewModel = viewModel)
        }
    }
}
