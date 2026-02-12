package com.geoquiz.app.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NormalizeInputUseCaseTest {

    private lateinit var normalizeInput: NormalizeInputUseCase

    @Before
    fun setUp() {
        normalizeInput = NormalizeInputUseCase()
    }

    @Test
    fun `lowercase conversion`() {
        assertEquals("france", normalizeInput("FRANCE"))
        assertEquals("france", normalizeInput("France"))
        assertEquals("france", normalizeInput("fRaNcE"))
    }

    @Test
    fun `accent removal`() {
        assertEquals("cote divoire", normalizeInput("Côte d'Ivoire"))
        assertEquals("curacao", normalizeInput("Curaçao"))
        assertEquals("reunion", normalizeInput("Réunion"))
        assertEquals("sao tome and principe", normalizeInput("São Tomé and Príncipe"))
    }

    @Test
    fun `hyphen replaced with space`() {
        assertEquals("timor leste", normalizeInput("Timor-Leste"))
        assertEquals("guinea bissau", normalizeInput("Guinea-Bissau"))
    }

    @Test
    fun `apostrophe removal`() {
        assertEquals("cote divoire", normalizeInput("Cote d'Ivoire"))
        assertEquals("cote divoire", normalizeInput("Cote d\u2019Ivoire"))
    }

    @Test
    fun `whitespace collapsing and trimming`() {
        assertEquals("new zealand", normalizeInput("  New   Zealand  "))
        assertEquals("united states", normalizeInput("United\t States"))
    }

    @Test
    fun `blank input returns empty`() {
        assertEquals("", normalizeInput(""))
        assertEquals("", normalizeInput("   "))
    }

    @Test
    fun `combined normalization`() {
        assertEquals("cote divoire", normalizeInput("  CÔTE D'IVOIRE  "))
    }
}
