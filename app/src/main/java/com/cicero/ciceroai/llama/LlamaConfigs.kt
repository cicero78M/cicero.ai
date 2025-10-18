package com.cicero.ciceroai.llama

import kotlin.math.max

/**
 * Configuration for llama.cpp runtime initialisation. Values that are `null` indicate that the
 * llama defaults should be preserved.
 */
data class RuntimeConfig(
    val threadCount: Int,
    val contextSize: Int,
    val threadCountBatch: Int? = null,
    val batchSize: Int? = null,
    val ubatchSize: Int? = null,
    val seqMax: Int? = null,
    val nGpuLayers: Int? = null,
    val mainGpu: Int? = null,
    val flashAttention: Int? = null,
    val ropeFreqBase: Float? = null,
    val ropeFreqScale: Float? = null,
    val offloadKqv: Boolean? = null,
    val noPerf: Boolean? = null,
    val embeddings: Boolean? = null,
    val kvUnified: Boolean? = null,
    val useMmap: Boolean? = null,
    val useMlock: Boolean? = null
) {
    init {
        require(threadCount > 0) { "threadCount harus lebih besar dari 0" }
        require(contextSize > 0) { "contextSize harus lebih besar dari 0" }
    }

    fun sanitized(): RuntimeConfig {
        return copy(
            threadCount = max(1, threadCount),
            contextSize = max(1, contextSize),
            threadCountBatch = threadCountBatch?.takeIf { it > 0 },
            batchSize = batchSize?.takeIf { it > 0 },
            ubatchSize = ubatchSize?.takeIf { it > 0 },
            seqMax = seqMax?.takeIf { it > 0 },
            nGpuLayers = nGpuLayers?.takeIf { it >= 0 },
            mainGpu = mainGpu?.takeIf { it >= 0 },
            flashAttention = flashAttention?.takeIf { it in -1..1 },
            ropeFreqBase = ropeFreqBase?.takeIf { it > 0f },
            ropeFreqScale = ropeFreqScale?.takeIf { it > 0f }
        )
    }

    companion object {
        fun withDefaults(threadCount: Int, contextSize: Int): RuntimeConfig {
            return RuntimeConfig(
                threadCount = max(1, threadCount),
                contextSize = max(1, contextSize)
            )
        }
    }
}

/**
 * Sampling hyper-parameters for llama.cpp generation.
 */
data class SamplingConfig(
    val maxTokens: Int,
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val repeatPenalty: Float? = null,
    val repeatLastN: Int? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val stopSequences: List<String> = emptyList(),
    val seed: Int? = null
) {
    init {
        require(maxTokens >= 0) { "maxTokens tidak boleh negatif" }
    }

    fun sanitized(): SamplingConfig {
        val sanitizedTemperature = temperature?.takeIf { it > 0f && it.isFinite() }
        val sanitizedTopP = topP?.takeIf { it.isFinite() && it in 0f..1f }
        val sanitizedTopK = topK?.takeIf { it > 0 }
        val sanitizedRepeatPenalty = repeatPenalty?.takeIf { it > 0f && it.isFinite() }
        val sanitizedRepeatLastN = repeatLastN?.takeIf { it >= 0 }
        val sanitizedFrequencyPenalty = frequencyPenalty?.takeIf { it.isFinite() }
        val sanitizedPresencePenalty = presencePenalty?.takeIf { it.isFinite() }
        val sanitizedSeed = seed?.takeIf { it >= 0 }
        val sanitizedStops = stopSequences.mapNotNull { sequence ->
            sequence.takeIf { it.isNotEmpty() }
        }

        return copy(
            maxTokens = max(0, maxTokens),
            temperature = sanitizedTemperature,
            topP = sanitizedTopP,
            topK = sanitizedTopK,
            repeatPenalty = sanitizedRepeatPenalty,
            repeatLastN = sanitizedRepeatLastN,
            frequencyPenalty = sanitizedFrequencyPenalty,
            presencePenalty = sanitizedPresencePenalty,
            stopSequences = sanitizedStops,
            seed = sanitizedSeed
        )
    }

    companion object {
        fun withDefaults(maxTokens: Int): SamplingConfig {
            return SamplingConfig(maxTokens = max(0, maxTokens))
        }
    }
}

/**
 * Parser helpers for turning loosely structured DataStore strings into strongly typed configs. The
 * strings are expected to contain JSON blobs but we fall back to sensible defaults when parsing
 * fails so legacy free-form notes still work.
 */
object LlamaSettingsParser {
    fun parseRuntimeConfig(raw: String?, fallbackThreads: Int, fallbackContext: Int): RuntimeConfig {
        val defaultConfig = RuntimeConfig.withDefaults(fallbackThreads, fallbackContext)
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty() || !candidate.startsWith("{")) {
            return defaultConfig
        }

        return runCatching { RuntimeConfigJsonAdapter.fromJson(candidate, fallbackThreads, fallbackContext) }
            .getOrElse { defaultConfig }
            .sanitized()
    }

    fun parseSamplingConfig(raw: String?, defaultMaxTokens: Int): SamplingConfig {
        val defaultConfig = SamplingConfig.withDefaults(defaultMaxTokens)
        val candidate = raw?.trim().orEmpty()
        if (candidate.isEmpty() || !candidate.startsWith("{")) {
            return defaultConfig
        }

        return runCatching { SamplingConfigJsonAdapter.fromJson(candidate, defaultMaxTokens) }
            .getOrElse { defaultConfig }
            .sanitized()
    }
}

private object RuntimeConfigJsonAdapter {
    fun fromJson(raw: String, fallbackThreads: Int, fallbackContext: Int): RuntimeConfig {
        val json = org.json.JSONObject(raw)
        val threadsValue = json.opt("threads") ?: json.opt("thread_count") ?: json.opt("n_threads")
        val (threadCount, threadBatch) = parseThreadCounts(threadsValue, fallbackThreads)
        val context = extractInt(json, "context", "context_size", "n_ctx", "ctx") ?: fallbackContext
        val batch = extractInt(json, "batch", "n_batch")
        val ubatch = extractInt(json, "ubatch", "n_ubatch")
        val seqMax = extractInt(json, "seq_max", "n_seq_max")
        val nGpuLayers = extractInt(json, "n_gpu_layers", "gpu_layers")
        val mainGpu = extractInt(json, "main_gpu")
        val flashAttention = extractFlashAttention(json.opt("flash_attn") ?: json.opt("flash_attention"))
        val ropeFreqBase = extractFloat(json, "rope_freq_base")
        val ropeFreqScale = extractFloat(json, "rope_freq_scale")
        val offloadKqv = extractBoolean(json, "offload_kqv")
        val noPerf = extractBoolean(json, "no_perf")
        val embeddings = extractBoolean(json, "embeddings")
        val kvUnified = extractBoolean(json, "kv_unified")
        val useMmap = extractBoolean(json, "use_mmap")
        val useMlock = extractBoolean(json, "use_mlock")

        return RuntimeConfig(
            threadCount = threadCount,
            contextSize = context,
            threadCountBatch = threadBatch,
            batchSize = batch,
            ubatchSize = ubatch,
            seqMax = seqMax,
            nGpuLayers = nGpuLayers,
            mainGpu = mainGpu,
            flashAttention = flashAttention,
            ropeFreqBase = ropeFreqBase,
            ropeFreqScale = ropeFreqScale,
            offloadKqv = offloadKqv,
            noPerf = noPerf,
            embeddings = embeddings,
            kvUnified = kvUnified,
            useMmap = useMmap,
            useMlock = useMlock
        )
    }

    private fun parseThreadCounts(value: Any?, fallbackThreads: Int): Pair<Int, Int?> {
        return when (value) {
            is Number -> value.toInt().coerceAtLeast(1) to null
            is String -> {
                val trimmed = value.trim()
                if (trimmed.equals("auto", ignoreCase = true) || trimmed.isEmpty()) {
                    fallbackThreads.coerceAtLeast(1) to null
                } else {
                    trimmed.toIntOrNull()?.takeIf { it > 0 }?.let { it to null }
                        ?: (fallbackThreads.coerceAtLeast(1) to null)
                }
            }
            is org.json.JSONObject -> {
                val inference = extractInt(value, "inference", "decode", "eval", "generation")
                    ?: fallbackThreads
                val batch = extractInt(value, "batch", "batch_eval", "thread_count_batch")
                inference.coerceAtLeast(1) to batch?.takeIf { it > 0 }
            }
            else -> fallbackThreads.coerceAtLeast(1) to null
        }
    }
}

private object SamplingConfigJsonAdapter {
    fun fromJson(raw: String, defaultMaxTokens: Int): SamplingConfig {
        val json = org.json.JSONObject(raw)
        val maxTokens = extractInt(json, "max_tokens", "max_new_tokens") ?: defaultMaxTokens
        val temperature = extractFloat(json, "temperature", "temp")
        val topP = extractFloat(json, "top_p")
        val topK = extractInt(json, "top_k")
        val repeatPenalty = extractFloat(json, "repeat_penalty", "presence_penalty_scale")
        val repeatLastN = extractInt(json, "repeat_last_n")
        val frequencyPenalty = extractFloat(json, "frequency_penalty")
        val presencePenalty = extractFloat(json, "presence_penalty")
        val seed = extractInt(json, "seed")

        val stopsRaw = json.opt("stop_sequences") ?: json.opt("stop") ?: json.opt("stops")
        val stopSequences = when (stopsRaw) {
            is org.json.JSONArray -> buildList {
                for (index in 0 until stopsRaw.length()) {
                    val entry = stopsRaw.optString(index)
                    if (!entry.isNullOrEmpty()) {
                        add(entry)
                    }
                }
            }
            is String -> listOfNotNull(stopsRaw.takeIf { it.isNotEmpty() })
            else -> emptyList()
        }

        return SamplingConfig(
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP,
            topK = topK,
            repeatPenalty = repeatPenalty,
            repeatLastN = repeatLastN,
            frequencyPenalty = frequencyPenalty,
            presencePenalty = presencePenalty,
            stopSequences = stopSequences,
            seed = seed
        )
    }
}

private fun extractInt(json: org.json.JSONObject, vararg keys: String): Int? {
    for (key in keys) {
        if (!json.has(key) || json.isNull(key)) continue
        val value = json.get(key)
        val intValue = when (value) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
        if (intValue != null) {
            return intValue
        }
    }
    return null
}

private fun extractFloat(json: org.json.JSONObject, vararg keys: String): Float? {
    for (key in keys) {
        if (!json.has(key) || json.isNull(key)) continue
        val value = json.get(key)
        val floatValue = when (value) {
            is Number -> value.toFloat()
            is String -> value.trim().toFloatOrNull()
            else -> null
        }
        if (floatValue != null) {
            return floatValue
        }
    }
    return null
}

private fun extractBoolean(json: org.json.JSONObject, vararg keys: String): Boolean? {
    for (key in keys) {
        if (!json.has(key) || json.isNull(key)) continue
        val value = json.get(key)
        val boolValue = when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            is String -> value.trim().lowercase().let {
                when (it) {
                    "true", "1", "yes", "enabled", "enable" -> true
                    "false", "0", "no", "disabled", "disable" -> false
                    else -> null
                }
            }
            else -> null
        }
        if (boolValue != null) {
            return boolValue
        }
    }
    return null
}

private fun extractFlashAttention(value: Any?): Int? {
    return when (value) {
        is Number -> value.toInt().takeIf { it in -1..1 }
        is Boolean -> if (value) 1 else 0
        is String -> when (value.trim().lowercase()) {
            "auto" -> -1
            "enabled", "enable", "true", "on" -> 1
            "disabled", "disable", "false", "off" -> 0
            else -> null
        }
        else -> null
    }
}
