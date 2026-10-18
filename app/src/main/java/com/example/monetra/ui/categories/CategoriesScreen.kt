package com.example.monetra.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.monetra.data.db.CategoryEntity
import com.example.monetra.data.db.CategoryType
import com.example.monetra.di.AppContainer
import com.example.monetra.ui.appViewModel
import com.example.monetra.ui.components.FieldHeader
import com.example.monetra.ui.components.IconTile
import com.example.monetra.ui.components.RowMenu
import com.example.monetra.ui.theme.MonetraTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CategoriesViewModel(private val c: AppContainer) : ViewModel() {
    val categories = c.categories.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun save(cat: CategoryEntity) = viewModelScope.launch { c.categories.save(cat) }

    fun move(list: List<CategoryEntity>, from: Int, to: Int) {
        if (to !in list.indices) return
        viewModelScope.launch { c.categories.reorder(list.toMutableList().apply { add(to, removeAt(from)) }) }
    }

    suspend fun delete(cat: CategoryEntity) = c.categories.deleteOrArchive(cat)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(contentPadding: PaddingValues, snackbar: SnackbarHostState, onMenu: () -> Unit) {
    val vm = appViewModel { CategoriesViewModel(it) }
    val all by vm.categories.collectAsStateWithLifecycle()
    var type by rememberSaveable { mutableStateOf(CategoryType.EXPENSE) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val list = all.orEmpty().filter { it.type == type }
    val c = MonetraTheme.colors

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        FieldHeader("Categories", onMenu) {
            val options = listOf(CategoryType.EXPENSE to "Expense", CategoryType.INCOME to "Income")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (t, label) ->
                    SegmentedButton(
                        selected = type == t, onClick = { type = t },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary,
                            activeContentColor = MaterialTheme.colorScheme.onPrimary,
                            activeBorderColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = c.field, inactiveContentColor = c.onField,
                            inactiveBorderColor = c.onFieldVariant.copy(alpha = 0.5f),
                        ),
                    ) { Text("$label · ${all.orEmpty().count { it.type == t }}") }
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = contentPadding.calculateBottomPadding() + 88.dp)) {
            itemsIndexed(list, key = { _, it -> it.id }) { i, cat ->
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    IconTile(cat.icon, cat.colorIndex)
                    Text(cat.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RowMenu("Options for ${cat.name}", listOf<Pair<String, (() -> Unit)?>>(
                        "Edit" to { editing = cat },
                        "Move up" to ({ vm.move(list, i, i - 1) }).takeIf { i > 0 },
                        "Move down" to ({ vm.move(list, i, i + 1) }).takeIf { i < list.lastIndex },
                        "Delete" to {
                            scope.launch {
                                val deleted = vm.delete(cat)
                                snackbar.showSnackbar(if (deleted) "${cat.name} deleted" else "${cat.name} has records, so it was archived instead")
                            }
                        },
                    ))
                }
            }
            item {
                OutlinedButton(onClick = { creating = true }, modifier = Modifier.padding(20.dp)) {
                    Icon(Icons.Rounded.Add, null); Text("  Add category")
                }
            }
        }
    }
    if (creating || editing != null) {
        CategoryEditorDialog(
            initial = editing, type = editing?.type ?: type,
            onSave = { vm.save(it); creating = false; editing = null },
            onDismiss = { creating = false; editing = null },
        )
    }
}
