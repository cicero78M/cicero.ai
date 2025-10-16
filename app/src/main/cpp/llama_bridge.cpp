#include <jni.h>
#include <android/log.h>

#include <memory>
#include <sstream>
#include <string>

namespace {

struct LlamaSession {
    std::string model_path;
    int thread_count;
    int context_size;
};

constexpr const char* kTag = "CiceroLlama";

jlong toHandle(LlamaSession* session) {
    return reinterpret_cast<jlong>(session);
}

LlamaSession* fromHandle(jlong handle) {
    return reinterpret_cast<LlamaSession*>(handle);
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_cicero_ciceroai_llama_LlamaBridge_nativeInit(
        JNIEnv* env,
        jobject /* thiz */,
        jstring modelPath,
        jint threadCount,
        jint contextSize) {
    const char* path_chars = env->GetStringUTFChars(modelPath, nullptr);
    auto session = std::make_unique<LlamaSession>();
    session->model_path = path_chars ? path_chars : "";
    session->thread_count = threadCount;
    session->context_size = contextSize;
    env->ReleaseStringUTFChars(modelPath, path_chars);

    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Initialized stub session for %s (threads=%d, ctx=%d)",
                        session->model_path.c_str(),
                        session->thread_count,
                        session->context_size);

    return toHandle(session.release());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cicero_ciceroai_llama_LlamaBridge_nativeCompletion(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle,
        jstring prompt,
        jint maxTokens) {
    auto* session = fromHandle(handle);
    const char* prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    std::ostringstream builder;
    builder << "[STUB OUTPUT]\n";
    builder << "Model path: " << (session ? session->model_path : "<none>") << '\n';
    builder << "Threads: " << (session ? session->thread_count : 0) << '\n';
    builder << "Context size: " << (session ? session->context_size : 0) << '\n';
    builder << "Max tokens: " << maxTokens << '\n';
    builder << "Prompt:\n" << (prompt_chars ? prompt_chars : "") << '\n';
    builder << "---\n";
    builder << "Integrasikan llama.cpp untuk hasil inferensi nyata.";
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    return env->NewStringUTF(builder.str().c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_cicero_ciceroai_llama_LlamaBridge_nativeRelease(
        JNIEnv* /* env */,
        jobject /* thiz */,
        jlong handle) {
    std::unique_ptr<LlamaSession> session{fromHandle(handle)};
    if (session) {
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "Released session for %s",
                            session->model_path.c_str());
    }
}
