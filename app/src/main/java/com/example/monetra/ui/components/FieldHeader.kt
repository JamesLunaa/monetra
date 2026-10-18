package com.example.monetra.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.monetra.ui.theme.MonetraTheme

/** The ube "tarp" band at the top of every main screen; draws behind the status bar. */
@Composable
fun FieldHeader(
    title: String,
    onMenu: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    navigation: (@Composable () -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = MonetraTheme.colors
    CompositionLocalProvider(LocalContentColor provides c.onField) {
        Column(
            modifier.fillMaxWidth().background(c.field).statusBarsPadding().padding(bottom = bottomPadding),
        ) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (navigation != null) {
                    navigation()
                } else if (onMenu != null) {
                    IconButton(onClick = onMenu) { Icon(Icons.Rounded.Menu, contentDescription = "Open menu") }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                actions()
            }
            Column(Modifier.padding(horizontal = 20.dp), content = content)
        }
    }
}

/** ‹ October 2026 › — tapping the label jumps back to today. */
@Composable
fun PeriodSwitcher(label: String, onPrevious: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    // Pulled left so the chevron's glyph, not its touch target, lines up with the content edge.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous period")
        }
        TextButton(onClick = onToday, colors = ButtonDefaults.textButtonColors(contentColor = LocalContentColor.current)) {
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next period")
        }
    }
}

/**
 * The hero figure. When [key] changes (a new period) the old figure slides out and the new one
 * slides in from the direction of travel, like a price card being swapped.
 */
@Composable
fun <K> HeroFigure(key: K, forward: Boolean, description: String, content: @Composable (K) -> Unit) {
    AnimatedContent(
        targetState = key,
        transitionSpec = {
            val dir = if (forward) 1 else -1
            (slideInVertically(tween(260)) { it / 2 * dir } + fadeIn(tween(200)))
                .togetherWith(slideOutVertically(tween(200)) { -it / 2 * dir } + fadeOut(tween(160)))
        },
        modifier = Modifier.semantics { contentDescription = description },
        label = "hero",
    ) { content(it) }
}

/** A small label over a secondary total, both on the field. */
@Composable
fun FieldStat(label: String, content: @Composable () -> Unit) {
    // Totals never wrap; at very large font scales they shrink instead (see [FitText]).
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MonetraTheme.colors.onFieldVariant,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        content()
    }
}
