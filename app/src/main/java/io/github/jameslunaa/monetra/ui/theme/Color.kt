package io.github.jameslunaa.monetra.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// "Tiangge After Dark": night ground, ube tarp field, price-card ink, sodium-lamp amber.
internal val DarkScheme = darkColorScheme(
    primary = Color(0xFFB794F6),
    onPrimary = Color(0xFF1A0F2B),
    primaryContainer = Color(0xFF3D2466),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCDBEE8),
    onSecondary = Color(0xFF2A1F3D),
    secondaryContainer = Color(0xFF2F2640),
    onSecondaryContainer = Color(0xFFE6DCF7),
    tertiary = Color(0xFFFFB84D),
    onTertiary = Color(0xFF2B1A00),
    tertiaryContainer = Color(0xFF4A3410),
    onTertiaryContainer = Color(0xFFFFDDB0),
    background = Color(0xFF0E0C13),
    onBackground = Color(0xFFF2EDE4),
    surface = Color(0xFF0E0C13),
    onSurface = Color(0xFFF2EDE4),
    surfaceVariant = Color(0xFF262033),
    onSurfaceVariant = Color(0xFFA59CB5),
    surfaceTint = Color(0xFFB794F6),
    surfaceDim = Color(0xFF0E0C13),
    surfaceBright = Color(0xFF2C2638),
    surfaceContainerLowest = Color(0xFF0A0910),
    surfaceContainerLow = Color(0xFF13101A),
    surfaceContainer = Color(0xFF17131F),
    surfaceContainerHigh = Color(0xFF1E1928),
    surfaceContainerHighest = Color(0xFF262033),
    inverseSurface = Color(0xFFF2EDE4),
    inverseOnSurface = Color(0xFF1B1622),
    inversePrimary = Color(0xFF6B3FB0),
    outline = Color(0xFF6E6580),
    outlineVariant = Color(0xFF2E2838),
    error = Color(0xFFFF7A6B),
    onError = Color(0xFF3A0A05),
    errorContainer = Color(0xFF5C1A12),
    onErrorContainer = Color(0xFFFFDAD4),
    scrim = Color(0xFF000000),
)

// "Tiangge by day": warm off-white ground, deeper ube field, purple-black ink.
internal val LightScheme = lightColorScheme(
    primary = Color(0xFF6B3FB0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF2A1050),
    secondary = Color(0xFF5E5470),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6DFF0),
    onSecondaryContainer = Color(0xFF211A2E),
    tertiary = Color(0xFF8A5100),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB0),
    onTertiaryContainer = Color(0xFF2B1A00),
    background = Color(0xFFF7F4F0),
    onBackground = Color(0xFF1B1622),
    surface = Color(0xFFF7F4F0),
    onSurface = Color(0xFF1B1622),
    surfaceVariant = Color(0xFFE5DFDC),
    onSurfaceVariant = Color(0xFF5B5268),
    surfaceTint = Color(0xFF6B3FB0),
    surfaceDim = Color(0xFFDDD6D4),
    surfaceBright = Color(0xFFF7F4F0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2EEEA),
    surfaceContainer = Color(0xFFECE7E3),
    surfaceContainerHigh = Color(0xFFE5DFDC),
    surfaceContainerHighest = Color(0xFFDDD6D4),
    inverseSurface = Color(0xFF302A38),
    inverseOnSurface = Color(0xFFF4EFF7),
    inversePrimary = Color(0xFFB794F6),
    outline = Color(0xFF8C8398),
    outlineVariant = Color(0xFFD6CFDB),
    // One red per theme: errors share the expense color.
    error = Color(0xFFC2372B),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    scrim = Color(0xFF000000),
)

/** Colors Material's scheme has no role for. Read via [MonetraTheme.colors]. */
@Immutable
data class MonetraColors(
    /** The ube "tarp" field that owns the header band. */
    val field: Color,
    val onField: Color,
    val onFieldVariant: Color,
    /** Income/expense tints legible on the field (light mode's ink-dark greens/reds fail there). */
    val fieldIncome: Color,
    val fieldExpense: Color,
    val income: Color,
    val expense: Color,
    val warning: Color,
    /** Logo mark and its shadow strokes; same in both themes since it always sits on the field. */
    val logo: Color,
    val logoShadow: Color,
    /** Glyph color drawn on top of a category color tile. */
    val onCategory: Color,
    /** Saturated category colors; a category stores an index into this list. */
    val categories: List<Color>,
)

internal val DarkMonetraColors = MonetraColors(
    field = Color(0xFF2A1745),
    onField = Color(0xFFF2EDE4),
    onFieldVariant = Color(0xFFD9C8FF),
    fieldIncome = Color(0xFF5FD38D),
    fieldExpense = Color(0xFFFF7A6B),
    income = Color(0xFF5FD38D),
    expense = Color(0xFFFF7A6B),
    warning = Color(0xFFFFB84D),
    logo = Color(0xFFB794F6),
    logoShadow = Color(0xFF3D2466),
    onCategory = Color(0xFF0E0C13),
    categories = listOf(
        // Slots 0 and 4 are shifted off the expense/income colors so a tile never reads as a money sign.
        Color(0xFFF4845F), Color(0xFFF08A3E), Color(0xFFE3B23C), Color(0xFFA6D25B),
        Color(0xFF4DB89A), Color(0xFF3FC1B0), Color(0xFF4FB3F0), Color(0xFF5B8DEF),
        Color(0xFF9AB0C8), Color(0xFFE36FC7), Color(0xFFF2709A), Color(0xFFC9A27A),
    ),
)

internal val LightMonetraColors = MonetraColors(
    field = Color(0xFF4A2A7A),
    onField = Color(0xFFF7F2EA),
    onFieldVariant = Color(0xFFDCCBFA),
    fieldIncome = Color(0xFF8EE6B0),
    fieldExpense = Color(0xFFFFB0A6),
    income = Color(0xFF1E7A47),
    expense = Color(0xFFC2372B),
    warning = Color(0xFF8A5100),
    logo = Color(0xFFB794F6),
    logoShadow = Color(0xFF3D2466),
    onCategory = Color(0xFFFFFFFF),
    categories = listOf(
        Color(0xFFB4532E), Color(0xFFC0601A), Color(0xFF9C730C), Color(0xFF5A841D),
        Color(0xFF1F8A73), Color(0xFF11807A), Color(0xFF1C78B5), Color(0xFF3563C9),
        Color(0xFF56708F), Color(0xFFAD3A8F), Color(0xFFBF3F69), Color(0xFF8A6843),
    ),
)
