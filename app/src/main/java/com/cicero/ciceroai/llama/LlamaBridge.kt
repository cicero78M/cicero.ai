package com.cicero.ciceroai.llama

internal object LlamaBridge {
    init {
        System.loadLibrary("cicero_llama")
    }

    external fun nativeInit(modelPath: String, threadCount: Int, contextSize: Int): Long
    external fun nativeCompletion(handle: Long, prompt: String, maxTokens: Int): String
    external fun nativeRelease(handle: Long)
}
