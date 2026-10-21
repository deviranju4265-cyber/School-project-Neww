package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val SafeLockDarkColorScheme = darkColorScheme(
  primary = SafeLockCyan,
  onPrimary = Color(0xFF04131A),
  primaryContainer = Color(0xFF0E3A53),
  onPrimaryContainer = SafeLockCyanLight,
  secondary = SafeLockIndigo,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFF282566),
  onSecondaryContainer = Color(0xFFE0E7FF),
  tertiary = SafeLockEmerald,
  onTertiary = Color(0xFF032218),
  background = SafeLockBackground,
  onBackground = SafeLockTextPrimary,
  surface = SafeLockSurface,
  onSurface = SafeLockTextPrimary,
  surfaceVariant = SafeLockSurfaceVariant,
  onSurfaceVariant = SafeLockTextSecondary,
  error = SafeLockRose,
  onError = Color.White
)

@Composable
fun MyApplicationTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = SafeLockDarkColorScheme,
    typography = Typography,
    content = content
  )
}
