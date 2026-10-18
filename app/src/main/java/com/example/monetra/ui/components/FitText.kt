package com.example.monetra.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** One line of text that shrinks (down to [min]) instead of wrapping or clipping. */
@Composable
fun FitText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = LocalContentColor.current, min: TextUnit = 10.sp) {
    BasicText(
        text, modifier = modifier, maxLines = 1, softWrap = false,
        style = style.copy(color = color),
        autoSize = TextAutoSize.StepBased(minFontSize = min, maxFontSize = style.fontSize),
    )
}
