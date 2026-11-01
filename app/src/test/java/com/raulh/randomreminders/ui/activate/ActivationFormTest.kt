package com.raulh.randomreminders.ui.activate

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationFormTest {

    @Test
    fun `formulario padrao e valido`() {
        val parsed = parseActivationForm(ActivationForm())
        assertTrue(parsed.rule != null)
        assertTrue(parsed.fieldErrors.isEmpty())
        assertTrue(parsed.rule?.validationErrors().isNullOrEmpty())
    }

    @Test
    fun `horarios mal formatados viram erro de campo`() {
        val form = ActivationForm(start = "7h", end = "2500")
        val parsed = parseActivationForm(form)

        assertTrue(ActivationField.START in parsed.fieldErrors)
        assertTrue(ActivationField.END in parsed.fieldErrors)
        assertFalse(parsed.rule != null)
    }

    @Test
    fun `horario sem zero a esquerda e aceito`() {
        val parsed = parseActivationForm(ActivationForm(start = "7:05"))
        assertEquals(LocalTime.of(7, 5), parsed.rule?.start)
    }

    @Test
    fun `intervalo zero indica que nao deve repetir em outros dias`() {
        val parsed = parseActivationForm(ActivationForm(intervalDays = "0"))

        assertEquals(0, parsed.rule?.intervalDays)
        assertTrue(parsed.fieldErrors.isEmpty())
        assertTrue(parsed.rule?.validationErrors().isNullOrEmpty())
    }

    @Test
    fun `valores nao numericos viram erro de campo`() {
        val form = ActivationForm(shots = "x", spacing = "", intervalDays = "zero")
        val parsed = parseActivationForm(form)

        assertTrue(ActivationField.SHOTS in parsed.fieldErrors)
        assertTrue(ActivationField.SPACING in parsed.fieldErrors)
        assertTrue(ActivationField.INTERVAL in parsed.fieldErrors)
        assertFalse(parsed.rule != null)
    }

    @Test
    fun `janela que nao comporta os disparos gera erro de regra`() {
        val form = ActivationForm(start = "08:00", end = "10:00", shots = "10", spacing = "30")
        val parsed = parseActivationForm(form)

        // Parse ok, mas regra de negócio reprova: 9 * 30 min > 120 min de janela.
        assertTrue(parsed.rule != null)
        assertTrue(parsed.rule?.validationErrors()?.isNotEmpty() == true)
    }

    @Test
    fun `inicio depois do fim gera erro de regra`() {
        val form = ActivationForm(start = "20:00", end = "08:00")
        val parsed = parseActivationForm(form)

        assertTrue(parsed.rule?.validationErrors()!!.any { it.contains("antes") })
    }

    @Test
    fun `update aplica o campo pedido`() {
        val form = ActivationForm().updated(ActivationField.SPACING, "45")
        assertEquals("45", form.spacing)
        assertEquals("07:00", form.start)
    }
}
