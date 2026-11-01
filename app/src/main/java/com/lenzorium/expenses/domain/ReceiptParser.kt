package com.lenzorium.expenses.domain

import java.time.LocalDate
import java.time.Month

/** What the scanner thinks it read on a receipt. Every field is a suggestion the user confirms. */
data class ReceiptGuess(
    val totalMinor: Long? = null,
    /** True when the total came from a line labelled "total"; false when it's only the largest number. */
    val totalConfident: Boolean = false,
    val date: LocalDate? = null,
    val merchant: String? = null,
)

/**
 * Turns recognised receipt text into suggestions. Pure Kotlin so it can be unit-tested.
 * Handles common layouts: "TOTAL 1,250.00", "Grand Total: Rs. 1250", amounts on the next line,
 * and dates like 30/10/2026, 2026-10-30, 30-Oct-26 or 30 Oct 2026.
 */
object ReceiptParser {
    private val amountRx = Regex("""(?<![\d.])(\d{1,3}(?:[,\s]\d{3})+|\d+)(?:[.](\d{1,3}))?(?![\d])""")
    private val totalWords = listOf("grand total", "net total", "total amount", "amount due", "net amount", "net payable", "payable", "total due", "balance due", "total")
    private val skipWords = listOf("subtotal", "sub total", "sub-total", "total items", "total qty", "items total", "tax", "gst", "vat", "discount", "change", "cash tendered", "tendered", "saving")
    private val headerWords = listOf("tax invoice", "invoice", "receipt", "sales receipt", "welcome", "customer copy", "duplicate", "cash memo", "bill")

    fun parse(text: String, decimals: Int, today: LocalDate = LocalDate.now()): ReceiptGuess {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val (total, confident) = findTotal(lines, decimals)
        return ReceiptGuess(total, confident, findDate(lines, today), findMerchant(lines))
    }

    private fun amounts(line: String, decimals: Int): List<Long> =
        amountRx.findAll(line).mapNotNull { m ->
            val whole = m.groupValues[1].replace(",", "").replace(" ", "")
            val frac = m.groupValues[2]
            if (whole.length > 10) return@mapNotNull null
            // A 3-digit "fraction" on a 2-decimal currency is really a thousands group (e.g. 1.250 in some locales).
            val text = if (frac.isEmpty()) whole else if (frac.length > decimals) whole + frac else "$whole.$frac"
            Money.parse(text, decimals)
        }.filter { it > 0 }.toList()

    private fun looksLikeDateOrPhone(line: String): Boolean =
        Regex("""\d{1,4}[/-]\d{1,2}[/-]\d{2,4}""").containsMatchIn(line) ||
            Regex("""\b\d{2}:\d{2}\b""").containsMatchIn(line) ||
            Regex("""(\+?\d[\d -]{8,}\d)""").containsMatchIn(line) && !line.contains('.')

    private fun findTotal(lines: List<String>, decimals: Int): Pair<Long?, Boolean> {
        for (word in totalWords) {
            for ((i, raw) in lines.withIndex()) {
                val l = raw.lowercase()
                if (!l.contains(word)) continue
                // Plain "total" lines can be subtotals or tax totals; specific phrases ("grand total") are trusted.
                if ((word == "total" || word == "payable") && skipWords.any { l.contains(it) }) continue
                val here = amounts(raw, decimals)
                if (here.isNotEmpty()) return here.last() to true
                // Amount printed on the next line (two-column receipts).
                lines.getOrNull(i + 1)?.let { n -> if (!looksLikeDateOrPhone(n)) amounts(n, decimals).lastOrNull()?.let { return it to true } }
            }
        }
        // Fallback: the largest amount that has decimals or a currency mark, ignoring dates and phone numbers.
        val candidates = lines.filterNot { looksLikeDateOrPhone(it) }.flatMap { l ->
            val money = l.contains('.') || l.contains("rs", ignoreCase = true) || l.contains('£') || l.contains('$') || l.contains("pkr", ignoreCase = true)
            if (money) amounts(l, decimals) else emptyList()
        }
        return candidates.maxOrNull() to false
    }

    private val months = mapOf(
        "jan" to Month.JANUARY, "feb" to Month.FEBRUARY, "mar" to Month.MARCH, "apr" to Month.APRIL, "may" to Month.MAY, "jun" to Month.JUNE,
        "jul" to Month.JULY, "aug" to Month.AUGUST, "sep" to Month.SEPTEMBER, "oct" to Month.OCTOBER, "nov" to Month.NOVEMBER, "dec" to Month.DECEMBER,
    )

    private fun year(y: Int) = if (y < 100) 2000 + y else y

    private fun findDate(lines: List<String>, today: LocalDate): LocalDate? {
        val iso = Regex("""\b(20\d{2})[-/.](\d{1,2})[-/.](\d{1,2})\b""")
        val dmy = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b""")
        val named = Regex("""\b(\d{1,2})[\s-]*([A-Za-z]{3})[A-Za-z]*[\s,-]*(\d{2,4})\b""")
        val namedFirst = Regex("""\b([A-Za-z]{3})[A-Za-z]*[\s-]+(\d{1,2}),?[\s-]+(\d{2,4})\b""")
        for (l in lines) {
            val d = iso.find(l)?.let { m -> runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull() }
                ?: dmy.find(l)?.let { m ->
                    val a = m.groupValues[1].toInt(); val b = m.groupValues[2].toInt(); val y = year(m.groupValues[3].toInt())
                    // Day first (as in Pakistan and the UK); month first only when the day-first reading is impossible.
                    runCatching { LocalDate.of(y, b, a) }.getOrNull() ?: runCatching { LocalDate.of(y, a, b) }.getOrNull()
                }
                ?: named.find(l)?.let { m -> months[m.groupValues[2].lowercase()]?.let { mo -> runCatching { LocalDate.of(year(m.groupValues[3].toInt()), mo, m.groupValues[1].toInt()) }.getOrNull() } }
                ?: namedFirst.find(l)?.let { m -> months[m.groupValues[1].lowercase()]?.let { mo -> runCatching { LocalDate.of(year(m.groupValues[3].toInt()), mo, m.groupValues[2].toInt()) }.getOrNull() } }
            // Ignore obviously wrong readings (future dates or more than 2 years old).
            if (d != null && !d.isAfter(today) && d.isAfter(today.minusYears(2))) return d
        }
        return null
    }

    private fun findMerchant(lines: List<String>): String? =
        lines.take(6).firstOrNull { l ->
            val low = l.lowercase()
            l.count { it.isLetter() } >= 3 && l.count { it.isDigit() } <= 3 &&
                headerWords.none { low == it || low.startsWith("$it ") || low.startsWith("$it:") } &&
                !low.startsWith("www") && !low.contains("@") && l.length <= 40
        }?.split(' ')?.joinToString(" ") { w -> if (w.length > 1 && w.all { it.isUpperCase() || !it.isLetter() }) w.lowercase().replaceFirstChar { it.titlecase() } else w }
}
