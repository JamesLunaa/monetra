package io.github.jameslunaa.monetra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.monetra.ui.theme.MonetraTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(keys: Collection<String>, selected: String, colorIndex: Int, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.forEach { key ->
            val on = key == selected
            Box(
                Modifier.size(48.dp).clip(MaterialTheme.shapes.medium)
                    .background(if (on) categoryColor(colorIndex) else MaterialTheme.colorScheme.surfaceContainerHighest)
                    .selectable(on, role = Role.RadioButton) { onSelect(key) }
                    .semantics { contentDescription = key },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons[key], null, tint = if (on) MonetraTheme.colors.onCategory else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonetraTheme.colors.categories.forEachIndexed { i, color ->
            val on = i == selected
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(color)
                    .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                    .selectable(on, role = Role.RadioButton) { onSelect(i) }
                    .semantics { contentDescription = "Color ${i + 1}" },
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Icons.Rounded.Check, null, tint = MonetraTheme.colors.onCategory)
            }
        }
    }
}
