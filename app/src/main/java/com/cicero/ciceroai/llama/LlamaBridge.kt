package com.cicero.ciceroai.llama

internal object LlamaBridge {
    init {
        System.loadLibrary("cicero_llama")
    }

    external fun nativeInit(modelPath: String, threadCount: Int, contextSize: Int): Long
    external fun nativeRelease(handle: Long)

    fun interface CompletionListener {
        fun onToken(token: String)
    }

    fun nativeCompletionWithProgress(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        listener: CompletionListener?
    ): String {
        val nativeListener = listener?.let { NativeCompletionForwarder(it) }
        return nativeCompletion(handle, prompt, maxTokens, nativeListener)
    }

    fun nativeCompletion(handle: Long, prompt: String, maxTokens: Int): String {
        return nativeCompletion(handle, prompt, maxTokens, null)
    }

    private external fun nativeCompletion(
        handle: Long,
        prompt: String,
        maxTokens: Int,
        listener: NativeCompletionListener?
    ): String

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
