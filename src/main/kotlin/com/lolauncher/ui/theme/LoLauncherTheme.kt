package com.lolauncher.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Адаптивная жёлтая фирменная тема LoLauncher.
 * Светлая — мягкий янтарный; тёмная — насыщенный золотой без излишнего свечения.
 */

/** Локальные фирменные цвета, зависящие от темы */
data class BrandColors(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val onPrimary: Color,
    val selectedBg: Color,
    val selectedBorder: Color,
    val chipBg: Color
)

val LocalBrandColors = staticCompositionLocalOf {
    BrandColors(
        primary = Color(0xFFE6A800),
        primaryDark = Color(0xFFCC9600),
        primaryLight = Color(0xFFFFD666),
        onPrimary = Color(0xFF1C1400),
        selectedBg = Color(0x33E6A800),
        selectedBorder = Color(0x66E6A800),
        chipBg = Color(0x22E6A800)
    )
}

// Обратная совместимость — используются через CompositionLocal в Theme
val BrandYellow @Composable get() = LocalBrandColors.current.primary
val BrandYellowDark @Composable get() = LocalBrandColors.current.primaryDark
val BrandYellowLight @Composable get() = LocalBrandColors.current.primaryLight

val AccentGreen = Color(0xFF43A047)
val AccentRed = Color(0xFFD32F2F)
val AccentOrange = Color(0xFFEF8C00)

private val darkBrand = BrandColors(
    primary = Color(0xFFD4A017),
    primaryDark = Color(0xFFB8860B),
    primaryLight = Color(0xFFE8C547),
    onPrimary = Color(0xFF1A1408),
    selectedBg = Color(0x26D4A017),
    selectedBorder = Color(0x55D4A017),
    chipBg = Color(0x1AD4A017)
)

private val lightBrand = BrandColors(
    primary = Color(0xFFC68A00),
    primaryDark = Color(0xFFA67400),
    primaryLight = Color(0xFFFFE082),
    onPrimary = Color(0xFF2A1F00),
    selectedBg = Color(0x33C68A00),
    selectedBorder = Color(0x55C68A00),
    chipBg = Color(0x22C68A00)
)

private val DarkColorScheme = darkColorScheme(
    primary = darkBrand.primary,
    onPrimary = darkBrand.onPrimary,
    primaryContainer = Color(0xFF3D3010),
    onPrimaryContainer = darkBrand.primaryLight,
    secondary = darkBrand.primaryDark,
    onSecondary = Color(0xFFF0E8D0),
    background = Color(0xFF141414),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF1C1C1C),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFCCCCCC),
    error = AccentRed,
    outline = Color(0xFF404040)
)

private val LightColorScheme = lightColorScheme(
    primary = lightBrand.primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFF3D6),
    onPrimaryContainer = Color(0xFF3D2E00),
    secondary = lightBrand.primaryDark,
    onSecondary = Color.White,
    background = Color(0xFFF7F7F5),
    onBackground = Color(0xFF1C1C1C),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2A2A2A),
    surfaceVariant = Color(0xFFF0EDE8),
    onSurfaceVariant = Color(0xFF444444),
    error = AccentRed,
    outline = Color(0xFFD8D4CC)
)

val LauncherTypography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp)
)

@Composable
fun LoLauncherTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val brand = if (darkTheme) darkBrand else lightBrand

    CompositionLocalProvider(LocalBrandColors provides brand) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LauncherTypography,
            content = content
        )
    }
}
