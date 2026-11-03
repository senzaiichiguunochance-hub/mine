package com.example.clipnote

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

private val LightOrangeColorScheme = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFCC80),
    onPrimaryContainer = Color(0xFF3E1F00),
    secondary = Color(0xFFF57C00),
    surface = Color(0xFFFFFBFE),
    background = Color(0xFFFFF8E1)
)

private val DarkOrangeColorScheme = darkColorScheme(
    primary = Color(0xFFFFB74D),
    onPrimary = Color(0xFF4A2800),
    primaryContainer = Color(0xFF7A3E00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFFFA726),
    surface = Color(0xFF211D17),
    background = Color(0xFF16130E)
)

@Composable
fun ClipNoteTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkOrangeColorScheme
        else -> LightOrangeColorScheme
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
