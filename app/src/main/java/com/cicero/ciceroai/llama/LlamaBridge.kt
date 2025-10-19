package com.cicero.ciceroai.llama

import android.util.Log

internal object LlamaBridge {
    private const val TAG = "LlamaBridge"

    private val libraryLoadError: Throwable?

    private val isLibraryLoaded: Boolean
        get() = libraryLoadError == null

    init {
        libraryLoadError = runCatching { System.loadLibrary("cicero_llama") }
            .onFailure { error ->
                Log.e(TAG, "Failed to load native library libcicero_llama.so", error)
            }
            .exceptionOrNull()
    }

    private fun ensureLibraryLoaded() {
        if (!isLibraryLoaded) {
            throw IllegalStateException(
                "Native inference runtime (libcicero_llama.so) is not available. " +
                    "Ensure the JNI library is bundled with the application.",
                libraryLoadError
            )
        }
    }

    private external fun nativeInitWithConfig(
        modelPath: String,
        runtimeConfig: RuntimeConfig
    ): Long

    @JvmStatic
    fun nativeInit(modelPath: String, runtimeConfig: RuntimeConfig): Long {
        ensureLibraryLoaded()
        return nativeInitWithConfig(modelPath, runtimeConfig.sanitized())
    }

    @Deprecated(
        message = "Gunakan konfigurasi runtime terstruktur",
        replaceWith = ReplaceWith(
            "nativeInit(modelPath, RuntimeConfig(threadCount, contextSize))"
        )
    )
    fun nativeInit(modelPath: String, threadCount: Int, contextSize: Int): Long {
        ensureLibraryLoaded()
        return nativeInit(
            modelPath,
            RuntimeConfig(
                threadCount = threadCount,
                contextSize = contextSize
            )
        )
    }

    private external fun nativeReleaseInternal(handle: Long)

    fun nativeRelease(handle: Long) {
        if (!isLibraryLoaded) {
            return
        }
        nativeReleaseInternal(handle)
    }

    external fun nativeIsVulkanAvailable(): Boolean

    fun isVulkanAvailable(): Boolean? {
        if (!isLibraryLoaded) {
            return null
        }
        return nativeIsVulkanAvailable()
    }

    fun interface CompletionListener {
        fun onToken(token: String)
    }

    fun nativeCompletionWithProgress(
        handle: Long,
        prompt: String,
        sampling: SamplingConfig,
        listener: CompletionListener?
    ): String {
        ensureLibraryLoaded()
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
        ensureLibraryLoaded()
        return nativeCompletionWithProgress(
            handle,
            prompt,
            SamplingConfig(maxTokens = maxTokens),
            listener
        )
    }

    fun nativeCompletion(handle: Long, prompt: String, maxTokens: Int): String {
        ensureLibraryLoaded()
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

