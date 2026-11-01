package com.lenzorium.expenses.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.lenzorium.expenses.data.Category
import com.lenzorium.expenses.data.Loan
import com.lenzorium.expenses.data.TxnType
import com.lenzorium.expenses.domain.CurrencyInfo
import com.lenzorium.expenses.domain.LedgerMath
import com.lenzorium.expenses.domain.Money
import com.lenzorium.expenses.domain.ReportData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Report exports (PDF/CSV). These are summaries for reading or sharing — NOT backups. */
object ReportExporter {
    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    private fun ldt(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDateTime()

    private fun describe(t: com.lenzorium.expenses.data.Txn, cats: Map<Long, Category>, loans: Map<Long, Loan>): String = when (t.type) {
        TxnType.EXPENSE, TxnType.INCOME -> t.party.ifBlank { t.categoryId?.let { cats[it]?.name } ?: "" }
        TxnType.TRANSFER -> t.notes.ifBlank { "Transfer" }
        else -> {
            val l = t.loanId?.let { loans[it] }
            listOfNotNull(l?.lender ?: t.party, l?.title?.ifBlank { null }).joinToString(" · ")
        }
    }

    suspend fun writeCsv(context: Context, uri: Uri, r: ReportData, c: CurrencyInfo, categories: List<Category>, loans: List<Loan>): Result<Unit> =
        write(context, uri) { out -> out.write(buildCsv(r, c, categories, loans).toByteArray(Charsets.UTF_8)) }

    fun buildCsv(r: ReportData, c: CurrencyInfo, categories: List<Category>, loans: List<Loan>): String {
        val cats = categories.associateBy { it.id }
        val loanMap = loans.associateBy { it.id }
        fun n(v: Long) = Money.toInput(v, c.decimals)
        val sb = StringBuilder()
        fun row(vararg cells: String) { sb.append(cells.joinToString(",") { csv(it) }).append("\r\n") }
        sb.append('﻿') // BOM so spreadsheet apps detect UTF-8
        row("Lenzorium Expenses report (export, not a backup)")
        row("Period", "${r.period.start.format(dateFmt)} to ${r.period.endInclusive.format(dateFmt)}")
        row("Currency", c.code)
        row("Generated", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        row()
        row("Summary", "Amount")
        row("Opening available balance", n(r.openingBalance))
        row("Total income", n(r.totals.income))
        row("Total expenses", n(r.totals.expenses))
        row("Money borrowed (not income)", n(r.totals.borrowed))
        row("Loan repayments (not expenses)", n(r.totals.repayments))
        row("Loan charges recorded (debt only)", n(r.totals.charges))
        row("Closing available balance", n(r.closingBalance))
        row("Outstanding debt as of period end", n(r.debtAtEnd))
        row("Outstanding debt now (current)", n(r.debtNow))
        r.accountClosing.forEach { (name, v) -> row("Closing balance: $name", n(v)) }
        row()
        row("Expenses by category", "Amount")
        r.expenseByCategory.forEach { row(it.name, n(it.amount)) }
        row()
        row("Income by category", "Amount")
        r.incomeByCategory.forEach { row(it.name, n(it.amount)) }
        row()
        row("Lender", "Outstanding as of period end", "Outstanding now (current)")
        r.lenders.forEach { row(it.lender, n(it.atPeriodEnd), n(it.current)) }
        row()
        row("Date", "Time", "Type", "Category", "Description", "Account", "Amount", "Effect on available balance", "Notes", "Tags")
        r.txns.forEach { t ->
            val d = ldt(t.timestamp)
            row(
                d.format(dateFmt), d.format(timeFmt), t.type.label,
                t.categoryId?.let { cats[it]?.name } ?: "",
                describe(t, cats, loanMap),
                r.accountLabel(t),
                n(t.amountMinor),
                n(LedgerMath.cashDelta(t)),
                t.notes,
                t.tagList.joinToString(" ") { "#$it" },
            )
        }
        return sb.toString()
    }

    private fun csv(s: String): String {
        // Neutralise spreadsheet formula injection, then quote.
        val safe = if (s.isNotEmpty() && s[0] in "=+@" ) "'$s" else s
        return if (safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    suspend fun writePdf(context: Context, uri: Uri, r: ReportData, c: CurrencyInfo, categories: List<Category>, loans: List<Loan>): Result<Unit> =
        write(context, uri) { out ->
            val doc = PdfDocument()
            try {
                PdfWriter(doc).render(r, c, categories, loans)
                doc.writeTo(out)
            } finally {
                doc.close()
            }
        }

    private suspend fun write(context: Context, uri: Uri, block: (OutputStream) -> Unit): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Couldn't open the file for writing.")
            out.use(block)
        }
    }

    private class PdfWriter(private val doc: PdfDocument) {
        private val w = 595
        private val h = 842
        private val margin = 40f
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var y = 0f
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.rgb(30, 30, 30) }
        private val muted = Paint(text).apply { color = Color.rgb(110, 110, 110); textSize = 9f }
        private val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        private val h1 = Paint(bold).apply { textSize = 18f; color = Color.rgb(11, 110, 79) }
        private val h2 = Paint(bold).apply { textSize = 12.5f; color = Color.rgb(11, 110, 79) }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        private fun canvas() = page!!.canvas

        private fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, pageNo).create())
            y = margin
            canvas().drawText("Lenzorium Expenses · report export (not a backup) · page $pageNo", margin, h - 20f, muted)
        }

        private fun ensure(space: Float) { if (page == null || y + space > h - 40f) newPage() }

        private fun line(left: String, right: String = "", p: Paint = text, rightPaint: Paint = p) {
            ensure(16f)
            canvas().drawText(fit(left, p, if (right.isEmpty()) w - 2 * margin else w - 2 * margin - 150f), margin, y + 11f, p)
            if (right.isNotEmpty()) canvas().drawText(right, w - margin - rightPaint.measureText(right), y + 11f, rightPaint)
            y += 16f
        }

        private fun heading(s: String) { ensure(36f); y += 12f; canvas().drawText(s, margin, y + 13f, h2); y += 22f }

        private fun fit(s: String, p: Paint, max: Float): String {
            if (p.measureText(s) <= max) return s
            var t = s
            while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
            return "$t…"
        }

        fun render(r: ReportData, c: CurrencyInfo, categories: List<Category>, loans: List<Loan>) {
            val cats = categories.associateBy { it.id }
            val loanMap = loans.associateBy { it.id }
            fun f(v: Long) = Money.format(v, c)
            newPage()
            canvas().drawText("Lenzorium Expenses — Report", margin, y + 18f, h1); y += 30f
            line(r.period.label + "   (" + r.period.start.format(dateFmt) + " to " + r.period.endInclusive.format(dateFmt) + ")", p = bold)
            line("Generated " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")) + " · Currency " + c.code, p = muted)
            line("This is a report export for reading and sharing. Use Settings › Backup for a full backup.", p = muted)

            heading("Summary")
            line("Opening available balance", f(r.openingBalance))
            line("Total income", f(r.totals.income))
            line("Total expenses", f(r.totals.expenses))
            line("Net (income − expenses)", Money.formatSigned(r.totals.income - r.totals.expenses, c), bold)
            line("Closing available balance", f(r.closingBalance), bold)
            r.accountClosing.forEach { (name, v) -> line("   $name", f(v)) }

            heading("Borrowing (kept separate from income and expenses)")
            line("Money borrowed in period", f(r.totals.borrowed))
            line("Loan repayments in period", f(r.totals.repayments))
            if (r.totals.charges > 0) line("Loan fees/interest recorded (adds to debt)", f(r.totals.charges))
            line("Outstanding debt as of ${r.period.endInclusive.format(dateFmt)}", f(r.debtAtEnd), bold)
            line("Outstanding debt now (current)", f(r.debtNow))

            heading("Expenses by category")
            if (r.expenseByCategory.isEmpty()) line("No expenses in this period.", p = muted)
            val maxCat = r.expenseByCategory.maxOfOrNull { it.amount }?.coerceAtLeast(1L) ?: 1L
            r.expenseByCategory.forEach { ca ->
                ensure(20f)
                fill.color = (ca.colorArgb and 0xFFFFFFFFL).toInt()
                val barMax = 140f
                val bw = barMax * ca.amount / maxCat
                canvas().drawRect(margin + 200f, y + 3f, margin + 200f + bw, y + 12f, fill)
                val share = if (r.totals.expenses > 0) " (${100 * ca.amount / r.totals.expenses}%)" else ""
                line(ca.name + share, f(ca.amount))
            }

            if (r.incomeByCategory.isNotEmpty()) {
                heading("Income by category")
                r.incomeByCategory.forEach { line(it.name, f(it.amount)) }
            }

            if (r.lenders.isNotEmpty()) {
                heading("Debt by lender")
                ensure(16f)
                canvas().drawText("Lender", margin, y + 11f, bold)
                val c1 = w - margin - 160f
                canvas().drawText("As of period end", c1 - bold.measureText("As of period end"), y + 11f, bold)
                canvas().drawText("Current", w - margin - bold.measureText("Current"), y + 11f, bold)
                y += 16f
                r.lenders.forEach { l ->
                    ensure(16f)
                    canvas().drawText(fit(l.lender + if (l.loans > 1) " (${l.loans} loans)" else "", text, 250f), margin, y + 11f, text)
                    val a = f(l.atPeriodEnd); val b = f(l.current)
                    canvas().drawText(a, c1 - text.measureText(a), y + 11f, text)
                    canvas().drawText(b, w - margin - text.measureText(b), y + 11f, text)
                    y += 16f
                }
            }

            heading("Transactions (${r.txns.size})")
            if (r.txns.isEmpty()) line("No transactions in this period.", p = muted)
            r.txns.forEach { t ->
                val d = ldt(t.timestamp)
                val desc = describe(t, cats, loanMap)
                val cat = t.categoryId?.let { cats[it]?.name }
                val left = d.format(DateTimeFormatter.ofPattern("dd MMM HH:mm")) + "  " + t.type.label + " · " +
                    listOfNotNull(desc.ifBlank { null }, cat?.takeIf { it != desc }, r.accountLabel(t).ifBlank { null }).joinToString(" · ")
                val right = if (t.type == TxnType.LOAN_CHARGE) "debt +" + Money.number(t.amountMinor, c.decimals) else Money.formatSigned(LedgerMath.cashDelta(t), c)
                line(left, right)
            }
            page?.let { doc.finishPage(it) }
            page = null
        }
    }
}
