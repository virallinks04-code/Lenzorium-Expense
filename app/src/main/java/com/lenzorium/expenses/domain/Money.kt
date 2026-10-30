package com.lenzorium.expenses.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

data class CurrencyInfo(val code: String, val name: String, val decimals: Int)

object Currencies {
    val all: List<CurrencyInfo> = listOf(
        CurrencyInfo("PKR", "Pakistani Rupee", 2),
        CurrencyInfo("USD", "US Dollar", 2),
        CurrencyInfo("EUR", "Euro", 2),
        CurrencyInfo("GBP", "British Pound", 2),
        CurrencyInfo("INR", "Indian Rupee", 2),
        CurrencyInfo("AED", "UAE Dirham", 2),
        CurrencyInfo("SAR", "Saudi Riyal", 2),
        CurrencyInfo("QAR", "Qatari Riyal", 2),
        CurrencyInfo("KWD", "Kuwaiti Dinar", 3),
        CurrencyInfo("BHD", "Bahraini Dinar", 3),
        CurrencyInfo("OMR", "Omani Rial", 3),
        CurrencyInfo("BDT", "Bangladeshi Taka", 2),
        CurrencyInfo("LKR", "Sri Lankan Rupee", 2),
        CurrencyInfo("NPR", "Nepalese Rupee", 2),
        CurrencyInfo("AFN", "Afghan Afghani", 2),
        CurrencyInfo("CAD", "Canadian Dollar", 2),
        CurrencyInfo("AUD", "Australian Dollar", 2),
        CurrencyInfo("NZD", "New Zealand Dollar", 2),
        CurrencyInfo("CHF", "Swiss Franc", 2),
        CurrencyInfo("CNY", "Chinese Yuan", 2),
        CurrencyInfo("JPY", "Japanese Yen", 0),
        CurrencyInfo("KRW", "South Korean Won", 0),
        CurrencyInfo("SGD", "Singapore Dollar", 2),
        CurrencyInfo("MYR", "Malaysian Ringgit", 2),
        CurrencyInfo("IDR", "Indonesian Rupiah", 2),
        CurrencyInfo("PHP", "Philippine Peso", 2),
        CurrencyInfo("THB", "Thai Baht", 2),
        CurrencyInfo("TRY", "Turkish Lira", 2),
        CurrencyInfo("EGP", "Egyptian Pound", 2),
        CurrencyInfo("NGN", "Nigerian Naira", 2),
        CurrencyInfo("KES", "Kenyan Shilling", 2),
        CurrencyInfo("ZAR", "South African Rand", 2),
        CurrencyInfo("BRL", "Brazilian Real", 2),
        CurrencyInfo("MXN", "Mexican Peso", 2),
    )

    fun find(code: String): CurrencyInfo = all.firstOrNull { it.code == code } ?: all.first()
}

/**
 * Amounts are stored as Long minor units (e.g. paisa / cents), so all arithmetic is exact.
 */
object Money {
    /** Parses user input such as "1,250.50". Returns null if it is not a valid number
     *  or has more decimal places than the currency allows. Sign is preserved; callers reject <= 0. */
    fun parse(input: String, decimals: Int): Long? {
        val cleaned = input.trim().replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return null
        if (!cleaned.matches(Regex("^-?\\d*(\\.\\d*)?$")) || cleaned == "." || cleaned == "-") return null
        return try {
            val bd = BigDecimal(cleaned)
            if (bd.stripTrailingZeros().scale() > decimals) return null
            bd.movePointRight(decimals).setScale(0, RoundingMode.UNNECESSARY).longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Plain number for editing fields, e.g. 125050 -> "1250.50". */
    fun toInput(minor: Long, decimals: Int): String =
        BigDecimal.valueOf(minor, decimals).toPlainString()

    private fun pattern(decimals: Int): DecimalFormat {
        val p = if (decimals == 0) "#,##0" else "#,##0." + "0".repeat(decimals)
        return DecimalFormat(p, DecimalFormatSymbols(Locale.US))
    }

    /** "1,250.50" (no currency). Negative values get a leading minus sign. */
    fun number(minor: Long, decimals: Int): String {
        val bd = BigDecimal.valueOf(minor, decimals)
        return pattern(decimals).format(bd)
    }

    /** "PKR 1,250.50" or "−PKR 1,250.50". */
    fun format(minor: Long, currency: CurrencyInfo): String {
        val abs = number(kotlin.math.abs(minor), currency.decimals)
        return if (minor < 0) "−${currency.code} $abs" else "${currency.code} $abs"
    }

    /** "+PKR 500.00" / "−PKR 200.00" for signed movements. */
    fun formatSigned(minor: Long, currency: CurrencyInfo): String {
        val abs = number(kotlin.math.abs(minor), currency.decimals)
        return when {
            minor > 0 -> "+${currency.code} $abs"
            minor < 0 -> "−${currency.code} $abs"
            else -> "${currency.code} $abs"
        }
    }
}
