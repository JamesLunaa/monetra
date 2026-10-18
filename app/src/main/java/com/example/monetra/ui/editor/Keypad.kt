package com.example.monetra.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.monetra.core.calc.Key
import com.example.monetra.core.calc.Op
import com.example.monetra.ui.theme.MonetraTheme

private data class Pad(val label: String, val key: Key, val spoken: String = label)

private val rows = listOf(
    listOf(Pad("+", Key.Operator(Op.PLUS), "plus"), Pad("7", Key.Digit(7)), Pad("8", Key.Digit(8)), Pad("9", Key.Digit(9))),
    listOf(Pad("−", Key.Operator(Op.MINUS), "minus"), Pad("4", Key.Digit(4)), Pad("5", Key.Digit(5)), Pad("6", Key.Digit(6))),
    listOf(Pad("×", Key.Operator(Op.TIMES), "times"), Pad("1", Key.Digit(1)), Pad("2", Key.Digit(2)), Pad("3", Key.Digit(3))),
    listOf(Pad("÷", Key.Operator(Op.DIVIDE), "divided by"), Pad("0", Key.Digit(0)), Pad(".", Key.Dot, "point"), Pad("=", Key.Equals, "equals")),
)

/**
 * Calculator keypad. Operators sit in the left column. The one lit key is "=" while a
 * calculation is pending and turns into Save once the amount is a plain number.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keypad(onKey: (Key) -> Unit, pending: Boolean, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { pad ->
                    val (bg, fg) = when (pad.key) {
                        is Key.Operator -> scheme.secondaryContainer to scheme.onSecondaryContainer
                        Key.Equals -> scheme.primary to scheme.onPrimary
                        else -> scheme.surfaceContainerHigh to scheme.onSurface
                    }
                    if (pad.key == Key.Equals && !pending) {
                        SaveCap(bg, fg, Modifier.weight(1f), onSave)
                    } else {
                        KeyCap(pad.label, pad.spoken, bg, fg, Modifier.weight(1f)) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onKey(pad.key)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SaveCap(bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(MaterialTheme.shapes.small).background(bg)
            .clickable(role = Role.Button, onClickLabel = "Save record", onClick = onClick)
            .semantics { contentDescription = "Save" },
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Check, contentDescription = null, tint = fg, modifier = Modifier.size(32.dp)) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeyCap(label: String, spoken: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.fillMaxHeight().clip(MaterialTheme.shapes.small).background(bg)
            .combinedClickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = spoken },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MonetraTheme.amounts.total, color = fg)
    }
}
