package io.github.jameslunaa.monetra.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

enum class ThemeMode { DARK, LIGHT, SYSTEM }

private val MonetraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val LocalMonetraColors = staticCompositionLocalOf { DarkMonetraColors }
private val LocalAmounts = staticCompositionLocalOf { MonetraAmounts }

@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

@Composable
fun MonetraTheme(
    mode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = mode.isDark()
    CompositionLocalProvider(
        LocalMonetraColors provides if (dark) DarkMonetraColors else LightMonetraColors,
        LocalAmounts provides MonetraAmounts,
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = MonetraTypography,
            shapes = MonetraShapes,
            content = content,
        )
    }
}

object MonetraTheme {
    val colors: MonetraColors
        @Composable @ReadOnlyComposable get() = LocalMonetraColors.current
    val amounts: AmountTypography
        @Composable @ReadOnlyComposable get() = LocalAmounts.current
}
