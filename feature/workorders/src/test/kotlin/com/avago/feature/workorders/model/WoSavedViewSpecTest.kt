package com.avago.feature.workorders.model

import com.avago.feature.workorders.viewmodel.WoHorizon
import com.avago.feature.workorders.viewmodel.WoListFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WoSavedViewSpecTest {

    @Test
    fun `a spec round-trips and compares equal regardless of status order`() {
        val spec = WoSavedViewSpec.of(" pump ", setOf("on_hold", "assigned"), WoListFilter.ALL, WoHorizon.LATER)
        val parsed = WoSavedViewSpec.parse(spec.toJson())
        assertEquals(spec, parsed)
        assertEquals("pump", parsed!!.query)
        assertEquals(WoListFilter.ALL, parsed.scopeFilter)
        assertEquals(WoHorizon.LATER, parsed.horizonFilter)
        assertEquals(spec, WoSavedViewSpec.of("pump", setOf("assigned", "on_hold"), WoListFilter.ALL, WoHorizon.LATER))
    }

    @Test
    fun `missing or unknown keys fall back to the defaults and foreign specs are rejected`() {
        val partial = WoSavedViewSpec.parse("""{"scope":"SOMEONE","extra":1}""")!!
        assertEquals(WoListFilter.MINE, partial.scopeFilter)
        assertEquals(WoHorizon.NOW, partial.horizonFilter)
        assertEquals(emptyList<String>(), partial.statuses)
        assertNull(WoSavedViewSpec.parse("""["not","an","object"]"""))
        assertNull(WoSavedViewSpec.parse("""{"statuses":"assigned"}"""))
    }
}
