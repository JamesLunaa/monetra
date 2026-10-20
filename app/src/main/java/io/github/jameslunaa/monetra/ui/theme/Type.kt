package io.github.jameslunaa.monetra.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.jameslunaa.monetra.R

@OptIn(ExperimentalTextApi::class)
private fun archivo(weight: Int, width: Float) = Font(
    R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

private val Archivo = FontFamily(
    archivo(400, 100f),
    archivo(500, 100f),
    archivo(600, 100f),
    archivo(700, 100f),
)

/** Narrow cut used for every money figure, like a hand-set price card. */
private val ArchivoPrice = FontFamily(
    archivo(600, 75f),
    archivo(700, 75f),
    archivo(800, 62f),
)

private const val TABULAR = "tnum"

private fun base(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Archivo,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = tracking.em,
)

internal val MonetraTypography = Typography(
    displayLarge = base(57, 60, 700, -0.02),
    displayMedium = base(45, 48, 700, -0.02),
    displaySmall = base(36, 40, 700, -0.01),
    headlineLarge = base(32, 38, 700, -0.01),
    headlineMedium = base(28, 34, 700, -0.01),
    headlineSmall = base(24, 30, 600),
    titleLarge = base(22, 28, 700),
    titleMedium = base(17, 24, 600),
    titleSmall = base(15, 20, 600),
    bodyLarge = base(17, 24, 400),
    bodyMedium = base(15, 21, 400),
    bodySmall = base(13, 18, 400),
    labelLarge = base(15, 20, 600),
    labelMedium = base(13, 18, 500),
    labelSmall = base(12, 16, 500),
)

/** Money styles. Always tabular so columns of amounts align. Read via [MonetraTheme.amounts]. */
@Immutable
data class AmountTypography(
    /** The period's net on the header field. */
    val hero: TextStyle,
    /** Secondary totals (spent / earned). */
    val total: TextStyle,
    /** Amount at the end of a record row. */
    val row: TextStyle,
)

internal val MonetraAmounts = AmountTypography(
    hero = TextStyle(
        fontFamily = ArchivoPrice, fontWeight = FontWeight(800), fontSize = 64.sp,
        lineHeight = 60.sp, letterSpacing = (-0.01).em, fontFeatureSettings = TABULAR,
    ),
    total = TextStyle(
        fontFamily = ArchivoPrice, fontWeight = FontWeight(700), fontSize = 24.sp,
        lineHeight = 28.sp, fontFeatureSettings = TABULAR,
    ),
    row = TextStyle(
        fontFamily = ArchivoPrice, fontWeight = FontWeight(700), fontSize = 21.sp,
        lineHeight = 26.sp, fontFeatureSettings = TABULAR,
    ),
)
