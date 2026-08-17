package com.sismored.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Fondo = Color(0xFF0C1210)
val Superficie = Color(0xFF131B18)
val Linea = Color(0xFF263029)
val Texto = Color(0xFFEDF1EE)
val TextoTenue = Color(0xFF8FA199)
val Rojo = Color(0xFFE63946)
val Verde = Color(0xFF5FE1A3)
val Ambar = Color(0xFFF5A623)

private val EsquemaSismoRed = darkColorScheme(
    background = Fondo,
    surface = Superficie,
    primary = Verde,
    error = Rojo,
    onBackground = Texto,
    onSurface = Texto,
)

@Composable
fun SismoRedTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EsquemaSismoRed,
        content = content
    )
}
