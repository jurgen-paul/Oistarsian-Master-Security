package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SecurityColorScheme = darkColorScheme(
    primary = CyberTeal,
    onPrimary = Color.Black,
    primaryContainer = ElectricBlue,
    onPrimaryContainer = Color.White,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    tertiary = ThreatRed,
    background = MidnightDeep,
    onBackground = IceWhite,
    surface = SlateCard,
    onSurface = IceWhite,
    error = ThreatRed,
    onError = Color.Black
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SecurityColorScheme,
        typography = Typography,
        content = content
    )
}
