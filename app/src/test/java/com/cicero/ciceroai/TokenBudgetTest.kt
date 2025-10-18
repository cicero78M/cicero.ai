package com.cicero.ciceroai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenBudgetTest {

    @Test
    fun `deriveDefaultMaxTokens scales with context size`() {
        assertEquals(0, deriveDefaultMaxTokens(0))
        assertEquals(512, deriveDefaultMaxTokens(1024))
        assertEquals(1024, deriveDefaultMaxTokens(2048))
        assertEquals(2048, deriveDefaultMaxTokens(4096))
    }

    @Test
    fun `computeTokenBudget clamps to remaining context`() {
        val prompt = List(100) { "word" }.joinToString(separator = " ")
        val configuredMaxTokens = 500
        val budget = computeTokenBudget(
            prompt = prompt,
            contextSize = 1024,
            configuredMaxTokens = configuredMaxTokens
        )

        assertTrue(budget.promptTokens > 0)
        assertEquals(budget.remainingTokens, 1024 - budget.promptTokens)
        assertTrue(budget.maxTokens <= budget.remainingTokens)
        assertTrue(budget.maxTokens <= configuredMaxTokens)
    }

    @Test
    fun `token budget reflects context slider changes`() {
        val prompt = "Hello world"
        val smallerContextBudget = computeTokenBudget(
            prompt = prompt,
            contextSize = 512,
            configuredMaxTokens = deriveDefaultMaxTokens(512)
        )
        val largerContextBudget = computeTokenBudget(
            prompt = prompt,
            contextSize = 2048,
            configuredMaxTokens = deriveDefaultMaxTokens(2048)
        )

        assertTrue(largerContextBudget.remainingTokens > smallerContextBudget.remainingTokens)
        assertTrue(largerContextBudget.maxTokens > smallerContextBudget.maxTokens)
    }
}
