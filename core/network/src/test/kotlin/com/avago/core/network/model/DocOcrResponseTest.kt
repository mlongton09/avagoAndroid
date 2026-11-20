package com.avago.core.network.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class DocOcrResponseTest {
    @Test
    fun `amounts read with either decimal separator and without currency symbols`() {
        assertEquals(BigDecimal("1234.56"), DocOcrResponse(total_amount = "$1,234.56").totalAmount())
        assertEquals(BigDecimal("1234.56"), DocOcrResponse(total_amount = "1.234,56 €").totalAmount())
        assertEquals(BigDecimal("12.5"), DocOcrResponse(total_amount = "12,5").totalAmount())
        assertEquals(BigDecimal("1200"), DocOcrResponse(total_amount = "1,200").totalAmount())
        assertNull(DocOcrResponse(total_amount = "n/a").totalAmount())
        assertNull(DocOcrResponse().totalAmount())
    }

    @Test
    fun `dates keep the day of ISO values and drop anything else`() {
        assertEquals(LocalDate.of(2026, 3, 1), DocOcrResponse(document_date = "2026-03-01T10:00:00Z").documentDate())
        assertEquals(LocalDate.of(2027, 3, 1), DocOcrResponse(warranty_end_date = "2027-03-01").warrantyEndDate())
        assertNull(DocOcrResponse(document_date = "March 1st").documentDate())
    }
}