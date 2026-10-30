package com.lenzorium.expenses.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.RemoveCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lenzorium.expenses.backup.BackupManager
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.ui.components.ConfirmDialog
import com.lenzorium.expenses.ui.components.IconBadge
import com.lenzorium.expenses.ui.components.MessageDialog
import com.lenzorium.expenses.ui.screens.CategoriesScreen
import com.lenzorium.expenses.ui.screens.EntryScreen
import com.lenzorium.expenses.ui.screens.HomeScreen
import com.lenzorium.expenses.ui.screens.LoanDetailScreen
import com.lenzorium.expenses.ui.screens.LoanEventScreen
import com.lenzorium.expenses.ui.screens.LoanFormScreen
import com.lenzorium.expenses.ui.screens.LoansScreen
import com.lenzorium.expenses.ui.screens.LockScreen
import com.lenzorium.expenses.ui.screens.ReportsScreen
import com.lenzorium.expenses.ui.screens.SettingsScreen
import com.lenzorium.expenses.ui.screens.SetupScreen
import com.lenzorium.expenses.ui.screens.TransactionsScreen
import com.lenzorium.expenses.ui.screens.TxnDetailScreen
import com.lenzorium.expenses.ui.theme.LenzoriumTheme
import com.lenzorium.expenses.ui.theme.LocalMoneyColors
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val TXNS = "transactions"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val LOANS = "loans"
    const val CATEGORIES = "categories"
    fun entry(type: TxnType, id: Long = 0) = "entry/${type.name}?id=$id"
    fun loanForm(id: Long = 0, existing: Boolean = false) = "loanForm?id=$id&existing=$existing"
    fun repay(loanId: Long = 0, id: Long = 0) = "loanEvent/LOAN_REPAYMENT?loanId=$loanId&id=$id"
    fun charge(loanId: Long, id: Long = 0) = "loanEvent/LOAN_CHARGE?loanId=$loanId&id=$id"
    fun loan(id: Long) = "loan/$id"
    fun txn(id: Long) = "txn/$id"

    /** Where "Edit" goes for any transaction. */
    fun editTxn(type: TxnType, id: Long, loanId: Long?): String = when (type) {
        TxnType.EXPENSE, TxnType.INCOME -> entry(type, id)
        TxnType.LOAN_RECEIVED -> loanForm(loanId ?: 0)
        TxnType.LOAN_REPAYMENT -> repay(loanId ?: 0, id)
        TxnType.LOAN_CHARGE -> charge(loanId ?: 0, id)
    }
}

@Composable
fun AppRoot(repo: Repository, locked: MutableState<Boolean>) {
    val vm: MainViewModel = viewModel(factory = MainViewModel.factory(repo))
    val state by vm.state.collectAsStateWithLifecycle()
    LenzoriumTheme(state.settings.themeMode) {
        Box(Modifier.fillMaxSize()) {
            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !state.settings.setupDone -> SetupWithRestore(repo)
                else -> MainShell(repo, state)
            }
            if (locked.value && state.settings.lockEnabled) {
                LockScreen(repo.settings) { locked.value = false }
            }
        }
    }
}

@Composable
private fun SetupWithRestore(repo: Repository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Uri?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pending = uri }
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background) {
        SetupScreen(repo, onRestore = { picker.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) })
    }
    pending?.let { uri ->
        ConfirmDialog(
            title = "Restore this backup?",
            message = "Your data will be replaced with the contents of the backup file.",
            confirmLabel = "Restore",
            onConfirm = {
                pending = null
                scope.launch { message = BackupManager(context, repo).restore(uri).fold({ "Backup restored." }, { it.message ?: "Restore failed." }) }
            },
            onDismiss = { pending = null },
        )
    }
    message?.let { MessageDialog("Restore", it) { message = null } }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Rounded.Home),
    Tab(Routes.TXNS, "Transactions", Icons.AutoMirrored.Rounded.ReceiptLong),
    Tab("add", "Add", Icons.Rounded.AddCircle),
    Tab(Routes.REPORTS, "Reports", Icons.Rounded.BarChart),
    Tab(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
)

fun NavController.goTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainShell(repo: Repository, state: LedgerState) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in setOf(Routes.HOME, Routes.TXNS, Routes.REPORTS, Routes.SETTINGS)
    var addSheet by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { if (t.route == "add") addSheet = true else nav.goTab(t.route) },
                            icon = {
                                Icon(
                                    t.icon, null,
                                    tint = if (t.route == "add") MaterialTheme.colorScheme.primary else androidx.compose.material3.LocalContentColor.current,
                                )
                            },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
            composable(Routes.HOME) { HomeScreen(state, nav) }
            composable(Routes.TXNS) { TransactionsScreen(state, nav) }
            composable(Routes.REPORTS) { ReportsScreen(state, repo) }
            composable(Routes.SETTINGS) { SettingsScreen(state, repo, nav) }
            composable(Routes.LOANS) { LoansScreen(state, nav) }
            composable(Routes.CATEGORIES) { CategoriesScreen(state, repo, nav) }
            composable(
                "entry/{type}?id={id}",
                arguments = listOf(navArgument("type") { type = NavType.StringType }, navArgument("id") { type = NavType.LongType; defaultValue = 0L }),
            ) { e ->
                EntryScreen(repo, state, TxnType.valueOf(e.arguments?.getString("type") ?: "EXPENSE"), e.arguments?.getLong("id") ?: 0L, nav)
            }
            composable(
                "loanForm?id={id}&existing={existing}",
                arguments = listOf(
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("existing") { type = NavType.BoolType; defaultValue = false },
                ),
            ) { e ->
                LoanFormScreen(repo, state, e.arguments?.getLong("id") ?: 0L, e.arguments?.getBoolean("existing") ?: false, nav)
            }
            composable(
                "loanEvent/{type}?loanId={loanId}&id={id}",
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType },
                    navArgument("loanId") { type = NavType.LongType; defaultValue = 0L },
                    navArgument("id") { type = NavType.LongType; defaultValue = 0L },
                ),
            ) { e ->
                LoanEventScreen(
                    repo, state,
                    TxnType.valueOf(e.arguments?.getString("type") ?: "LOAN_REPAYMENT"),
                    e.arguments?.getLong("loanId") ?: 0L,
                    e.arguments?.getLong("id") ?: 0L,
                    nav,
                )
            }
            composable("loan/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                LoanDetailScreen(state, repo, e.arguments?.getLong("id") ?: 0L, nav)
            }
            composable("txn/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                TxnDetailScreen(state, repo, e.arguments?.getLong("id") ?: 0L, nav)
            }
        }
    }

    if (addSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { addSheet = false }, sheetState = sheetState) {
            val m = LocalMoneyColors.current
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Add", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                AddOption(Icons.Rounded.RemoveCircle, m.expense, "Add Expense", "Record money you spent") { addSheet = false; nav.navigate(Routes.entry(TxnType.EXPENSE)) }
                AddOption(Icons.Rounded.TrendingUp, m.income, "Add Income", "Salary, business, gifts and more") { addSheet = false; nav.navigate(Routes.entry(TxnType.INCOME)) }
                AddOption(Icons.Rounded.AccountBalance, m.loan, "Take Loan", "Money you borrowed — adds to balance and debt") { addSheet = false; nav.navigate(Routes.loanForm()) }
                AddOption(Icons.Rounded.Payments, m.loan, "Repay Loan", "Pay back part or all of a loan") { addSheet = false; nav.navigate(Routes.repay()) }
            }
        }
    }
}

@Composable
private fun AddOption(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint, size = 44)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
