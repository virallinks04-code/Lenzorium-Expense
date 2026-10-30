package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lenzorium.expenses.data.Category
import com.lenzorium.expenses.data.CategoryKind
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SaveResult
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.components.CategoryIcons
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.MessageDialog
import com.lenzorium.expenses.ui.components.ScreenScaffold
import com.lenzorium.expenses.ui.components.TextInput
import androidx.compose.material3.FloatingActionButton
import kotlinx.coroutines.launch

@Composable
fun CategoriesScreen(state: LedgerState, repo: Repository, nav: NavController) {
    val scope = rememberCoroutineScope()
    var kindName by rememberSaveable { mutableStateOf(CategoryKind.EXPENSE.name) }
    val kind = CategoryKind.valueOf(kindName)
    var editing by remember { mutableStateOf<Category?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = state.categories.filter { it.kind == kind }

    ScreenScaffold("Categories", nav, floatingActionButton = {
        FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Rounded.Add, "Add category") }
    }) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    CategoryKind.entries.forEachIndexed { i, k ->
                        SegmentedButton(selected = k == kind, onClick = { kindName = k.name }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                            Text(if (k == CategoryKind.EXPENSE) "Expense" else "Income")
                        }
                    }
                }
            }
            items(list, key = { it.id }) { cat ->
                val used = state.txns.count { it.categoryId == cat.id }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(CategoryIcons.of(cat.iconKey), Color(cat.colorArgb))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(cat.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            (if (used == 1) "1 transaction" else "$used transactions") + if (cat.isFallback) " · fallback" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editing = cat }) { Icon(Icons.Rounded.Edit, "Edit ${cat.name}") }
                    if (!cat.isFallback) IconButton(onClick = { deleting = cat }) { Icon(Icons.Rounded.Delete, "Delete ${cat.name}") }
                }
            }
        }
    }

    if (adding || editing != null) {
        val e = editing
        CategoryEditDialog(
            kind = kind,
            existing = e,
            onSave = { name, icon, color ->
                scope.launch {
                    val r = if (e == null) repo.addCategory(name, kind, icon, color)
                    else repo.updateCategory(e.copy(name = name, iconKey = icon, colorArgb = color))
                    when (r) {
                        is SaveResult.Ok -> { adding = false; editing = null }
                        is SaveResult.Error -> error = r.message
                    }
                }
            },
            onDismiss = { adding = false; editing = null },
        )
    }
    deleting?.let { cat ->
        val used = state.txns.count { it.categoryId == cat.id }
        val fallback = state.categories.firstOrNull { it.kind == cat.kind && it.isFallback }?.name ?: "Other"
        ConfirmDialog(
            "Delete \"${cat.name}\"?",
            if (used > 0) "Its $used transactions will be moved to \"$fallback\". No amounts change." else "This category isn't used by any transactions.",
            "Delete", destructive = true,
            onConfirm = {
                deleting = null
                scope.launch { (repo.deleteCategory(cat) as? SaveResult.Error)?.let { error = it.message } }
            },
            onDismiss = { deleting = null },
        )
    }
    error?.let { MessageDialog("Categories", it) { error = null } }
}

@Composable
fun CategoryEditDialog(kind: CategoryKind, existing: Category?, onSave: (String, String, Long) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.iconKey ?: "other") }
    var color by remember { mutableStateOf(existing?.colorArgb ?: CategoryIcons.colorChoices.first()) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New ${if (kind == CategoryKind.EXPENSE) "expense" else "income"} category" else "Edit category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextInput(name, { name = it }, "Name")
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.choices.forEach { (key, vec) ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .background(if (key == icon) Color(color).copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vec, key, tint = if (key == icon) Color(color) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp)) }
                    }
                }
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcons.colorChoices.forEach { col ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .background(Color(col), CircleShape)
                                .then(if (col == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .clickable { color = col }
                        )
                    }
                }
                ErrorBanner(err)
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isBlank()) err = "Enter a name." else onSave(name.trim(), icon, color) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
