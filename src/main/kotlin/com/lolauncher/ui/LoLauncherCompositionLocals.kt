package com.lolauncher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.awt.Window

val LocalAwtWindow = staticCompositionLocalOf<Window?> { null }
