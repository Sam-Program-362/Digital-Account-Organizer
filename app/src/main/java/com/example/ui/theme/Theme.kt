package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.AppColorPalette
import com.example.domain.model.ThemePreference

private fun buildDarkScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    background: Color = ObsidianBackgroundDark,
    surface: Color = CharcoalSurfaceDark
): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = SlateTertiaryDark,
    onTertiary = OnSlateTertiaryDark,
    tertiaryContainer = SlateTertiaryContainerDark,
    onTertiaryContainer = OnSlateTertiaryContainerDark,
    background = background,
    onBackground = OnObsidianBackgroundDark,
    surface = surface,
    onSurface = OnCharcoalSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

private fun buildLightScheme(
    primary: Color,
    onPrimary: Color,
    primaryContainer: Color,
    onPrimaryContainer: Color,
    secondary: Color,
    onSecondary: Color,
    secondaryContainer: Color,
    onSecondaryContainer: Color,
    background: Color = AlabasterBackgroundLight,
    surface: Color = PureWhiteSurfaceLight
): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = SlateTertiaryLight,
    onTertiary = OnSlateTertiaryLight,
    tertiaryContainer = SlateTertiaryContainerLight,
    onTertiaryContainer = OnSlateTertiaryContainerLight,
    background = background,
    onBackground = OnAlabasterBackgroundLight,
    surface = surface,
    onSurface = OnSurfaceSlateLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

private fun resolveColorScheme(palette: AppColorPalette, useDark: Boolean): ColorScheme {
    return when (palette) {
        AppColorPalette.EMERALD -> if (useDark) {
            buildDarkScheme(
                EmeraldPrimaryDark, OnEmeraldPrimaryDark, EmeraldContainerDark, OnEmeraldContainerDark,
                TealSecondaryDark, OnTealSecondaryDark, TealContainerDark, OnTealContainerDark
            )
        } else {
            buildLightScheme(
                EmeraldPrimaryLight, OnEmeraldPrimaryLight, EmeraldContainerLight, OnEmeraldContainerLight,
                TealSecondaryLight, OnTealSecondaryLight, TealContainerLight, OnTealContainerLight
            )
        }
        AppColorPalette.SAPPHIRE -> if (useDark) {
            buildDarkScheme(
                SapphirePrimaryDark, OnSapphirePrimaryDark, SapphireContainerDark, OnSapphireContainerDark,
                SapphireSecondaryDark, OnSapphireSecondaryDark, SapphireSecondaryContainerDark, OnSapphireSecondaryContainerDark,
                background = Color(0xFF091024),
                surface = Color(0xFF152238)
            )
        } else {
            buildLightScheme(
                SapphirePrimaryLight, OnSapphirePrimaryLight, SapphireContainerLight, OnSapphireContainerLight,
                SapphireSecondaryLight, OnSapphireSecondaryLight, SapphireSecondaryContainerLight, OnSapphireSecondaryContainerLight
            )
        }
        AppColorPalette.AMETHYST -> if (useDark) {
            buildDarkScheme(
                AmethystPrimaryDark, OnAmethystPrimaryDark, AmethystContainerDark, OnAmethystContainerDark,
                AmethystSecondaryDark, OnAmethystSecondaryDark, AmethystSecondaryContainerDark, OnAmethystSecondaryContainerDark,
                background = Color(0xFF120B20),
                surface = Color(0xFF1F1635)
            )
        } else {
            buildLightScheme(
                AmethystPrimaryLight, OnAmethystPrimaryLight, AmethystContainerLight, OnAmethystContainerLight,
                AmethystSecondaryLight, OnAmethystSecondaryLight, AmethystSecondaryContainerLight, OnAmethystSecondaryContainerLight
            )
        }
        AppColorPalette.AMBER -> if (useDark) {
            buildDarkScheme(
                AmberPrimaryDark, OnAmberPrimaryDark, AmberContainerDark, OnAmberContainerDark,
                AmberSecondaryDark, OnAmberSecondaryDark, AmberSecondaryContainerDark, OnAmberSecondaryContainerDark,
                background = Color(0xFF17120B),
                surface = Color(0xFF271F14)
            )
        } else {
            buildLightScheme(
                AmberPrimaryLight, OnAmberPrimaryLight, AmberContainerLight, OnAmberContainerLight,
                AmberSecondaryLight, OnAmberSecondaryLight, AmberSecondaryContainerLight, OnAmberSecondaryContainerLight
            )
        }
        AppColorPalette.ROSE -> if (useDark) {
            buildDarkScheme(
                RosePrimaryDark, OnRosePrimaryDark, RoseContainerDark, OnRoseContainerDark,
                RoseSecondaryDark, OnRoseSecondaryDark, RoseSecondaryContainerDark, OnRoseSecondaryContainerDark,
                background = Color(0xFF180B12),
                surface = Color(0xFF291520)
            )
        } else {
            buildLightScheme(
                RosePrimaryLight, OnRosePrimaryLight, RoseContainerLight, OnRoseContainerLight,
                RoseSecondaryLight, OnRoseSecondaryLight, RoseSecondaryContainerLight, OnRoseSecondaryContainerLight
            )
        }
        AppColorPalette.SLATE -> if (useDark) {
            buildDarkScheme(
                SlatePrimaryDark, OnSlatePrimaryDark, SlateContainerDark, OnSlateContainerDark,
                SlateSecondaryDark, OnSlateSecondaryDark, SlateSecondaryContainerDark, OnSlateSecondaryContainerDark,
                background = Color(0xFF0A0D12),
                surface = Color(0xFF181C24)
            )
        } else {
            buildLightScheme(
                SlatePrimaryLight, OnSlatePrimaryLight, SlateContainerLight, OnSlateContainerLight,
                SlateSecondaryLight, OnSlateSecondaryLight, SlateSecondaryContainerLight, OnSlateSecondaryContainerLight
            )
        }
    }
}

val OrganizerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun DigitalAccountOrganizerTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    colorPalette: AppColorPalette = AppColorPalette.EMERALD,
    content: @Composable () -> Unit
) {
    val useDark = when (themePreference) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }

    MaterialTheme(
        colorScheme = resolveColorScheme(colorPalette, useDark),
        typography = Typography,
        shapes = OrganizerShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    DigitalAccountOrganizerTheme(
        themePreference = if (darkTheme) ThemePreference.DARK else ThemePreference.LIGHT,
        content = content
    )
}
