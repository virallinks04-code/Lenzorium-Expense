package com.lenzorium.expenses.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.lenzorium.expenses.data.Tags

/** Merchant / payee field with one-tap suggestions from earlier entries. */
@Composable
fun MerchantField(
    value: String,
    onChange: (String) -> Unit,
    onPick: (String) -> Unit,
    suggestions: List<String>,
    label: String,
    placeholder: String,
) {
    Column {
        TextInput(value, onChange, label, placeholder = placeholder)
        val q = value.trim().lowercase()
        val matches = suggestions.asSequence()
            .filter { it.lowercase() != q && (q.isEmpty() || it.lowercase().contains(q)) }
            .take(6).toList()
        if (matches.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                matches.forEach { m ->
                    AssistChip(
                        onClick = { onPick(m) },
                        label = { Text(m, maxLines = 1) },
                        leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                }
            }
        }
    }
}

/** Tags as removable chips, a field to add more, and the user's frequent tags as suggestions. */
@Composable
fun TagEditor(tags: List<String>, onChange: (List<String>) -> Unit, known: List<String>) {
    var text by remember { mutableStateOf("") }
    fun add(raw: String) {
        val t = Tags.clean(raw)
        if (t.isNotEmpty() && t !in tags && tags.size < 10) onChange(tags + t)
        text = ""
    }
    Column {
        Text("Tags", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { t ->
                    InputChip(
                        selected = true,
                        onClick = { onChange(tags - t) },
                        label = { Text("#$t") },
                        trailingIcon = { Icon(Icons.Rounded.Close, "Remove $t", Modifier.size(InputChipDefaults.IconSize)) },
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v -> if (v.endsWith(",") || v.endsWith(" ") && v.isNotBlank()) add(v.dropLast(1)) else text = v.take(30) },
            placeholder = { Text("Add a tag, e.g. family, work, trip") },
            leadingIcon = { Icon(Icons.Rounded.Tag, null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(text) }),
            modifier = Modifier.fillMaxWidth(),
        )
        val sugg = known.filter { it !in tags && (text.isBlank() || it.startsWith(Tags.clean(text))) }.take(6)
        if (sugg.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sugg.forEach { s -> AssistChip(onClick = { add(s) }, label = { Text("#$s") }) }
            }
        }
    }
}
