package com.cicero.ciceroai

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DEFAULT_MAX_TOKEN_FRACTION = 0.5
private const val MIN_DEFAULT_MAX_TOKENS = 16
private const val AVERAGE_CHARS_PER_TOKEN = 4.0
private val WHITESPACE_REGEX = "\\s+".toRegex()

internal data class TokenBudget(
    val promptTokens: Int,
    val remainingTokens: Int,
    val maxTokens: Int
)

internal fun deriveDefaultMaxTokens(contextSize: Int): Int {
    val sanitizedContext = contextSize.coerceAtLeast(0)
    if (sanitizedContext == 0) {
        return 0
    }
    val scaled = (sanitizedContext * DEFAULT_MAX_TOKEN_FRACTION).roundToInt()
    val candidate = max(MIN_DEFAULT_MAX_TOKENS, scaled)
    return min(sanitizedContext, candidate)
}

internal fun estimatePromptTokens(prompt: String): Int {
    val trimmed = prompt.trim()
    if (trimmed.isEmpty()) {
        return 0
    }
    val wordEstimate = WHITESPACE_REGEX.split(trimmed).count { it.isNotEmpty() }
    val charEstimate = max(1, (trimmed.length / AVERAGE_CHARS_PER_TOKEN).roundToInt())
    return max(wordEstimate, charEstimate)
}

internal fun computeTokenBudget(
    prompt: String,
    contextSize: Int,
    configuredMaxTokens: Int
): TokenBudget {
    val sanitizedContext = contextSize.coerceAtLeast(0)
    val promptTokens = estimatePromptTokens(prompt)
    val remaining = max(0, sanitizedContext - promptTokens)
    val sanitizedConfigured = configuredMaxTokens.coerceAtLeast(0)
    val effectiveMaxTokens = min(remaining, sanitizedConfigured)
    return TokenBudget(
        promptTokens = promptTokens,
        remainingTokens = remaining,
        maxTokens = effectiveMaxTokens
    )
}
