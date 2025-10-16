#include <jni.h>
#include <android/log.h>

#include "llama.h"

#include <algorithm>
#include <limits>
#include <memory>
#include <mutex>
#include <sstream>
#include <stdexcept>
#include <string>
#include <vector>

namespace {

constexpr const char* kTag = "CiceroLlama";

class JniString {
public:
    JniString(JNIEnv* env, jstring value)
            : env_(env), value_(value), chars_(env ? env->GetStringUTFChars(value, nullptr) : nullptr) {}

    ~JniString() {
        if (env_ && value_ && chars_) {
            env_->ReleaseStringUTFChars(value_, chars_);
        }
    }

    const char* get() const { return chars_; }

private:
    JNIEnv* env_;
    jstring value_;
    const char* chars_;
};

struct LlamaSession {
    std::string model_path;
    int thread_count = 0;
    int context_size = 0;
    llama_model* model = nullptr;
    llama_context* context = nullptr;
};

std::once_flag g_backend_once;
std::mutex g_backend_mutex;
int g_backend_users = 0;

void retainBackend() {
    std::call_once(g_backend_once, []() {
        ggml_backend_load_all();
        llama_backend_init();
    });

    std::lock_guard<std::mutex> lock(g_backend_mutex);
    ++g_backend_users;
}

void releaseBackend() {
    std::lock_guard<std::mutex> lock(g_backend_mutex);
    if (g_backend_users == 0) {
        return;
    }

    --g_backend_users;
    if (g_backend_users == 0) {
        llama_backend_free();
    }
}

jlong toHandle(LlamaSession* session) {
    return reinterpret_cast<jlong>(session);
}

LlamaSession* fromHandle(jlong handle) {
    return reinterpret_cast<LlamaSession*>(handle);
}

void throwJavaException(JNIEnv* env, const char* class_name, const std::string& message) {
    if (!env) {
        return;
    }

    jclass clazz = env->FindClass(class_name);
    if (!clazz) {
        env->ExceptionClear();
        clazz = env->FindClass("java/lang/RuntimeException");
    }
    env->ThrowNew(clazz, message.c_str());
}

std::string tokenToString(const llama_vocab* vocab, llama_token token) {
    std::vector<char> buffer(128);
    while (true) {
        const int32_t written = llama_token_to_piece(vocab, token, buffer.data(), buffer.size(), 0, true);
        if (written >= 0) {
            return std::string(buffer.data(), written);
        }

        const size_t required = static_cast<size_t>(-written);
        if (required <= buffer.size()) {
            buffer.resize(buffer.size() * 2);
        } else {
            buffer.resize(required);
        }
    }
}

std::vector<llama_token> tokenizePrompt(const llama_model* model, const std::string& prompt) {
    const llama_vocab* vocab = llama_model_get_vocab(model);
    const int32_t estimated = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            nullptr,
            0,
            true,
            true);

    if (estimated == std::numeric_limits<int32_t>::min()) {
        throw std::runtime_error("Jumlah token terlalu besar.");
    }

    const int32_t required = estimated < 0 ? -estimated : estimated;
    std::vector<llama_token> tokens(static_cast<size_t>(required));
    if (required == 0) {
        return tokens;
    }

    const int32_t encoded = llama_tokenize(
            vocab,
            prompt.c_str(),
            static_cast<int32_t>(prompt.size()),
            tokens.data(),
            static_cast<int32_t>(tokens.size()),
            true,
            true);

    if (encoded < 0) {
        throw std::runtime_error("Failed to tokenize prompt");
    }

    tokens.resize(static_cast<size_t>(encoded));
    return tokens;
}

std::string runCompletion(LlamaSession* session, const std::string& prompt, int max_tokens) {
    if (!session || !session->model || !session->context) {
        throw std::runtime_error("Session belum siap digunakan.");
    }

    if (max_tokens <= 0) {
        return std::string();
    }

    auto tokens = tokenizePrompt(session->model, prompt);
    const int total_needed = static_cast<int>(tokens.size()) + max_tokens;
    if (total_needed > session->context_size) {
        std::ostringstream msg;
        msg << "Konteks terlalu kecil: membutuhkan " << total_needed
            << ", tetapi konteks saat ini " << session->context_size << '.';
        throw std::runtime_error(msg.str());
    }

    llama_set_n_threads(session->context, session->thread_count, session->thread_count);
    llama_kv_cache_clear(session->context);

    auto evaluate_tokens = [&](const llama_token* data, int32_t count) {
        if (count <= 0) {
            return;
        }

        const int32_t max_batch = std::max<int32_t>(1, static_cast<int32_t>(llama_n_batch(session->context)));
        int32_t processed = 0;
        while (processed < count) {
            const int32_t chunk = std::min<int32_t>(max_batch, count - processed);
            llama_batch batch = llama_batch_get_one(const_cast<llama_token*>(data + processed), chunk);
            const int32_t status = llama_decode(session->context, batch);
            if (status != 0) {
                std::ostringstream msg;
                msg << "Gagal memproses token (status=" << status << ")";
                throw std::runtime_error(msg.str());
            }
            processed += chunk;
        }
    };

    evaluate_tokens(tokens.data(), static_cast<int32_t>(tokens.size()));

    auto sampler_params = llama_sampler_chain_default_params();
    sampler_params.no_perf = true;
    llama_sampler* sampler = llama_sampler_chain_init(sampler_params);
    if (!sampler) {
        throw std::runtime_error("Tidak dapat membuat sampler llama.");
    }

    std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)> sampler_guard(sampler, &llama_sampler_free);
    if (auto* greedy = llama_sampler_init_greedy()) {
        const bool added = llama_sampler_chain_add(sampler, greedy);
        if (!added) {
            llama_sampler_free(greedy);
            throw std::runtime_error("Tidak dapat menambahkan sampler greedy.");
        }
    } else {
        throw std::runtime_error("Tidak dapat membuat sampler greedy.");
    }

    const llama_vocab* vocab = llama_model_get_vocab(session->model);
    std::string completion;
    completion.reserve(static_cast<size_t>(max_tokens) * 4);

    llama_token previous_token = tokens.empty() ? LLAMA_TOKEN_NULL : tokens.back();

    for (int generated = 0; generated < max_tokens; ++generated) {
        const llama_token next = llama_sampler_sample(sampler, session->context, previous_token);

        if (llama_vocab_is_eog(vocab, next)) {
            break;
        }

        llama_sampler_accept(sampler, next);
        completion += tokenToString(vocab, next);

        evaluate_tokens(&next, 1);
        previous_token = next;
    }

    return completion;
}

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_cicero_ciceroai_llama_LlamaBridge_nativeInit(
        JNIEnv* env,
        jobject /* thiz */,
        jstring modelPath,
        jint threadCount,
        jint contextSize) {
    try {
        JniString path(env, modelPath);
        if (!path.get() || contextSize <= 0 || threadCount <= 0) {
            throw std::runtime_error("Parameter inisialisasi tidak valid.");
        }

        auto session = std::make_unique<LlamaSession>();
        session->model_path = path.get();
        session->thread_count = threadCount;
        session->context_size = contextSize;

        retainBackend();

        llama_model_params model_params = llama_model_default_params();
        model_params.progress_callback = nullptr;
        session->model = llama_model_load_from_file(session->model_path.c_str(), model_params);
        if (!session->model) {
            releaseBackend();
            std::ostringstream msg;
            msg << "Gagal memuat model: " << session->model_path;
            throw std::runtime_error(msg.str());
        }

        llama_context_params ctx_params = llama_context_default_params();
        ctx_params.n_ctx = session->context_size;
        ctx_params.n_batch = std::min(session->context_size, 512);
        ctx_params.n_threads = session->thread_count;
        ctx_params.n_threads_batch = session->thread_count;
        ctx_params.no_perf = true;

        session->context = llama_init_from_model(session->model, ctx_params);
        if (!session->context) {
            llama_model_free(session->model);
            session->model = nullptr;
            releaseBackend();
            throw std::runtime_error("Gagal membuat konteks llama.");
        }

        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "Session siap. Model=%s, threads=%d, ctx=%d",
                            session->model_path.c_str(),
                            session->thread_count,
                            session->context_size);

        return toHandle(session.release());
    } catch (const std::exception& ex) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "nativeInit gagal: %s", ex.what());
        throwJavaException(env, "java/lang/IllegalStateException", ex.what());
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cicero_ciceroai_llama_LlamaBridge_nativeCompletion(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle,
        jstring prompt,
        jint maxTokens) {
    auto* session = fromHandle(handle);
    try {
        if (!session) {
            throw std::runtime_error("Session tidak ditemukan.");
        }

        JniString prompt_utf(env, prompt);
        const std::string prompt_str = prompt_utf.get() ? prompt_utf.get() : "";
        const std::string completion = runCompletion(session, prompt_str, maxTokens);
        return env->NewStringUTF(completion.c_str());
    } catch (const std::exception& ex) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "nativeCompletion gagal: %s", ex.what());
        throwJavaException(env, "java/lang/IllegalStateException", ex.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_cicero_ciceroai_llama_LlamaBridge_nativeRelease(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle) {
    std::unique_ptr<LlamaSession> session{fromHandle(handle)};
    if (!session) {
        return;
    }

    if (session->context) {
        llama_free(session->context);
        session->context = nullptr;
    }
    if (session->model) {
        llama_model_free(session->model);
        session->model = nullptr;
    }

    releaseBackend();
    __android_log_print(ANDROID_LOG_INFO, kTag,
                        "Session ditutup untuk %s",
                        session->model_path.c_str());
}
