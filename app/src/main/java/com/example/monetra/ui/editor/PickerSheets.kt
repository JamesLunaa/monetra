package com.example.monetra.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.monetra.data.db.AccountEntity
import com.example.monetra.data.db.CategoryEntity
import com.example.monetra.data.db.CategoryType
import com.example.monetra.data.db.TxType
import com.example.monetra.ui.categories.CategoryEditorDialog
import com.example.monetra.ui.components.IconTile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountPickerSheet(accounts: List<AccountEntity>, selected: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text("Account", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(8.dp))
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            accounts.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(a.id) }.padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconTile(a.icon, a.colorIndex)
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    if (a.id == selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (accounts.isEmpty()) {
                Text("No other account yet. Add one in the Accounts tab.", modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    categories: List<CategoryEntity>,
    type: TxType,
    selected: Long?,
    onPick: (Long) -> Unit,
    onCreate: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text("Category", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            modifier = Modifier.navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(categories, key = { it.id }) { cat ->
                GridCell(cat.name, selected = cat.id == selected, onClick = { onPick(cat.id) }) {
                    IconTile(cat.icon, cat.colorIndex, size = 48.dp)
                }
            }
            item {
                GridCell("New", selected = false, onClick = { creating = true }) {
                    Box(
                        Modifier.size(48.dp).clip(MaterialTheme.shapes.medium)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, contentDescription = null) }
                }
            }
        }
    }
    if (creating) {
        CategoryEditorDialog(
            initial = null,
            type = if (type == TxType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE,
            onSave = { creating = false; onCreate(it) },
            onDismiss = { creating = false },
        )
    }
}

@Composable
private fun GridCell(label: String, selected: Boolean, onClick: () -> Unit, tile: @Composable () -> Unit) {
    Column(
        Modifier.clip(MaterialTheme.shapes.medium)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tile()
        // Long names shrink to fit one line instead of breaking mid-word.
        BasicText(
            label, maxLines = 1,
            style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
            autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp),
        )
    }
}
