package com.expense.tracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val IncomeGreen = Color(0xFF1B873F)
val ExpenseRed = Color(0xFFD03A2B)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A5FB4),
    secondary = Color(0xFF265E51),
    tertiary = Color(0xFF7A4988)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF99C1F1),
    secondary = Color(0xFF8FD0BE),
    tertiary = Color(0xFFDCB8E8)
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
