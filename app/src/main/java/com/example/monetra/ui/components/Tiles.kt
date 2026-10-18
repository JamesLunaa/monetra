package com.example.monetra.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.monetra.ui.theme.MonetraTheme

@Composable
fun categoryColor(index: Int?): Color {
    val list = MonetraTheme.colors.categories
    return list[Math.floorMod(index ?: 0, list.size)]
}

/** A saturated color tile carrying a category or account icon. */
@Composable
fun IconTile(icon: String?, colorIndex: Int?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).background(categoryColor(colorIndex), MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons[icon], contentDescription = null, tint = MonetraTheme.colors.onCategory, modifier = Modifier.size(size * 0.55f))
    }
}

/** Neutral tile for records with no category: transfers, and cash lent or borrowed. */
@Composable
fun TransferTile(modifier: Modifier = Modifier, size: Dp = 40.dp, icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.AutoMirrored.Rounded.CompareArrows) {
    Box(
        modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.medium),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(size * 0.55f))
    }
}
