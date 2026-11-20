package com.avago.feature.rentalsops.domain

import com.avago.feature.rentalsops.data.RentalOpsApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FinanceTest {
    private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    @Test
    fun `aging report keeps fixed bucket order and computes over-60 share`() {
        val r = AgingReport.parse(
            obj(
                """{"as_of":"2026-05-01","buckets":["current","1_30","31_60","61_90","over_90"],
                "customers":[
                  {"customer_id":"c1","name":"Acme","currency":"EUR","buckets":{"current":100,"1_30":0,"31_60":0,"61_90":300,"over_90":"600","total":1000},
                   "invoices":[{"invoice_id":"i1","number":"INV-1","due_at":"2026-01-01T00:00:00Z","days_past_due":120,"bucket":"over_90","balance_minor":600}]},
                  {"customer_id":"c2","name":"Small","currency":"EUR","buckets":{"current":50}}
                ],
                "totals":[{"currency":"EUR","buckets":{"current":150,"61_90":300,"over_90":600}}]}""",
            ),
        )
        assertEquals("2026-05-01", r.asOf)
        val eur = r.totals.single()
        assertEquals(listOf(150L, 0L, 0L, 300L, 600L), AgingBucket.entries.map { eur[it] })
        assertEquals(0.857, r.over60Share("EUR")!!, 0.001)
        assertNull(r.over60Share("USD"))
        // Largest balance first; string minor amounts accepted.
        assertEquals(listOf("c1", "c2"), r.customers.map { it.customerId })
        assertEquals(AgingBucket.OVER_90, r.customers[0].invoices.single().bucket)
    }

    @Test
    fun `aging totals fall back to customer sums per currency`() {
        val r = AgingReport.parse(
            obj(
                """{"customers":[
                  {"customer_id":"a","currency":"USD","buckets":{"d1_30":10}},
                  {"customer_id":"b","currency":"USD","buckets":{"1_30":5,"current":1}},
                  {"customer_id":"c","currency":"EUR","buckets":{"current":7}}]}""",
            ),
        )
        assertEquals(listOf("EUR", "USD"), r.totals.map { it.currency })
        assertEquals(15L, r.totals[1][AgingBucket.D1_30])
        assertEquals(0.0, r.over60Share("USD")!!, 0.0)
    }

    @Test
    fun `statement parses invoices newest first and balances by currency`() {
        val s = Statement.parse(
            obj(
                """{"invoices":[
                  {"id":"i1","number":"A","issued_at":"2026-01-01T00:00:00Z","currency":"EUR","total_minor":1000,"balance_minor":0},
                  {"id":"i2","number":"B","issued_at":"2026-03-01T00:00:00Z","currency":"EUR","total_minor":"2500","balance_minor":2500}],
                 "balance_by_currency":{"EUR":2500},"customer_credit_minor":0}""",
            ),
        )
        assertEquals(listOf("i2", "i1"), s.invoices.map { it.id })
        assertEquals(mapOf("EUR" to 2500L), s.balanceByCurrency)
        assertEquals(0L, s.creditMinor)
    }

    @Test
    fun `statement e-mail body carries the idempotency key and only given fields`() {
        assertEquals(
            obj("""{"client_uuid":"u1"}"""),
            RentalOpsApi.statementEmailBody("u1", emptyList(), null),
        )
        assertEquals(
            obj("""{"client_uuid":"u2","recipients":["a@b.c"],"message":"Hi"}"""),
            RentalOpsApi.statementEmailBody("u2", listOf("a@b.c"), "Hi"),
        )
    }
}
