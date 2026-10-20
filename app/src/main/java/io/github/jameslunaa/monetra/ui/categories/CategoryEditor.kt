package io.github.jameslunaa.monetra.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.jameslunaa.monetra.data.db.CategoryEntity
import io.github.jameslunaa.monetra.data.db.CategoryType
import io.github.jameslunaa.monetra.ui.components.AppIcons
import io.github.jameslunaa.monetra.ui.components.ColorPicker
import io.github.jameslunaa.monetra.ui.components.IconPicker

/** Create ([initial] null) or edit a category. */
@Composable
fun CategoryEditorDialog(
    initial: CategoryEntity?,
    type: CategoryType,
    onSave: (CategoryEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var icon by remember { mutableStateOf(initial?.icon ?: "other") }
    var color by remember { mutableIntStateOf(initial?.colorIndex ?: 8) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New ${type.name.lowercase()} category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
                Text("Color", style = MaterialTheme.typography.titleSmall)
                ColorPicker(color) { color = it }
                Text("Icon", style = MaterialTheme.typography.titleSmall)
                IconPicker(AppIcons.all.keys, icon, color) { icon = it }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val base = initial ?: CategoryEntity(name = "", type = type, icon = icon, colorIndex = color)
                onSave(base.copy(name = name.trim(), icon = icon, colorIndex = color))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
