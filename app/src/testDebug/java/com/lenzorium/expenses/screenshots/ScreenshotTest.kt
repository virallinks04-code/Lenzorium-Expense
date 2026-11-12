package com.lenzorium.expenses.screenshots

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lenzorium.expenses.data.AppDatabase
import com.lenzorium.expenses.data.Repository
import com.lenzorium.expenses.data.SettingsStore
import com.lenzorium.expenses.data.ThemeMode
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.ui.LedgerState
import com.lenzorium.expenses.ui.MainShell
import com.lenzorium.expenses.ui.Routes
import com.lenzorium.expenses.ui.screens.AccountDetailScreen
import com.lenzorium.expenses.ui.screens.AccountsScreen
import com.lenzorium.expenses.ui.screens.CategoriesScreen
import com.lenzorium.expenses.ui.screens.TransferScreen
import com.lenzorium.expenses.ui.screens.EntryScreen
import com.lenzorium.expenses.ui.screens.LoanDetailScreen
import com.lenzorium.expenses.ui.screens.LoanEventScreen
import com.lenzorium.expenses.ui.screens.LoanFormScreen
import com.lenzorium.expenses.ui.screens.LockScreen
import com.lenzorium.expenses.ui.screens.SetupScreen
import com.lenzorium.expenses.ui.screens.TxnDetailScreen
import com.lenzorium.expenses.ui.screens.PlanRoutes
import com.lenzorium.expenses.ui.screens.SafeRoutes
import com.lenzorium.expenses.ui.screens.SafePersonScreen
import com.lenzorium.expenses.ui.screens.SafeFormScreen
import com.lenzorium.expenses.ui.screens.ProfileScreen
import com.lenzorium.expenses.ui.screens.RecurringFormScreen
import com.lenzorium.expenses.ui.screens.GoalDetailScreen
import com.lenzorium.expenses.ui.theme.LenzoriumTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screen to PNG (app/build/screenshots). Phone size 360×640dp at xxhdpi = 1080×1920,
 * the 9:16 ratio Google Play accepts for store screenshots. "_full" variants are tall to show whole pages.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var repo: Repository
    private val s = SampleData.state

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = Repository(ctx, db, SettingsStore(ctx))
    }

    private fun shot(name: String, dark: Boolean = false, content: @Composable (NavController) -> Unit) {
        rule.setContent {
            LenzoriumTheme(if (dark) ThemeMode.DARK else ThemeMode.LIGHT) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content(rememberNavController())
                }
            }
        }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(1500)
        rule.waitForIdle()
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnIdle { view.draw(Canvas(bmp)) }
        val dir = File(System.getProperty("screenshot.dir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun tab(name: String, route: String, dark: Boolean = false, state: LedgerState = s) =
        shot(name, dark) { MainShell(repo, state, route) }

    private val expenseId get() = s.txns.first { it.party == "Imtiaz Super Market" }.id

    // Main tabs
    @Test fun home() = tab("01_home", Routes.HOME)
    @Test fun homeDark() = tab("01_home_dark", Routes.HOME, dark = true)
    @Config(qualifiers = "w360dp-h1700dp-xxhdpi") @Test fun homeFull() = tab("01_home_full", Routes.HOME)
    @Test fun homeEmpty() = tab("02_home_empty", Routes.HOME, state = SampleData.empty)
    @Test fun transactions() = tab("03_transactions", Routes.TXNS)
    @Test fun transactionsDark() = tab("03_transactions_dark", Routes.TXNS, dark = true)
    @Test fun reports() = tab("04_reports", Routes.REPORTS)
    @Config(qualifiers = "w360dp-h2600dp-xxhdpi") @Test fun reportsFull() = tab("04_reports_full", Routes.REPORTS)
    @Config(qualifiers = "w360dp-h1500dp-xxhdpi") @Test fun settings() = tab("05_settings_full", Routes.SETTINGS)
    @Test fun loans() = tab("06_loans", Routes.LOANS)
    @Test fun loansDark() = tab("06_loans_dark", Routes.LOANS, dark = true)

    // Detail and form screens
    @Test fun loanDetail() = shot("07_loan_detail") { LoanDetailScreen(s, repo, 1, it) }
    @Test fun txnDetail() = shot("08_txn_detail") { TxnDetailScreen(s, repo, expenseId, it) }
    @Test fun addExpense() = shot("09_add_expense") { EntryScreen(repo, s, TxnType.EXPENSE, 0, it) }
    @Test fun addExpenseDark() = shot("09_add_expense_dark", dark = true) { EntryScreen(repo, s, TxnType.EXPENSE, 0, it) }
    @Test fun addIncome() = shot("10_add_income") { EntryScreen(repo, s, TxnType.INCOME, 0, it) }
    @Test fun takeLoan() = shot("11_take_loan") { LoanFormScreen(repo, s, 0, false, it) }
    @Test fun repayLoan() = shot("12_repay_loan") { LoanEventScreen(repo, s, TxnType.LOAN_REPAYMENT, 1, 0, it) }
    @Test fun categories() = shot("13_categories") { CategoriesScreen(s, repo, it) }
    @Test fun lock() = shot("14_lock") { LockScreen(repo.settings) {} }
    @Test fun transfer() = shot("16_transfer") { TransferScreen(repo, s, 0, 0, it) }
    @Test fun accounts() = shot("17_accounts") { AccountsScreen(s, repo, it) }
    @Test fun accountDetail() = shot("18_account_cash") { AccountDetailScreen(s, repo, 1, it) }
    @Test fun txnDetailExchange() = shot("19_exchange_detail") { TxnDetailScreen(s, repo, 102, it) }
    @Test fun accountForeign() = shot("18_account_gbp") { AccountDetailScreen(s, repo, 4, it) }
    @Test fun txnDetailTransfer() = shot("19_transfer_detail") { TxnDetailScreen(s, repo, 100, it) }
    @Test fun setup() = shot("15_setup_welcome") { SetupScreen(repo, onRestore = {}) }

    // Planning (Release 1)
    @Config(qualifiers = "w360dp-h1100dp-xxhdpi") @Test fun budgets() = tab("20_budgets", PlanRoutes.BUDGETS)
    @Test fun budgetsEmpty() = tab("20_budgets_empty", PlanRoutes.BUDGETS, state = SampleData.empty)
    @Config(qualifiers = "w360dp-h1300dp-xxhdpi") @Test fun bills() = tab("21_bills", PlanRoutes.BILLS)
    @Config(qualifiers = "w360dp-h1500dp-xxhdpi") @Test fun billForm() = shot("22_bill_form") { RecurringFormScreen(s, repo, 0, it) }
    @Test fun goals() = tab("23_goals", PlanRoutes.GOALS)
    @Test fun goalDetail() = shot("24_goal_detail") { GoalDetailScreen(s, repo, 1, it) }
    @Config(qualifiers = "w360dp-h1100dp-xxhdpi") @Test fun profile() = shot("26_profile") { ProfileScreen(s, repo, it) }
    @Config(qualifiers = "w360dp-h1100dp-xxhdpi") @Test fun profileSignedIn() = shot("26_profile_signed_in") {
        ProfileScreen(s.copy(settings = s.settings.copy(profileName = "Ahmed Khan", googleEmail = "ahmed.khan@gmail.com", driveLast = System.currentTimeMillis() - 3_600_000)), repo, it)
    }
    @Config(qualifiers = "w360dp-h1500dp-xxhdpi") @Test fun safeHome() = tab("27_safekeeping", SafeRoutes.HOME)
    @Config(qualifiers = "w360dp-h1300dp-xxhdpi") @Test fun safePerson() = shot("28_safe_person") { SafePersonScreen(s, repo, 1, it) }
    @Config(qualifiers = "w360dp-h1600dp-xxhdpi") @Test fun safeGive() = shot("29_safe_give") { SafeFormScreen(s, repo, TxnType.SAFE_GIVEN, 1, 0, it) }
    @Config(qualifiers = "w360dp-h1300dp-xxhdpi") @Test fun safeReceive() = shot("30_safe_receive") { SafeFormScreen(s, repo, TxnType.SAFE_RETURNED, 1, 0, it) }
    @Test fun safeDetail() = shot("31_safe_detail") { TxnDetailScreen(s, repo, 201, it) }
    @Test fun shortcuts() = tab("25_shortcuts", PlanRoutes.SHORTCUTS)
    @Test fun budgetsDark() = tab("20_budgets_dark", PlanRoutes.BUDGETS, dark = true)
    @Config(qualifiers = "w360dp-h1700dp-xxhdpi") @Test fun homeFullDark() = tab("01_home_full_dark", Routes.HOME, dark = true)
    @Config(qualifiers = "w360dp-h1300dp-xxhdpi") @Test fun addExpenseFull() = shot("09_add_expense_full") { EntryScreen(repo, s, TxnType.EXPENSE, 0, it) }
}
