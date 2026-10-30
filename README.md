# Lenzorium Expenses

An offline Android expense manager. Record daily spending and income, track money you've borrowed and repaid, and always see how much you have left.

> **Where did my money go, how much do I owe, and how much do I have left?**

- Kotlin + Jetpack Compose (Material 3), light and dark themes
- Room (SQLite) database on the device — no account, no internet needed
- Exact money arithmetic (amounts stored as whole minor units, e.g. paisa)
- Minimum Android 8.0 (API 26)

## Install the APK

1. On your phone, open this repository's **Releases** page and download the latest `LenzoriumExpenses-…apk`.
2. Open the downloaded file. If Android asks, allow your browser/Files app to **install unknown apps**.
3. Tap **Install**, then open **Lenzorium Expenses**.

Updates: install a newer APK over the old one. Your data is kept because every build is signed with the same key (see *Signing*).

## Build it yourself

Every push to `main` builds automatically on GitHub Actions (`.github/workflows/build.yml`): it runs the calculation tests, builds the signed release APK, and publishes it as a GitHub Release.

To build locally you need JDK 17 and the Android SDK (easiest: install Android Studio).

```bash
./gradlew testReleaseUnitTest   # run the calculation tests
./gradlew assembleRelease       # build the APK
# → app/build/outputs/apk/release/app-release.apk
```

Or open the folder in Android Studio and use **Build › Build App Bundle(s) / APK(s) › Build APK(s)**.

## Signing

The release signing key is **not** stored in this repository. GitHub Actions reads it from two encrypted repository secrets (Settings › Secrets and variables › Actions):

| Secret | Value |
|---|---|
| `LENZORIUM_KEYSTORE_BASE64` | the `.jks` key file, base64-encoded |
| `LENZORIUM_KEYSTORE_PASSWORD` | the key's password (key alias: `lenzorium`) |

Without these secrets, the workflow still runs the tests and builds a **test** APK (signed with a throwaway key, named `…-TEST-unsigned-key.apk`) but does not publish a release.

For local builds, put a `keystore.properties` file in the project root (it's git-ignored):

```
storeFile=path/to/lenzorium-release.jks
storePassword=…
keyAlias=lenzorium
keyPassword=…
```

Keep a safe copy of the key file and password. An APK signed with a different key can't update the installed app without uninstalling it, which deletes its data — so make a backup first if that ever happens.

## Balance rules

Implemented in `app/src/main/java/com/lenzorium/expenses/domain/LedgerMath.kt`:

| Figure | Formula |
|---|---|
| Available Balance | Opening Balance + Income + New Loans Received − Expenses − Loan Repayments |
| Outstanding Debt | Opening Debt + New Borrowing + Recorded Loan Charges − Repayments |
| Balance After Clearing Debt | Available Balance − Outstanding Debt |

- Existing loans entered at setup (or via *Add a loan I already had*) add to debt only, never to the balance.
- Loan charges (fees/interest) add to debt only; cash changes when you repay.
- Borrowing is never income; repayments are never expenses (reports show them separately).
- Repayments are checked against the loan's history in date order, so a backdated edit can never make a loan over-repaid at any point in time.

## Verification

`app/src/test/java/com/lenzorium/expenses/LedgerMathTest.kt` contains the specification's example as a test (`specVerificationExample`):

| Input | Value |
|---|---|
| Opening balance | 1,000 |
| Income | 500 |
| New loan received | 300 |
| Expenses | 200 |
| Loan repayment | 100 |

Expected and asserted: **Available 1,500 · Debt 200 · After clearing debt 1,300**.
`openingLoanAddsDebtButNotCash` checks that an existing loan raises debt without raising the balance. Other tests cover partial/full repayment status, overdue loans, over-repayment rejection, backdated edits, loan charges, period reports (opening/closing balance), decimal parsing, and backup round-trip/corruption handling.

## Project layout

```
app/src/main/java/com/lenzorium/expenses/
  data/      Room entities, DAO, repository (all saves are transactional), settings
  domain/    Money parsing/formatting, LedgerMath (balance rules), ReportBuilder
  backup/    Full backup/restore (zip: backup.json + receipt photos)
  export/    PDF and CSV report exports (not backups)
  ui/        Compose screens, navigation, theme, charts, lock screen
```
