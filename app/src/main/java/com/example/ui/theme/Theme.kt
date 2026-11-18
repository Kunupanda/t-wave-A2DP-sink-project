package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class AcousticDeckColors(
  val idleBeacon: Color,
  val listeningBeacon: Color,
  val connectedBeacon: Color,
  val vuPeakAmber: Color,
  val vuSpectrumCyan: Color,
  val vuSignalGreen: Color,
  val surfaceWell: Color,
  val surfaceBorder: Color,
)

val LocalAcousticColors = staticCompositionLocalOf {
  AcousticDeckColors(
    idleBeacon = TextSecondaryDark,
    listeningBeacon = AcousticCyan,
    connectedBeacon = SignalEmerald,
    vuPeakAmber = StudioAmber,
    vuSpectrumCyan = AcousticCyan,
    vuSignalGreen = SignalEmerald,
    surfaceWell = ObsidianBg,
    surfaceBorder = DeckOutlineSubtle,
  )
}

private val DarkColorScheme =
  darkColorScheme(
    primary = AcousticCyan,
    onPrimary = Color(0xFF002229),
    primaryContainer = AcousticCyanContainer,
    onPrimaryContainer = OnAcousticCyanContainer,
    secondary = StudioAmber,
    onSecondary = Color(0xFF261900),
    secondaryContainer = StudioAmberContainer,
    onSecondaryContainer = OnStudioAmberContainer,
    tertiary = SignalEmerald,
    onTertiary = Color(0xFF002918),
    tertiaryContainer = SignalEmeraldContainer,
    onTertiaryContainer = OnSignalEmeraldContainer,
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = DeckSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DeckSurfaceContainer,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = DeckSurfaceContainer,
    surfaceContainerHigh = DeckSurfaceHigh,
    outline = DeckOutline,
    outlineVariant = DeckOutlineSubtle,
    error = AlertCoral,
    onError = Color(0xFF330008),
    errorContainer = AlertCoralContainer,
    onErrorContainer = OnAlertCoralContainer,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = AcousticCyanDark,
    onPrimary = Color.White,
    primaryContainer = OnAcousticCyanContainer,
    onPrimaryContainer = Color(0xFF001F25),
    secondary = StudioAmberDark,
    onSecondary = Color.White,
    secondaryContainer = OnStudioAmberContainer,
    onSecondaryContainer = Color(0xFF2B1B00),
    tertiary = SignalEmeraldDark,
    onTertiary = Color.White,
    tertiaryContainer = OnSignalEmeraldContainer,
    onTertiaryContainer = Color(0xFF002113),
    background = DaylightBg,
    onBackground = TextPrimaryLight,
    surface = DaylightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = DaylightContainer,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = DaylightContainer,
    surfaceContainerHigh = DaylightHigh,
    outline = DaylightOutline,
    outlineVariant = DaylightOutlineSubtle,
    error = Color(0xFFBA1A2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD9),
    onErrorContainer = Color(0xFF410008),
  )

@Composable
fun WaveCastTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
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

  val acousticColors =
    if (darkTheme) {
      AcousticDeckColors(
        idleBeacon = TextSecondaryDark,
        listeningBeacon = AcousticCyan,
        connectedBeacon = SignalEmerald,
        vuPeakAmber = StudioAmber,
        vuSpectrumCyan = AcousticCyan,
        vuSignalGreen = SignalEmerald,
        surfaceWell = ObsidianBg,
        surfaceBorder = DeckOutlineSubtle,
      )
    } else {
      AcousticDeckColors(
        idleBeacon = TextSecondaryLight,
        listeningBeacon = AcousticCyanDark,
        connectedBeacon = SignalEmeraldDark,
        vuPeakAmber = Color(0xFFD97706),
        vuSpectrumCyan = AcousticCyanDark,
        vuSignalGreen = SignalEmeraldDark,
        surfaceWell = DaylightContainer,
        surfaceBorder = DaylightOutlineSubtle,
      )
    }

  CompositionLocalProvider(LocalAcousticColors provides acousticColors) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
