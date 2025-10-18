package com.cicero.ciceroai.llama

internal object LlamaBridge {
    init {
        System.loadLibrary("cicero_llama")
    }

    private external fun nativeInitWithConfig(
        modelPath: String,
        runtimeConfig: RuntimeConfig
    ): Long

    @JvmStatic
    fun nativeInit(modelPath: String, runtimeConfig: RuntimeConfig): Long {
        return nativeInitWithConfig(modelPath, runtimeConfig.sanitized())
    }

    @Deprecated(
        message = "Gunakan konfigurasi runtime terstruktur",
        replaceWith = ReplaceWith(
            "nativeInit(modelPath, RuntimeConfig(threadCount, contextSize))"
        )
    )
    fun nativeInit(modelPath: String, threadCount: Int, contextSize: Int): Long {
        return nativeInit(
            modelPath,
            RuntimeConfig(
                threadCount = threadCount,
                contextSize = contextSize
            )
        )
    }

    external fun nativeRelease(handle: Long)

    external fun nativeIsVulkanAvailable(): Boolean

    fun isVulkanAvailable(): Boolean = nativeIsVulkanAvailable()

    fun interface CompletionListener {
        fun onToken(token: String)
    }

    fun nativeCompletionWithProgress(
        handle: Long,
        prompt: String,
        sampling: SamplingConfig,
        listener: CompletionListener?
    ): String {
        val sanitized = sampling.sanitized()
        val nativeListener = listener?.let { NativeCompletionForwarder(it) }
        val stopSequences = sanitized.stopSequences.toTypedArray()
        return nativeCompletionWithOptions(
            handle = handle,
            prompt = prompt,
            maxTokens = sanitized.maxTokens,
            temperature = sanitized.temperature ?: Float.NaN,
            topP = sanitized.topP ?: Float.NaN,
            topK = sanitized.topK ?: -1,
            repeatPenalty = sanitized.repeatPenalty ?: Float.NaN,
            repeatLastN = sanitized.repeatLastN ?: -1,
            frequencyPenalty = sanitized.frequencyPenalty ?: Float.NaN,
            presencePenalty = sanitized.presencePenalty ?: Float.NaN,
            stopSequences = stopSequences,
            seed = sanitized.seed ?: SAMPLING_SEED_UNSET,
            listener = nativeListener
        )
    }

    fun nativeCompletionWithProgress(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        listener: CompletionListener?
    ): String {
        return nativeCompletionWithProgress(
            handle,
            prompt,
            SamplingConfig(maxTokens = maxTokens),
            listener
        )
    }

    fun nativeCompletion(handle: Long, prompt: String, maxTokens: Int): String {
        return nativeCompletionWithOptions(
            handle = handle,
            prompt = prompt,
            maxTokens = maxTokens,
            temperature = Float.NaN,
            topP = Float.NaN,
            topK = -1,
            repeatPenalty = Float.NaN,
            repeatLastN = -1,
            frequencyPenalty = Float.NaN,
            presencePenalty = Float.NaN,
            stopSequences = emptyArray(),
            seed = SAMPLING_SEED_UNSET,
            listener = null
        )
    }

    private external fun nativeCompletionWithOptions(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        repeatLastN: Int,
        frequencyPenalty: Float,
        presencePenalty: Float,
        stopSequences: Array<String>,
        seed: Int,
        listener: NativeCompletionListener?
    ): String

    private const val SAMPLING_SEED_UNSET: Int = -1

    private class NativeCompletionForwarder(
        private val delegate: CompletionListener
    ) : NativeCompletionListener {
        override fun onTokenGenerated(token: String) {
            delegate.onToken(token)
        }
    }

    private interface NativeCompletionListener {
        fun onTokenGenerated(token: String)
    }
}
