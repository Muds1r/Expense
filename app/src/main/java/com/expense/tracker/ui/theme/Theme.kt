package com.expense.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val IncomeGreen = Color(0xFF1B873F)
val ExpenseRed = Color(0xFFC62828)

// Soft, minimal palette — airy surfaces with translucent overlays in UI.
private val LightColors = lightColorScheme(
    primary = Color(0xFF1B5E3B),
    onPrimary = Color.White,
    primaryContainer = Color(0x331B5E3B),
    onPrimaryContainer = Color(0xFF0D3B24),
    secondary = Color(0xFF5A6B60),
    secondaryContainer = Color(0x225A6B60),
    onSecondaryContainer = Color(0xFF1C2B22),
    tertiary = Color(0xFF4A6670),
    background = Color(0xFFF5F7F5),
    surface = Color(0xFFF5F7F5),
    surfaceVariant = Color(0x1A1B5E3B),
    surfaceContainer = Color(0xCCF0F3F0),
    surfaceContainerHigh = Color(0xE6FFFFFF),
    outline = Color(0x331B5E3B),
    outlineVariant = Color(0x221B5E3B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD5A2),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0x3348A06B),
    onPrimaryContainer = Color(0xFFB8F0CB),
    secondary = Color(0xFFB6CCB8),
    secondaryContainer = Color(0x33374B3C),
    background = Color(0xFF101512),
    surface = Color(0xFF101512),
    surfaceVariant = Color(0x22FFFFFF),
    surfaceContainer = Color(0xCC161B17),
    surfaceContainerHigh = Color(0xE61C221E),
    outline = Color(0x33FFFFFF),
    outlineVariant = Color(0x22FFFFFF)
)

@Composable
fun ExpenseTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
