package com.avago.feature.assets.viewmodel

import com.avago.core.data.domain.AssetLabelTemplate
import com.avago.feature.assets.model.QrLabelLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class QrLabelSheetStateTest {
    private val default = template("d", 50.0, 25.0, isDefault = true)
    private val big = template("b", 250.0, 100.0)

    @Test
    fun `thermal is offered only when a template applies`() {
        assertEquals(QrLabelLayout.entries - QrLabelLayout.THERMAL, QrLabelSheetState().layouts)
        assertEquals(QrLabelLayout.entries.toList(), QrLabelSheetState(templates = listOf(default)).layouts)
    }

    @Test
    fun `a layout the chosen template does not fit falls back to one it prints on`() {
        val state = QrLabelSheetState(templates = listOf(default, big), layout = QrLabelLayout.AVERY_5160)
            .copy(templateId = "b")
            .normalized()
        assertEquals(big, state.template)
        assertEquals(listOf(QrLabelLayout.THERMAL), state.layouts)
        assertEquals(QrLabelLayout.THERMAL, state.layout)
    }

    @Test
    fun `a template removed by sync falls back to the account default`() {
        val chosen = QrLabelSheetState(templates = listOf(default, big), templateId = "b", layout = QrLabelLayout.THERMAL)

        val synced = chosen.copy(templates = listOf(default)).normalized()
        assertNull(synced.templateId)
        assertEquals(default, synced.template)
        assertEquals(QrLabelLayout.THERMAL, synced.layout)

        val noTemplates = chosen.copy(templates = emptyList()).normalized()
        assertNull(noTemplates.template)
        assertEquals(QrLabelLayout.LETTER, noTemplates.layout)
    }

    @Test
    fun `the default template is chosen through the account default, not by id`() {
        val state = QrLabelSheetState(templates = listOf(default), templateId = "d").normalized()
        assertNull(state.templateId)
        assertEquals(default, state.template)
    }

    private fun template(id: String, w: Double, h: Double, isDefault: Boolean = false) =
        AssetLabelTemplate(id, "Template $id", BigDecimal.valueOf(w), BigDecimal.valueOf(h), isDefault)
}
