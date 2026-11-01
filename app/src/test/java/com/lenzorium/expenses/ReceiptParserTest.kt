package com.lenzorium.expenses

import com.lenzorium.expenses.domain.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    private val today = LocalDate.of(2026, 11, 1)

    @Test
    fun supermarketReceipt() {
        val text = """
            IMTIAZ SUPER MARKET
            Shop 12, Gulshan Block 5
            Tel: 021-34567890
            Date: 30/10/2026  Time: 19:42
            Milk 1L            320.00
            Bread              180.00
            Eggs (12)          420.00
            Sub Total          920.00
            GST 18%            165.60
            Grand Total      1,085.60
            Cash             1,100.00
            Change              14.40
        """.trimIndent()
        val g = ReceiptParser.parse(text, 2, today)
        assertEquals(108560L, g.totalMinor)
        assertTrue(g.totalConfident)
        assertEquals(LocalDate.of(2026, 10, 30), g.date)
        assertEquals("Imtiaz Super Market", g.merchant)
    }

    @Test
    fun totalOnNextLineAndNamedMonth() {
        val text = """
            TAX INVOICE
            Savour Foods
            31-Oct-26
            Pulao Kabab x2
            TOTAL
            Rs. 1250
        """.trimIndent()
        val g = ReceiptParser.parse(text, 2, today)
        assertEquals(125000L, g.totalMinor)
        assertEquals(LocalDate.of(2026, 10, 31), g.date)
        assertEquals("Savour Foods", g.merchant)
    }

    @Test
    fun ukReceiptAndSubtotalIgnored() {
        val text = """
            Tesco Express
            12 Oct 2026 14:05
            Subtotal £9.80
            Total £10.30
        """.trimIndent()
        val g = ReceiptParser.parse(text, 2, today)
        assertEquals(1030L, g.totalMinor)
        assertEquals(LocalDate.of(2026, 10, 12), g.date)
    }

    @Test
    fun noTotalFallsBackToLargestAmount() {
        val g = ReceiptParser.parse("Corner shop\nTea 50.00\nBiscuits 120.00", 2, today)
        assertEquals(12000L, g.totalMinor)
        assertFalse(g.totalConfident)
    }

    @Test
    fun futureOrImpossibleDatesIgnored() {
        assertNull(ReceiptParser.parse("Shop\n15/12/2026\nTotal 100", 2, today).date)
        assertEquals(LocalDate.of(2026, 3, 5), ReceiptParser.parse("Shop\n05/03/2026\nTotal 100", 2, today).date)
        assertEquals(LocalDate.of(2026, 10, 25), ReceiptParser.parse("Shop\n10/25/2026\nTotal 100", 2, today).date) // month-first only when needed
    }
}
