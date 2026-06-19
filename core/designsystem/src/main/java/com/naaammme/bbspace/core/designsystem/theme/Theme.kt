package com.naaammme.bbspace.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeTonalSpot

private val MonochromeDarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1A1A1A),
    onPrimaryContainer = Color.White,
    inversePrimary = Color(0xFF2A2A2A),
    secondary = Color(0xFFE0E0E0),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF202020),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFCCCCCC),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF262626),
    onTertiaryContainer = Color.White,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFFD0D0D0),
    surfaceTint = Color.White,
    inverseSurface = Color.White,
    inverseOnSurface = Color.Black,
    outline = Color(0xFF808080),
    outlineVariant = Color(0xFF404040),
    scrim = Color.Black,
    surfaceBright = Color(0xFF161616),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF202020),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainerLowest = Color.Black,
    surfaceDim = Color.Black,
    error = Color(0xFFE0E0E0),
    onError = Color.Black,
    errorContainer = Color(0xFF2A2A2A),
    onErrorContainer = Color.White
)

private val MonochromeLightColorScheme = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEAEA),
    onPrimaryContainer = Color.Black,
    inversePrimary = Color(0xFF3A3A3A),
    secondary = Color(0xFF2E2E2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF4A4A4A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E6E6),
    onTertiaryContainer = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFF0F0F0),
    onSurfaceVariant = Color(0xFF505050),
    surfaceTint = Color.Black,
    inverseSurface = Color(0xFF1A1A1A),
    inverseOnSurface = Color.White,
    outline = Color(0xFF707070),
    outlineVariant = Color(0xFFC8C8C8),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceContainer = Color(0xFFF7F7F7),
    surfaceContainerHigh = Color(0xFFF2F2F2),
    surfaceContainerHighest = Color(0xFFEDEDED),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainerLowest = Color.White,
    surfaceDim = Color(0xFFF3F3F3),
    error = Color(0xFF2E2E2E),
    onError = Color.White,
    errorContainer = Color(0xFFE2E2E2),
    onErrorContainer = Color.Black
)

@Composable
fun BiliTheme(
    config: ThemeConfig = ThemeConfig(),
    content: @Composable () -> Unit
) {
    val darkTheme = when (config.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val baseDensity = LocalDensity.current
    val useMonochromeScheme = config.seedColor.isPureBlack()
    val uiScale = config.uiScale

    val colorScheme = remember(
        context,
        darkTheme,
        config.seedColor,
        config.useDynamicColor,
        config.swapBaseColors,
        config.isPureBlack,
        useMonochromeScheme
    ) {
        val base = when {
            useMonochromeScheme -> createMonochromeColorScheme(darkTheme)
            config.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> createColorScheme(config.seedColor, darkTheme)
        }
        val normalized = if (useMonochromeScheme) {
            base
        } else {
            applyNeutralBackgroundScheme(base, darkTheme, config.isPureBlack)
        }
        if (config.swapBaseColors) {
            applyBaseColorSwap(normalized)
        } else {
            normalized
        }
    }

    val density = remember(baseDensity, uiScale) {
        Density(
            density = baseDensity.density * uiScale,
            fontScale = baseDensity.fontScale / uiScale
        )
    }
    val typography = remember(config.fontScale) { createTypography(config.fontScale) }
    val shapes = remember(config.cornerStyle) { buildShapes(config.cornerStyle) }

    CompositionLocalProvider(LocalDensity provides density) {
        ProvideAnimations(config.animationSpeed) {
            ProvidePullRefresh(config.pullRefreshDistanceDp) {
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = typography,
                    shapes = shapes,
                    content = content
                )
            }
        }
    }
}

private fun createColorScheme(seedColor: Color, isDark: Boolean): ColorScheme {
    val hct = Hct.fromInt(seedColor.toArgb())
    val scheme = if (hct.chroma < 5.0) {
        SchemeNeutral(hct, isDark, 0.0)
    } else {
        SchemeTonalSpot(hct, isDark, 0.0)
    }
    val c = MaterialDynamicColors()

    val base = if (isDark) {
        darkColorScheme(
            primary = Color(c.primary().getArgb(scheme)),
            onPrimary = Color(c.onPrimary().getArgb(scheme)),
            primaryContainer = Color(c.primaryContainer().getArgb(scheme)),
            onPrimaryContainer = Color(c.onPrimaryContainer().getArgb(scheme)),
            inversePrimary = Color(c.inversePrimary().getArgb(scheme)),
            secondary = Color(c.secondary().getArgb(scheme)),
            onSecondary = Color(c.onSecondary().getArgb(scheme)),
            secondaryContainer = Color(c.secondaryContainer().getArgb(scheme)),
            onSecondaryContainer = Color(c.onSecondaryContainer().getArgb(scheme)),
            tertiary = Color(c.tertiary().getArgb(scheme)),
            onTertiary = Color(c.onTertiary().getArgb(scheme)),
            tertiaryContainer = Color(c.tertiaryContainer().getArgb(scheme)),
            onTertiaryContainer = Color(c.onTertiaryContainer().getArgb(scheme)),
            background = Color(c.background().getArgb(scheme)),
            onBackground = Color(c.onBackground().getArgb(scheme)),
            surface = Color(c.surface().getArgb(scheme)),
            onSurface = Color(c.onSurface().getArgb(scheme)),
            surfaceVariant = Color(c.surfaceVariant().getArgb(scheme)),
            onSurfaceVariant = Color(c.onSurfaceVariant().getArgb(scheme)),
            surfaceTint = Color(c.surfaceTint().getArgb(scheme)),
            inverseSurface = Color(c.inverseSurface().getArgb(scheme)),
            inverseOnSurface = Color(c.inverseOnSurface().getArgb(scheme)),
            outline = Color(c.outline().getArgb(scheme)),
            outlineVariant = Color(c.outlineVariant().getArgb(scheme)),
            scrim = Color(c.scrim().getArgb(scheme)),
            surfaceBright = Color(c.surfaceBright().getArgb(scheme)),
            surfaceContainer = Color(c.surfaceContainer().getArgb(scheme)),
            surfaceContainerHigh = Color(c.surfaceContainerHigh().getArgb(scheme)),
            surfaceContainerHighest = Color(c.surfaceContainerHighest().getArgb(scheme)),
            surfaceContainerLow = Color(c.surfaceContainerLow().getArgb(scheme)),
            surfaceContainerLowest = Color(c.surfaceContainerLowest().getArgb(scheme)),
            surfaceDim = Color(c.surfaceDim().getArgb(scheme)),
            error = Color(c.error().getArgb(scheme)),
            onError = Color(c.onError().getArgb(scheme)),
            errorContainer = Color(c.errorContainer().getArgb(scheme)),
            onErrorContainer = Color(c.onErrorContainer().getArgb(scheme)),
        )
    } else {
        lightColorScheme(
            primary = Color(c.primary().getArgb(scheme)),
            onPrimary = Color(c.onPrimary().getArgb(scheme)),
            primaryContainer = Color(c.primaryContainer().getArgb(scheme)),
            onPrimaryContainer = Color(c.onPrimaryContainer().getArgb(scheme)),
            inversePrimary = Color(c.inversePrimary().getArgb(scheme)),
            secondary = Color(c.secondary().getArgb(scheme)),
            onSecondary = Color(c.onSecondary().getArgb(scheme)),
            secondaryContainer = Color(c.secondaryContainer().getArgb(scheme)),
            onSecondaryContainer = Color(c.onSecondaryContainer().getArgb(scheme)),
            tertiary = Color(c.tertiary().getArgb(scheme)),
            onTertiary = Color(c.onTertiary().getArgb(scheme)),
            tertiaryContainer = Color(c.tertiaryContainer().getArgb(scheme)),
            onTertiaryContainer = Color(c.onTertiaryContainer().getArgb(scheme)),
            background = Color(c.background().getArgb(scheme)),
            onBackground = Color(c.onBackground().getArgb(scheme)),
            surface = Color(c.surface().getArgb(scheme)),
            onSurface = Color(c.onSurface().getArgb(scheme)),
            surfaceVariant = Color(c.surfaceVariant().getArgb(scheme)),
            onSurfaceVariant = Color(c.onSurfaceVariant().getArgb(scheme)),
            surfaceTint = Color(c.surfaceTint().getArgb(scheme)),
            inverseSurface = Color(c.inverseSurface().getArgb(scheme)),
            inverseOnSurface = Color(c.inverseOnSurface().getArgb(scheme)),
            outline = Color(c.outline().getArgb(scheme)),
            outlineVariant = Color(c.outlineVariant().getArgb(scheme)),
            scrim = Color(c.scrim().getArgb(scheme)),
            surfaceBright = Color(c.surfaceBright().getArgb(scheme)),
            surfaceContainer = Color(c.surfaceContainer().getArgb(scheme)),
            surfaceContainerHigh = Color(c.surfaceContainerHigh().getArgb(scheme)),
            surfaceContainerHighest = Color(c.surfaceContainerHighest().getArgb(scheme)),
            surfaceContainerLow = Color(c.surfaceContainerLow().getArgb(scheme)),
            surfaceContainerLowest = Color(c.surfaceContainerLowest().getArgb(scheme)),
            surfaceDim = Color(c.surfaceDim().getArgb(scheme)),
            error = Color(c.error().getArgb(scheme)),
            onError = Color(c.onError().getArgb(scheme)),
            errorContainer = Color(c.errorContainer().getArgb(scheme)),
            onErrorContainer = Color(c.onErrorContainer().getArgb(scheme)),
        )
    }

    return base
}

private fun applyNeutralBackgroundScheme(
    base: ColorScheme,
    isDark: Boolean,
    usePureBackground: Boolean
): ColorScheme {
    if (!usePureBackground) {
        return base
    }
    return if (isDark) {
        base.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceBright = Color(0xFF161616),
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
            surfaceContainer = Color(0xFF121212),
            surfaceContainerHigh = Color(0xFF181818),
            surfaceContainerHighest = Color(0xFF202020)
        )
    } else {
        base.copy(
            background = Color.White,
            surface = Color.White,
            surfaceDim = Color(0xFFF5F5F5),
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFFAFAFA),
            surfaceContainer = Color(0xFFF7F7F7),
            surfaceContainerHigh = Color(0xFFF2F2F2),
            surfaceContainerHighest = Color(0xFFEDEDED)
        )
    }
}

private fun createMonochromeColorScheme(isDark: Boolean): ColorScheme {
    return if (isDark) MonochromeDarkColorScheme else MonochromeLightColorScheme
}

private fun applyBaseColorSwap(base: ColorScheme): ColorScheme {
    return base.copy(
        background = base.secondaryContainer,
        onBackground = base.onSecondaryContainer,
        surface = base.secondaryContainer,
        onSurface = base.onSecondaryContainer,
        surfaceDim = base.secondaryContainer,
        surfaceBright = base.secondaryContainer,
        surfaceContainerLowest = base.background,
        surfaceContainerLow = base.background,
        surfaceContainer = base.background,
        surfaceContainerHigh = base.background,
        surfaceContainerHighest = base.background,
        surfaceVariant = base.background,
        onSurfaceVariant = base.onBackground,
        surfaceTint = base.onSecondaryContainer,
        inverseSurface = base.secondaryContainer,
        inverseOnSurface = base.onSecondaryContainer,
        secondaryContainer = base.background,
        onSecondaryContainer = base.onBackground
    )
}

private fun Color.isPureBlack(): Boolean = toArgb() == Color.Black.toArgb()
