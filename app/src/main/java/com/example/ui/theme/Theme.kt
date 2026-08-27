package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val StarkColorScheme = darkColorScheme(
    primary = ArcReactorCyan,
    onPrimary = NanotechBlack,
    primaryContainer = ArcReactorDim,
    onPrimaryContainer = ArcReactorCyan,
    secondary = StarkGold,
    onSecondary = NanotechBlack,
    secondaryContainer = StarkGoldMuted,
    onSecondaryContainer = StarkTextPrimary,
    tertiary = StarkCrimson,
    onTertiary = StarkTextPrimary,
    background = NanotechBlack,
    onBackground = StarkTextPrimary,
    surface = TitaniumDark,
    onSurface = StarkTextPrimary,
    surfaceVariant = TitaniumCard,
    onSurfaceVariant = StarkTextSecondary,
    outline = TitaniumBorder,
    error = StarkError
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            window?.let {
                it.statusBarColor = NanotechBlack.toArgb()
                it.navigationBarColor = NanotechBlack.toArgb()
                WindowCompat.getInsetsController(it, view).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = StarkColorScheme,
        typography = Typography,
        content = content
    )
}
