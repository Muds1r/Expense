package com.expense.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val IncomeGreen = Color(0xFF1B873F)
val ExpenseRed = Color(0xFFD03A2B)

// Green palette to match the banknote-style launcher icon.
private val LightColors = lightColorScheme(
    primary = Color(0xFF176B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5F2BD),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFF4F6353),
    secondaryContainer = Color(0xFFD2E8D4),
    tertiary = Color(0xFF3A646F),
    surfaceVariant = Color(0xFFDDE5DB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AD5A2),
    onPrimary = Color(0xFF00391C),
    primaryContainer = Color(0xFF005229),
    onPrimaryContainer = Color(0xFFA5F2BD),
    secondary = Color(0xFFB6CCB8),
    secondaryContainer = Color(0xFF374B3C),
    tertiary = Color(0xFFA2CDDA)
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
