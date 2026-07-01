package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = NavyBlue,
    onPrimary = White,
    secondary = AccentBlue,
    onSecondary = White,
    background = DarkNavy,
    surface = DarkNavy,
    onBackground = White,
    onSurface = White,
    error = ErrorRed,
    onError = White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NavyBlue,
    onPrimary = White,
    secondary = AccentBlue,
    onSecondary = White,
    background = LightGray,
    surface = White,
    onBackground = TextDark,
    onSurface = TextDark,
    surfaceVariant = MediumGray,
    onSurfaceVariant = TextLight,
    outline = BorderGray,
    error = ErrorRed,
    onError = White
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color disabled to ensure our custom palette is used
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
