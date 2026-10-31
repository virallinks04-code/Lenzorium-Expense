package com.lenzorium.expenses.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.lenzorium.expenses.data.AppSettings
import com.lenzorium.expenses.data.OpeningLoanInput
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.domain.Currencies
import com.lenzorium.expenses.domain.CurrencyInfo
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.ui.Biometrics
import com.lenzorium.expenses.ui.components.AmountField
import com.lenzorium.expenses.ui.components.DatePickDialog
import com.lenzorium.expenses.ui.components.Dates
import com.lenzorium.expenses.ui.components.ErrorBanner
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.TextInput
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun SetupScreen(repo: Repository, onRestore: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableStateOf(0) }
    var currencyCode by rememberSaveable { mutableStateOf("PKR") }
    var balanceText by rememberSaveable { mutableStateOf("") }
    var startDay by rememberSaveable { mutableStateOf(LocalDate.now().withDayOfMonth(1).toEpochDay()) }
    val loans = remember { mutableStateListOf<OpeningLoanInput>() }
    var pin by rememberSaveable { mutableStateOf("") }
    var pin2 by rememberSaveable { mutableStateOf("") }
    var usePin by rememberSaveable { mutableStateOf(false) }
    var useBio by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var finishing by remember { mutableStateOf(false) }
    val currency = Currencies.find(currencyCode)
    val steps = 6

    fun next() {
        error = null
        when (step) {
            2 -> {
                val v = if (balanceText.isBlank()) 0L else Money.parse(balanceText, currency.decimals)
                if (v == null) { error = "Enter a valid amount (up to ${currency.decimals} decimal places)."; return }
            }
            5 -> {
                if (usePin) {
                    if (pin.length !in 4..8) { error = "PIN must be 4 to 8 digits."; return }
                    if (pin != pin2) { error = "The two PINs don't match."; return }
                }
                if (finishing) return
                finishing = true
                val opening = if (balanceText.isBlank()) 0L else Money.parse(balanceText, currency.decimals) ?: 0L
                scope.launch {
                    repo.completeSetup(
                        AppSettings(
                            currencyCode = currencyCode,
                            openingBalanceMinor = opening,
                            trackingStartEpochDay = startDay,
                            biometricEnabled = usePin && useBio,
                        ),
                        pin = if (usePin) pin else null,
                        openingLoans = loans.toList(),
                    )
                }
                return
            }
        }
        step++
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        if (step > 0) {
            LinearProgressIndicator(progress = { step / (steps - 1).toFloat() }, modifier = Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f).padding(24.dp)) {
            when (step) {
                0 -> Welcome(onRestore)
                1 -> CurrencyStep(currencyCode) { currencyCode = it }
                2 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    StepHeader("Your current balance", "How much money do you have available right now, across cash and bank? You can enter 0.")
                    AmountField(balanceText, { balanceText = it }, currency, label = "Available balance")
                    Spacer(Modifier.height(8.dp))
                    Text("Don't include money you owe — you can add existing loans in a moment.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                3 -> StartDateStep(startDay) { startDay = it }
                4 -> LoansStep(currency, loans)
                5 -> Column(Modifier.verticalScroll(rememberScrollState())) {
                    StepHeader("Protect the app", "Optional. Ask for a PIN each time the app opens.")
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Use a PIN lock", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        Switch(checked = usePin, onCheckedChange = { usePin = it })
                    }
                    if (usePin) {
                        Spacer(Modifier.height(8.dp))
                        PinField(pin, { pin = it }, "PIN (4–8 digits)")
                        Spacer(Modifier.height(8.dp))
                        PinField(pin2, { pin2 = it }, "Repeat PIN")
                        if (Biometrics.isAvailable(context)) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.weight(1f)) {
                                    Text("Unlock with fingerprint or face", style = MaterialTheme.typography.titleMedium)
                                    Text("Your PIN still works as a backup.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = useBio, onCheckedChange = { useBio = it })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            ErrorBanner(error)
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) OutlinedButton(onClick = { error = null; step-- }, modifier = Modifier.weight(1f)) { Text("Back") }
            Button(onClick = { next() }, enabled = !finishing, modifier = Modifier.weight(1f)) {
                Text(when (step) { 0 -> "Get started"; steps - 1 -> "Finish"; 4 -> if (loans.isEmpty()) "Skip" else "Next"; else -> "Next" })
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun Welcome(onRestore: () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        com.lenzorium.expenses.ui.components.BrandMark(96)
        Spacer(Modifier.height(20.dp))
        Text("Lenzorium Expenses", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Where did my money go, how much do I owe, and how much do I have left?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text("Everything stays on this phone. No account needed.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = onRestore) { Text("I have a backup — restore it") }
    }
}

@Composable
private fun CurrencyStep(selected: String, onSelect: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    Column {
        StepHeader("Choose your currency", "All amounts use this one currency. No conversion is done.")
        OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        val list = Currencies.all.filter { query.isBlank() || it.code.contains(query, true) || it.name.contains(query, true) }
        LazyColumn {
            items(list, key = { it.code }) { c ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(c.code) }.padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(c.code, style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(56.dp))
                    Text(c.name, modifier = Modifier.weight(1f))
                    if (c.code == selected) Icon(Icons.Rounded.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StartDateStep(startDay: Long, onChange: (Long) -> Unit) {
    var pick by remember { mutableStateOf(false) }
    Column {
        StepHeader(
            "Start tracking from",
            "Your balance above is treated as what you had at the start of this day. Entries can't be dated earlier.",
        )
        OutlinedButton(onClick = { pick = true }, modifier = Modifier.fillMaxWidth()) {
            Text(Dates.epochDay(startDay), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onChange(LocalDate.now().toEpochDay()) }) { Text("Today") }
            TextButton(onClick = { onChange(LocalDate.now().withDayOfMonth(1).toEpochDay()) }) { Text("Start of this month") }
        }
    }
    if (pick) DatePickDialog(LocalDate.ofEpochDay(startDay), { onChange(it.toEpochDay()) }, { pick = false }, maxDate = LocalDate.now())
}

@Composable
private fun LoansStep(currency: CurrencyInfo, loans: MutableList<OpeningLoanInput>) {
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        StepHeader(
            "Money you already owe",
            "Optional. Loans you already have add to your debt but don't add to your balance again.",
        )
        loans.forEachIndexed { i, l ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(l.lender, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOfNotNull(l.title.ifBlank { null }, l.dueEpochDay?.let { "Due ${Dates.epochDay(it)}" }).joinToString(" · ").ifBlank { "Existing loan" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(Money.format(l.amountMinor, currency), style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { loans.removeAt(i) }) { Icon(Icons.Rounded.Delete, "Remove") }
            }
            HorizontalDivider()
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = { adding = true }) {
            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Add an existing loan")
        }
    }
    if (adding) OpeningLoanDialog(currency, onAdd = { loans.add(it); adding = false }, onDismiss = { adding = false })
}

@Composable
fun OpeningLoanDialog(currency: CurrencyInfo, onAdd: (OpeningLoanInput) -> Unit, onDismiss: () -> Unit) {
    var lender by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var due by remember { mutableStateOf<Long?>(null) }
    var pickDue by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Existing loan") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextInput(lender, { lender = it }, "Lender's name")
                TextInput(title, { title = it }, "Loan title or reference (optional)")
                AmountField(amount, { amount = it }, currency, label = "Amount still owed")
                OutlinedButton(onClick = { pickDue = true }) { Text(due?.let { "Due ${Dates.epochDay(it)}" } ?: "Add due date (optional)") }
                ErrorBanner(err)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val v = Money.parse(amount, currency.decimals)
                when {
                    lender.isBlank() -> err = "Enter the lender's name."
                    v == null || v <= 0 -> err = "Enter an amount greater than zero."
                    else -> onAdd(OpeningLoanInput(lender, title, v, due, ""))
                }
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDue) DatePickDialog(due?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now(), { due = it.toEpochDay() }, { pickDue = false })
}

@Composable
fun PinField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(8)) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
