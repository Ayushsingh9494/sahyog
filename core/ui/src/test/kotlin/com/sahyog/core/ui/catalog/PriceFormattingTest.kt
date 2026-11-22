package com.sahyog.core.ui.catalog

import com.sahyog.core.model.Paise
import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormattingTest {

    @Test
    fun `whole rupees drop the decimals`() {
        assertEquals("₹0", formatRupees(Paise(0)))
        assertEquals("₹199", formatRupees(Paise.rupees(199)))
    }

    @Test
    fun `uses indian digit grouping`() {
        assertEquals("₹1,499", formatRupees(Paise.rupees(1_499)))
        assertEquals("₹12,34,567", formatRupees(Paise.rupees(1_234_567)))
        assertEquals("₹1,00,00,000", formatRupees(Paise.rupees(10_000_000)))
    }

    @Test
    fun `keeps paise when present`() {
        assertEquals("₹1,23,456.78", formatRupees(Paise(12_345_678)))
        assertEquals("₹5.05", formatRupees(Paise(505)))
    }

    @Test
    fun `negative amounts`() {
        assertEquals("-₹250", formatRupees(Paise(-25_000)))
    }
}
