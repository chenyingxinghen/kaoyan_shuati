package com.example.kaoshishuati.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// 关闭壁纸取色，保证品牌识别度；如需开启传 dynamicColor = true
private val LightColors = lightColorScheme(
    primary = Brand600,
    onPrimary = Neutral50,
    primaryContainer = Brand100,
    onPrimaryContainer = Brand800,
    inversePrimary = Brand300,

    secondary = Teal500,
    onSecondary = Neutral50,
    secondaryContainer = Teal50,
    onSecondaryContainer = Teal700,

    tertiary = Amber500,
    onTertiary = Neutral50,
    tertiaryContainer = Amber50,
    onTertiaryContainer = Amber700,

    error = Red500,
    onError = Neutral50,
    errorContainer = Red100,
    onErrorContainer = Red700,

    background = Neutral50,
    onBackground = Neutral900,

    surface = Color.White,
    onSurface = Neutral900,
    surfaceVariant = Neutral100,
    onSurfaceVariant = Neutral500,
    surfaceTint = Brand600,

    inverseSurface = Neutral900,
    inverseOnSurface = Neutral50,

    outline = Neutral300,
    outlineVariant = Neutral200,
    scrim = Color(0x99000000),
)

private val DarkColors = darkColorScheme(
    primary = DarkBrand,
    onPrimary = Brand800,
    primaryContainer = DarkBrandContainer,
    onPrimaryContainer = Brand100,

    secondary = DarkTeal,
    onSecondary = Teal700,
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = Teal100,

    tertiary = DarkAmber,
    onTertiary = Amber700,
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = Amber100,

    error = DarkRed,
    onError = Red700,
    errorContainer = DarkRedContainer,
    onErrorContainer = Red100,

    background = DarkBg,
    onBackground = DarkInk,

    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceHigh,
    onSurfaceVariant = DarkInkSecondary,
    surfaceTint = DarkBrand,

    inverseSurface = DarkInk,
    inverseOnSurface = DarkSurface,

    outline = DarkOutline,
    outlineVariant = Color(0xFF2B3743),
    scrim = Color(0x99000000),
)

@Composable
fun KaoshishuatiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // 默认关闭壁纸取色，确保品牌色一致
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}