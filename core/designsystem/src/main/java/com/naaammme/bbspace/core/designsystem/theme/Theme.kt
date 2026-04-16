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
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeTonalSpot

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

    val colorScheme = remember(
        context,
        darkTheme,
        config.seedColor,
        config.useDynamicColor,
        config.isPureBlack
    ) {
        val base = when {
            config.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            else -> createColorScheme(config.seedColor, darkTheme, config.isPureBlack)
        }
        if (
            config.useDynamicColor &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            config.isPureBlack
        ) {
            base.copy(
                background = if (darkTheme) Color.Black else Color.White
            )
        } else {
            base
        }
    }

    val typography = remember(config.fontScale) { createTypography(config.fontScale) }
    val shapes = remember(config.cornerStyle) { buildShapes(config.cornerStyle) }

    ProvideAnimations(config.animationSpeed) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

private fun createColorScheme(seedColor: Color, isDark: Boolean, usePureColor: Boolean): ColorScheme {
    val scheme = SchemeTonalSpot(Hct.fromInt(seedColor.toArgb()), isDark, 0.0)
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

    return if (usePureColor) {
        if (isDark) {
            base.copy(
                background = Color.Black
            )
        } else {
            base.copy(
                background = Color.White
            )
        }
    } else {
        base
    }
}
