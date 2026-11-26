package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = SapphirePrimary,
    onPrimary = Color.White,
    primaryContainer = SapphireContainer,
    onPrimaryContainer = OnSapphireContainer,
    secondary = AmberGoldAccent,
    onSecondary = Color.White,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = OnAmberContainer,
    tertiary = EmeraldVerified,
    onTertiary = Color.White,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = OnEmeraldContainer,
    background = IvoryCanvas,
    onBackground = DeepIndigoInk,
    surface = PurePaper,
    onSurface = DeepIndigoInk,
    surfaceVariant = SoftParchment,
    onSurfaceVariant = SlateMuted,
    outline = WarmOutlineStrong,
    outlineVariant = WarmOutline,
    error = ErrorCrimson,
    onError = Color.White,
    errorContainer = ErrorSoftContainer,
    onErrorContainer = OnErrorSoftContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = PeriwinklePrimary,
    onPrimary = OnPeriwinkle,
    primaryContainer = PeriwinkleContainer,
    onPrimaryContainer = OffWhiteInk,
    secondary = GoldDarkAccent,
    onSecondary = Color(0xFF3E2800),
    secondaryContainer = Color(0xFF543900),
    onSecondaryContainer = Color(0xFFFFE08A),
    tertiary = EmeraldDarkAccent,
    onTertiary = Color(0xFF003824),
    tertiaryContainer = Color(0xFF005136),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = ObsidianCanvas,
    onBackground = OffWhiteInk,
    surface = SlateSurface,
    onSurface = OffWhiteInk,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = CoolGrayText,
    outline = DarkOutline,
    outlineVariant = Color(0xFF242F49)
)

val SubjectSolverShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SubjectSolverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
        typography = SubjectSolverTypography,
        shapes = SubjectSolverShapes,
        content = content
    )
}
