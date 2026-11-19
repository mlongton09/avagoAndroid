package com.avago.core.data.v2

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class V2OverlayMergeTest {

    private val base = listOf<V2Row>(
        mapOf("id" to "a", "name" to "Alpha", "qty" to 5L, "site" to "s1"),
        mapOf("id" to "b", "name" to "Beta", "qty" to 1L, "site" to "s1"),
    )

    private fun merge(vararg ops: V2RowOp, lookup: (Map<String, Any?>) -> V2Row? = { null }, predicate: (V2Row) -> Boolean = { true }) =
        V2OverlayMerge.apply(base, ops.toList(), predicate, lookup)

    @Test
    fun `upsert sets columns over the base row and null clears`() {
        val rows = merge(V2RowOp.upsert("t", "a", mapOf("name" to "Renamed", "site" to null)))
        val a = rows.first { it["id"] == "a" }
        assertEquals("Renamed", a["name"])
        assertNull(a["site"])
        assertEquals(5L, a["qty"])
    }

    @Test
    fun `upsert of an unknown key creates the row`() {
        val rows = merge(V2RowOp.upsert("t", "c", mapOf("name" to "New")))
        assertEquals(listOf("a", "b", "c"), rows.map { it["id"] })
    }

    @Test
    fun `delete hides the row`() {
        assertEquals(listOf("b"), merge(V2RowOp.delete("t", "a")).map { it["id"] })
    }

    @Test
    fun `add deltas accumulate in causal order`() {
        val rows = merge(
            V2RowOp.upsert("t", mapOf("id" to "a"), emptyMap(), mapOf("qty" to -2L)),
            V2RowOp.upsert("t", mapOf("id" to "a"), emptyMap(), mapOf("qty" to 10L)),
        )
        assertEquals(13L, rows.first { it["id"] == "a" }["qty"])
    }

    @Test
    fun `rows moved by an overlay are re-checked against the filter`() {
        val moved = V2RowOp.upsert("t", "a", mapOf("site" to "s2"))
        assertEquals(listOf("b"), merge(moved) { it["site"] == "s1" }.map { it["id"] })

        val outside = mapOf("id" to "z", "name" to "Zed", "qty" to 0L, "site" to "s2")
        val movedIn = V2RowOp.upsert("t", "z", mapOf("site" to "s1"))
        val rows = merge(movedIn, lookup = { key -> outside.takeIf { key["id"] == "z" } }) { it["site"] == "s1" }
        assertEquals(listOf("a", "b", "z"), rows.map { it["id"] })
        assertEquals("Zed", rows.last()["name"])
    }

    @Test
    fun `a patch survives its json round trip`() {
        val patch = V2OverlayPatch.of(
            V2RowOp.upsert("t", mapOf("id" to "a"), mapOf("n" to null, "b" to true, "d" to 1.5), mapOf("qty" to 3L)),
            V2RowOp.delete("u", mapOf("x" to "1", "y" to "2")),
        )
        val parsed = V2OverlayPatch.parse(patch.toJson())!!
        assertEquals(mapOf("n" to null, "b" to 1L, "d" to 1.5), parsed.ops[0].set)
        assertEquals(mapOf("qty" to 3L), parsed.ops[0].add)
        assertEquals(V2RowOp.Kind.DELETE, parsed.ops[1].kind)
        assertEquals(mapOf("x" to "1", "y" to "2"), parsed.ops[1].key)
    }
}
