package com.example.pintxomatch.ui.theme

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

// 1. Esquema de colores para el MODO OSCURO (Noche)
private val DarkColorScheme = darkColorScheme(
    primary = PimentonRed,
    secondary = TxakoliGold,
    background = ArbelGrey,       // Fondo gris oscuro tipo pizarra
    surface = ArbelGrey,          // Las tarjetas también oscuras
    onPrimary = Color.White,      // El texto sobre rojo sigue siendo blanco
    onBackground = CreamBackground, // El texto sobre fondo oscuro será clarito
    onSurface = CreamBackground
)

// 2. Esquema de colores para el MODO CLARO (Día)
private val LightColorScheme = lightColorScheme(
    primary = PimentonRed,
    secondary = TxakoliGold,
    background = CreamBackground, // Nuestro blanco roto/crema
    surface = CreamBackground,    // Tarjetas color crema
    onPrimary = Color.White,
    onBackground = ArbelGrey,     // Texto oscuro para que se lea bien
    onSurface = ArbelGrey
)

@Composable
fun PintxoMatchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // MUY IMPORTANTE: Lo cambiamos a 'false' para forzar a que Android
    // respete nuestros colores y no use los del sistema (Material You).
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Usa las tipografías por defecto de tu Type.kt
        content = content
    )
}