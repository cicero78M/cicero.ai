#include <jni.h>
#include <android/log.h>

#include "llama.h"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <functional>
#include <limits>
#include <memory>
#include <mutex>
#include <optional>
#include <sstream>
#include <stdexcept>
#include <string>
#include <type_traits>
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
    int thread_count_batch = 0;
    int context_size = 0;
    llama_model* model = nullptr;
    llama_context* context = nullptr;
    int32_t tokens_processed = 0;
};

struct RuntimeNativeConfig {
    int32_t thread_count = 0;
    int32_t thread_count_batch = 0;
    bool has_thread_count_batch = false;
    int32_t context_size = 0;
    int32_t batch_size = 0;
    bool has_batch_size = false;
    int32_t ubatch_size = 0;
    bool has_ubatch_size = false;
    int32_t seq_max = 0;
    bool has_seq_max = false;
    int32_t n_gpu_layers = 0;
    bool has_n_gpu_layers = false;
    int32_t main_gpu = 0;
    bool has_main_gpu = false;
    int32_t flash_attention = 0;
    bool has_flash_attention = false;
    float rope_freq_base = 0.0f;
    bool has_rope_freq_base = false;
    float rope_freq_scale = 0.0f;
    bool has_rope_freq_scale = false;
    bool offload_kqv = false;
    bool has_offload_kqv = false;
    bool no_perf = true;
    bool has_no_perf = false;
    bool embeddings = false;
    bool has_embeddings = false;
    bool kv_unified = false;
    bool has_kv_unified = false;
    bool use_mmap = false;
    bool has_use_mmap = false;
    bool use_mlock = false;
    bool has_use_mlock = false;
};

struct SamplingNativeOptions {
    int32_t max_tokens = 0;
    std::optional<float> temperature;
    std::optional<float> top_p;
    std::optional<int32_t> top_k;
    std::optional<float> repeat_penalty;
    std::optional<int32_t> repeat_last_n;
    std::optional<float> frequency_penalty;
    std::optional<float> presence_penalty;
    std::vector<std::string> stop_sequences;
    std::optional<uint32_t> seed;
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

std::optional<int32_t> getOptionalInt(JNIEnv* env, jobject object, jmethodID method) {
    if (!env || !object || !method) {
        return std::nullopt;
    }

    jobject value_obj = env->CallObjectMethod(object, method);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        throw std::runtime_error("Gagal membaca nilai Integer dari konfigurasi runtime.");
    }
    if (!value_obj) {
        return std::nullopt;
    }

    jclass integer_class = env->FindClass("java/lang/Integer");
    if (!integer_class) {
        env->ExceptionClear();
        env->DeleteLocalRef(value_obj);
        throw std::runtime_error("java.lang.Integer tidak tersedia di lingkungan JNI.");
    }
    jmethodID int_value_method = env->GetMethodID(integer_class, "intValue", "()I");
    if (!int_value_method) {
        env->DeleteLocalRef(integer_class);
        env->DeleteLocalRef(value_obj);
        throw std::runtime_error("Metode intValue tidak ditemukan pada java.lang.Integer.");
    }
    const jint result = env->CallIntMethod(value_obj, int_value_method);
    env->DeleteLocalRef(integer_class);
    env->DeleteLocalRef(value_obj);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        throw std::runtime_error("Gagal mengambil nilai Integer dari konfigurasi runtime.");
    }
    return static_cast<int32_t>(result);
}

std::optional<float> getOptionalFloat(JNIEnv* env, jobject object, jmethodID method) {
    if (!env || !object || !method) {
        return std::nullopt;
    }

    jobject value_obj = env->CallObjectMethod(object, method);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        throw std::runtime_error("Gagal membaca nilai Float dari konfigurasi runtime.");
    }
    if (!value_obj) {
        return std::nullopt;
    }

    jclass float_class = env->FindClass("java/lang/Float");
    if (!float_class) {
        env->ExceptionClear();
        env->DeleteLocalRef(value_obj);
        throw std::runtime_error("java.lang.Float tidak tersedia di lingkungan JNI.");
    }
    jmethodID float_value_method = env->GetMethodID(float_class, "floatValue", "()F");
    if (!float_value_method) {
        env->DeleteLocalRef(float_class);
        env->DeleteLocalRef(value_obj);
        throw std::runtime_error("Metode floatValue tidak ditemukan pada java.lang.Float.");
    }
    const jfloat result = env->CallFloatMethod(value_obj, float_value_method);
    env->DeleteLocalRef(float_class);
    env->DeleteLocalRef(value_obj);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        throw std::runtime_error("Gagal mengambil nilai Float dari konfigurasi runtime.");
    }
    return static_cast<float>(result);
}

std::optional<bool> getOptionalBoolean(JNIEnv* env, jobject object, jmethodID method) {
    if (!env || !object || !method) {
        return std::nullopt;
    }

    jobject value_obj = env->CallObjectMethod(object, method);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        throw std::runtime_error("Gagal membaca nilai Boolean dari konfigurasi runtime.");
    }
    if (!value_obj) {
        return std::nullopt;
    }

    jclass boolean_class = env->FindClass("java/lang/Boolean");
    if (!boolean_class) {
        env->ExceptionClear();
        env->DeleteLocalRef(value_obj);
        throw std::runtime_error("java.lang.Boolean tidak tersedia di lingkungan JNI.");
    }
    jmethodID bool_value_method = env->GetMethodID(boolean_class, "booleanValue", "()Z");
    if (!bool_value_method) {
        env->DeleteLocalRef(boolean_class);
        env->DeleteLocalRef(value_obj);
        throw std::runtime_error("Metode booleanValue tidak ditemukan pada java.lang.Boolean.");
    }
    const jboolean result = env->CallBooleanMethod(value_obj, bool_value_method);
    env->DeleteLocalRef(boolean_class);
    env->DeleteLocalRef(value_obj);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        throw std::runtime_error("Gagal mengambil nilai Boolean dari konfigurasi runtime.");
    }
    return result == JNI_TRUE;
}

RuntimeNativeConfig parseRuntimeConfig(JNIEnv* env, jobject runtime_config) {
    if (!env) {
        throw std::runtime_error("Lingkungan JNI tidak tersedia untuk runtime config.");
    }
    if (!runtime_config) {
        throw std::runtime_error("RuntimeConfig tidak boleh null.");
    }

    jclass config_class = env->GetObjectClass(runtime_config);
    if (!config_class) {
        throw std::runtime_error("Tidak dapat mengambil kelas RuntimeConfig.");
    }

    jmethodID get_thread_count = env->GetMethodID(config_class, "getThreadCount", "()I");
    jmethodID get_context_size = env->GetMethodID(config_class, "getContextSize", "()I");
    jmethodID get_thread_batch = env->GetMethodID(config_class, "getThreadCountBatch", "()Ljava/lang/Integer;");
    jmethodID get_batch_size = env->GetMethodID(config_class, "getBatchSize", "()Ljava/lang/Integer;");
    jmethodID get_ubatch_size = env->GetMethodID(config_class, "getUbatchSize", "()Ljava/lang/Integer;");
    jmethodID get_seq_max = env->GetMethodID(config_class, "getSeqMax", "()Ljava/lang/Integer;");
    jmethodID get_n_gpu_layers = env->GetMethodID(config_class, "getNGpuLayers", "()Ljava/lang/Integer;");
    jmethodID get_main_gpu = env->GetMethodID(config_class, "getMainGpu", "()Ljava/lang/Integer;");
    jmethodID get_flash_attention = env->GetMethodID(config_class, "getFlashAttention", "()Ljava/lang/Integer;");
    jmethodID get_rope_freq_base = env->GetMethodID(config_class, "getRopeFreqBase", "()Ljava/lang/Float;");
    jmethodID get_rope_freq_scale = env->GetMethodID(config_class, "getRopeFreqScale", "()Ljava/lang/Float;");
    jmethodID get_offload_kqv = env->GetMethodID(config_class, "getOffloadKqv", "()Ljava/lang/Boolean;");
    jmethodID get_no_perf = env->GetMethodID(config_class, "getNoPerf", "()Ljava/lang/Boolean;");
    jmethodID get_embeddings = env->GetMethodID(config_class, "getEmbeddings", "()Ljava/lang/Boolean;");
    jmethodID get_kv_unified = env->GetMethodID(config_class, "getKvUnified", "()Ljava/lang/Boolean;");
    jmethodID get_use_mmap = env->GetMethodID(config_class, "getUseMmap", "()Ljava/lang/Boolean;");
    jmethodID get_use_mlock = env->GetMethodID(config_class, "getUseMlock", "()Ljava/lang/Boolean;");

    RuntimeNativeConfig config;
    config.thread_count = env->CallIntMethod(runtime_config, get_thread_count);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(config_class);
        throw std::runtime_error("Nilai threadCount tidak valid pada runtime config.");
    }
    config.context_size = env->CallIntMethod(runtime_config, get_context_size);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(config_class);
        throw std::runtime_error("Nilai contextSize tidak valid pada runtime config.");
    }

    if (auto value = getOptionalInt(env, runtime_config, get_thread_batch)) {
        if (*value > 0) {
            config.thread_count_batch = *value;
            config.has_thread_count_batch = true;
        }
    }
    if (auto value = getOptionalInt(env, runtime_config, get_batch_size)) {
        if (*value > 0) {
            config.batch_size = *value;
            config.has_batch_size = true;
        }
    }
    if (auto value = getOptionalInt(env, runtime_config, get_ubatch_size)) {
        if (*value > 0) {
            config.ubatch_size = *value;
            config.has_ubatch_size = true;
        }
    }
    if (auto value = getOptionalInt(env, runtime_config, get_seq_max)) {
        if (*value > 0) {
            config.seq_max = *value;
            config.has_seq_max = true;
        }
    }
    if (auto value = getOptionalInt(env, runtime_config, get_n_gpu_layers)) {
        if (*value >= 0) {
            config.n_gpu_layers = *value;
            config.has_n_gpu_layers = true;
        }
    }
    if (auto value = getOptionalInt(env, runtime_config, get_main_gpu)) {
        if (*value >= 0) {
            config.main_gpu = *value;
            config.has_main_gpu = true;
        }
    }
    if (auto value = getOptionalInt(env, runtime_config, get_flash_attention)) {
        if (*value < -1 || *value > 1) {
            env->DeleteLocalRef(config_class);
            throw std::runtime_error("Nilai flash_attn tidak valid (gunakan -1, 0, atau 1).");
        }
        config.flash_attention = *value;
        config.has_flash_attention = true;
    }
    if (auto value = getOptionalFloat(env, runtime_config, get_rope_freq_base)) {
        if (*value > 0.0f) {
            config.rope_freq_base = *value;
            config.has_rope_freq_base = true;
        }
    }
    if (auto value = getOptionalFloat(env, runtime_config, get_rope_freq_scale)) {
        if (*value > 0.0f) {
            config.rope_freq_scale = *value;
            config.has_rope_freq_scale = true;
        }
    }
    if (auto value = getOptionalBoolean(env, runtime_config, get_offload_kqv)) {
        config.offload_kqv = *value;
        config.has_offload_kqv = true;
    }
    if (auto value = getOptionalBoolean(env, runtime_config, get_no_perf)) {
        config.no_perf = *value;
        config.has_no_perf = true;
    }
    if (auto value = getOptionalBoolean(env, runtime_config, get_embeddings)) {
        config.embeddings = *value;
        config.has_embeddings = true;
    }
    if (auto value = getOptionalBoolean(env, runtime_config, get_kv_unified)) {
        config.kv_unified = *value;
        config.has_kv_unified = true;
    }
    if (auto value = getOptionalBoolean(env, runtime_config, get_use_mmap)) {
        config.use_mmap = *value;
        config.has_use_mmap = true;
    }
    if (auto value = getOptionalBoolean(env, runtime_config, get_use_mlock)) {
        config.use_mlock = *value;
        config.has_use_mlock = true;
    }

    env->DeleteLocalRef(config_class);

    if (config.thread_count <= 0 || config.context_size <= 0) {
        throw std::runtime_error("Parameter inisialisasi tidak valid.");
    }

    return config;
}

std::vector<std::string> extractStopSequences(JNIEnv* env, jobjectArray sequences) {
    std::vector<std::string> result;
    if (!env || !sequences) {
        return result;
    }

    const jsize length = env->GetArrayLength(sequences);
    result.reserve(static_cast<size_t>(length));
    for (jsize index = 0; index < length; ++index) {
        jstring element = static_cast<jstring>(env->GetObjectArrayElement(sequences, index));
        if (!element) {
            continue;
        }
        JniString text(env, element);
        if (text.get() && text.get()[0] != '\0') {
            result.emplace_back(text.get());
        }
        env->DeleteLocalRef(element);
    }
    return result;
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

RuntimeNativeConfig makeDefaultRuntimeConfig(int thread_count, int context_size) {
    RuntimeNativeConfig config;
    config.thread_count = thread_count;
    config.context_size = context_size;
    return config;
}

jlong createSession(JNIEnv* env,
                    const char* model_path,
                    const RuntimeNativeConfig& config) {
    if (!model_path) {
        throw std::runtime_error("Parameter inisialisasi tidak valid.");
    }
    if (config.thread_count <= 0 || config.context_size <= 0) {
        throw std::runtime_error("Parameter inisialisasi tidak valid.");
    }

    auto session = std::make_unique<LlamaSession>();
    session->model_path = model_path;
    session->thread_count = config.thread_count;
    session->thread_count_batch = config.has_thread_count_batch ? config.thread_count_batch
                                                                : config.thread_count;
    session->context_size = config.context_size;

    retainBackend();

    llama_model_params model_params = llama_model_default_params();
    if (config.has_n_gpu_layers) {
        model_params.n_gpu_layers = config.n_gpu_layers;
    }
    if (config.has_main_gpu) {
        model_params.main_gpu = config.main_gpu;
    }
    if (config.has_use_mmap) {
        model_params.use_mmap = config.use_mmap;
    }
    if (config.has_use_mlock) {
        model_params.use_mlock = config.use_mlock;
    }

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
    if (config.has_batch_size) {
        ctx_params.n_batch = std::max<int32_t>(1, config.batch_size);
    } else {
        ctx_params.n_batch = std::min(session->context_size, 512);
    }
    if (config.has_ubatch_size) {
        ctx_params.n_ubatch = std::max<int32_t>(1, config.ubatch_size);
    }
    if (config.has_seq_max) {
        ctx_params.n_seq_max = std::max<int32_t>(1, config.seq_max);
    }
    ctx_params.n_threads = session->thread_count;
    ctx_params.n_threads_batch = config.has_thread_count_batch ? config.thread_count_batch
                                                               : session->thread_count;
    if (config.has_flash_attention) {
        ctx_params.flash_attn_type = static_cast<llama_flash_attn_type>(config.flash_attention);
    }
    if (config.has_rope_freq_base) {
        ctx_params.rope_freq_base = config.rope_freq_base;
    }
    if (config.has_rope_freq_scale) {
        ctx_params.rope_freq_scale = config.rope_freq_scale;
    }
    if (config.has_offload_kqv) {
        ctx_params.offload_kqv = config.offload_kqv;
    }
    if (config.has_no_perf) {
        ctx_params.no_perf = config.no_perf;
    } else {
        ctx_params.no_perf = true;
    }
    if (config.has_embeddings) {
        ctx_params.embeddings = config.embeddings;
    }
    if (config.has_kv_unified) {
        ctx_params.kv_unified = config.kv_unified;
    }

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
}

std::string runCompletion(LlamaSession* session,
                          const std::string& prompt,
                          const SamplingNativeOptions& options,
                          const std::function<void(const std::string&)>& on_token) {
    if (!session || !session->model || !session->context) {
        throw std::runtime_error("Session belum siap digunakan.");
    }

    session->tokens_processed = 0;

    if (options.max_tokens <= 0) {
        return std::string();
    }

    const llama_vocab* vocab = llama_model_get_vocab(session->model);

    auto tokens = tokenizePrompt(session->model, prompt);
    if (tokens.empty()) {
        const llama_token bos = llama_vocab_bos(vocab);
        if (bos == LLAMA_TOKEN_NULL) {
            throw std::runtime_error("Model tidak memiliki token BOS.");
        }
        tokens.push_back(bos);
    }
    const int total_needed = static_cast<int>(tokens.size()) + options.max_tokens;
    if (total_needed > session->context_size) {
        std::ostringstream msg;
        msg << "Konteks terlalu kecil: membutuhkan " << total_needed
            << ", tetapi konteks saat ini " << session->context_size << '.';
        throw std::runtime_error(msg.str());
    }

    llama_set_n_threads(session->context, session->thread_count, session->thread_count_batch);
    // llama_kv_cache_clear(session->context) is not available in the pinned llama.cpp revision.

    auto evaluate_tokens = [&](const llama_token* data, int32_t count) {
        if (count <= 0) {
            return;
        }

        const int32_t max_batch = std::max<int32_t>(1, static_cast<int32_t>(llama_n_batch(session->context)));
        int32_t processed = 0;
        while (processed < count) {
            const int32_t chunk = std::min<int32_t>(max_batch, count - processed);
            llama_batch batch = llama_batch_get_one(const_cast<llama_token*>(data + processed), chunk);

            if (batch.pos) {
                for (int32_t i = 0; i < batch.n_tokens; ++i) {
                    batch.pos[i] = session->tokens_processed + processed + i;
                }
            }
            if (batch.seq_id && batch.n_tokens > 0) {
                for (int32_t i = 0; i < batch.n_tokens; ++i) {
                    batch.n_seq_id[i] = 1;
                    batch.seq_id[i][0] = 0;
                }
            }
            if (batch.logits && batch.n_tokens > 0) {
                for (int32_t i = 0; i < batch.n_tokens; ++i) {
                    batch.logits[i] = (i == batch.n_tokens - 1) ? 1 : 0;
                }
            }

            const int32_t status = llama_decode(session->context, batch);
            // Batches created via llama_batch_get_one do not own their buffers,
            // so they must not be released with llama_batch_free.
            if (status != 0) {
                std::ostringstream msg;
                msg << "Gagal memproses token (status=" << status << ")";
                throw std::runtime_error(msg.str());
            }
            processed += chunk;
            session->tokens_processed += chunk;
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

    auto add_sampler_to_chain = [&](llama_sampler* sampler_to_add, const char* name) {
        if (!sampler_to_add) {
            std::ostringstream msg;
            msg << "Tidak dapat membuat sampler " << name << '.';
            throw std::runtime_error(msg.str());
        }
        using ChainAddReturn = decltype(llama_sampler_chain_add(sampler, sampler_to_add));
        if constexpr (std::is_same_v<ChainAddReturn, bool>) {
            const bool added = llama_sampler_chain_add(sampler, sampler_to_add);
            if (!added) {
                llama_sampler_free(sampler_to_add);
                std::ostringstream msg;
                msg << "Tidak dapat menambahkan sampler " << name << " ke rantai.";
                throw std::runtime_error(msg.str());
            }
        } else {
            llama_sampler_chain_add(sampler, sampler_to_add);
        }
    };

    const float repeat_penalty_value = options.repeat_penalty.value_or(1.0f);
    const float frequency_penalty_value = options.frequency_penalty.value_or(0.0f);
    const float presence_penalty_value = options.presence_penalty.value_or(0.0f);
    const bool use_repeat_penalty = options.repeat_penalty.has_value() && repeat_penalty_value > 1.0f + 1e-5f;
    const bool use_frequency_penalty = options.frequency_penalty.has_value() && std::fabs(frequency_penalty_value) > 1e-5f;
    const bool use_presence_penalty = options.presence_penalty.has_value() && std::fabs(presence_penalty_value) > 1e-5f;
    if (use_repeat_penalty || use_frequency_penalty || use_presence_penalty) {
        const int32_t repeat_last_n = options.repeat_last_n.value_or(
                std::min(session->context_size, 64));
        llama_sampler* penalties = llama_sampler_init_penalties(
                repeat_last_n,
                use_repeat_penalty ? repeat_penalty_value : 1.0f,
                use_frequency_penalty ? frequency_penalty_value : 0.0f,
                use_presence_penalty ? presence_penalty_value : 0.0f);
        add_sampler_to_chain(penalties, "penalties");
    }

    if (options.top_k.has_value()) {
        llama_sampler* top_k = llama_sampler_init_top_k(options.top_k.value());
        add_sampler_to_chain(top_k, "top_k");
    }

    if (options.top_p.has_value()) {
        llama_sampler* top_p = llama_sampler_init_top_p(options.top_p.value(), 1);
        add_sampler_to_chain(top_p, "top_p");
    }

    if (options.temperature.has_value()) {
        llama_sampler* temperature = llama_sampler_init_temp(options.temperature.value());
        add_sampler_to_chain(temperature, "temperature");
    }

    const uint32_t sampler_seed = options.seed.value_or(LLAMA_DEFAULT_SEED);
    llama_sampler* dist = llama_sampler_init_dist(sampler_seed);
    add_sampler_to_chain(dist, "dist");

    for (llama_token token : tokens) {
        llama_sampler_accept(sampler, token);
    }

    std::string completion;
    completion.reserve(static_cast<size_t>(options.max_tokens) * 4);

    for (int generated = 0; generated < options.max_tokens; ++generated) {
        const llama_token next = llama_sampler_sample(sampler, session->context, -1);

        if (llama_vocab_is_eog(vocab, next)) {
            break;
        }

        const std::string token_text = tokenToString(vocab, next);
        std::string candidate = completion;
        candidate += token_text;

        bool reached_stop = false;
        for (const auto& stop : options.stop_sequences) {
            if (!stop.empty() && candidate.size() >= stop.size()) {
                const size_t offset = candidate.size() - stop.size();
                if (candidate.compare(offset, stop.size(), stop) == 0) {
                    candidate.erase(offset);
                    reached_stop = true;
                    break;
                }
            }
        }

        if (reached_stop) {
            completion = std::move(candidate);
            break;
        }

        llama_sampler_accept(sampler, next);
        completion = std::move(candidate);

        if (on_token) {
            on_token(token_text);
        }

        evaluate_tokens(&next, 1);
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
        if (!path.get()) {
            throw std::runtime_error("Parameter inisialisasi tidak valid.");
        }

        RuntimeNativeConfig config = makeDefaultRuntimeConfig(threadCount, contextSize);
        return createSession(env, path.get(), config);
    } catch (const std::exception& ex) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "nativeInit gagal: %s", ex.what());
        throwJavaException(env, "java/lang/IllegalStateException", ex.what());
        return 0;
    }
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_cicero_ciceroai_llama_LlamaBridge_nativeInitWithConfig(
        JNIEnv* env,
        jobject /* thiz */,
        jstring modelPath,
        jobject runtimeConfig) {
    try {
        JniString path(env, modelPath);
        if (!path.get()) {
            throw std::runtime_error("Parameter inisialisasi tidak valid.");
        }

        RuntimeNativeConfig config = parseRuntimeConfig(env, runtimeConfig);
        return createSession(env, path.get(), config);
    } catch (const std::exception& ex) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "nativeInitWithConfig gagal: %s", ex.what());
        throwJavaException(env, "java/lang/IllegalStateException", ex.what());
        return 0;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cicero_ciceroai_llama_LlamaBridge_nativeCompletionWithOptions(
        JNIEnv* env,
        jobject /* thiz */,
        jlong handle,
        jstring prompt,
        jint maxTokens,
        jfloat temperature,
        jfloat topP,
        jint topK,
        jfloat repeatPenalty,
        jint repeatLastN,
        jfloat frequencyPenalty,
        jfloat presencePenalty,
        jobjectArray stopSequences,
        jint seed,
        jobject listener) {
    auto* session = fromHandle(handle);
    try {
        if (!session) {
            throw std::runtime_error("Session tidak ditemukan.");
        }

        JniString prompt_utf(env, prompt);
        const std::string prompt_str = prompt_utf.get() ? prompt_utf.get() : "";

        SamplingNativeOptions options;
        options.max_tokens = std::max(0, static_cast<int>(maxTokens));
        if (std::isfinite(temperature) && temperature > 0.0f) {
            options.temperature = temperature;
        }
        if (std::isfinite(topP) && topP > 0.0f && topP <= 1.0f) {
            options.top_p = topP;
        }
        if (topK > 0) {
            options.top_k = topK;
        }
        if (std::isfinite(repeatPenalty) && repeatPenalty > 0.0f) {
            options.repeat_penalty = repeatPenalty;
        }
        if (repeatLastN >= 0) {
            options.repeat_last_n = repeatLastN;
        }
        if (std::isfinite(frequencyPenalty)) {
            options.frequency_penalty = frequencyPenalty;
        }
        if (std::isfinite(presencePenalty)) {
            options.presence_penalty = presencePenalty;
        }
        if (seed >= 0) {
            options.seed = static_cast<uint32_t>(seed);
        }
        options.stop_sequences = extractStopSequences(env, stopSequences);

        std::function<void(const std::string&)> progress_callback;
        if (listener) {
            jclass listener_class = env->GetObjectClass(listener);
            if (!listener_class) {
                throw std::runtime_error("Listener progres tidak valid.");
            }

            jmethodID on_token_method = env->GetMethodID(
                    listener_class, "onTokenGenerated", "(Ljava/lang/String;)V");
            env->DeleteLocalRef(listener_class);
            if (!on_token_method) {
                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                }
                throw std::runtime_error(
                        "Metode onTokenGenerated tidak ditemukan pada listener progres.");
            }

            progress_callback = [env, listener, on_token_method](const std::string& token_text) {
                jstring token_string = env->NewStringUTF(token_text.c_str());
                if (!token_string) {
                    throw std::runtime_error(
                            "Gagal membuat representasi string untuk token yang dihasilkan.");
                }

                env->CallVoidMethod(listener, on_token_method, token_string);
                env->DeleteLocalRef(token_string);

                if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                    throw std::runtime_error("Listener progres melempar pengecualian.");
                }
            };
        }

        const std::string completion =
                runCompletion(session, prompt_str, options, progress_callback);
        return env->NewStringUTF(completion.c_str());
    } catch (const std::exception& ex) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "nativeCompletionWithOptions gagal: %s", ex.what());
        throwJavaException(env, "java/lang/IllegalStateException", ex.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_cicero_ciceroai_llama_LlamaBridge_nativeCompletion(
        JNIEnv* env,
        jobject thiz,
        jlong handle,
        jstring prompt,
        jint maxTokens,
        jobject listener) {
    const jfloat nan = std::numeric_limits<float>::quiet_NaN();
    return Java_com_cicero_ciceroai_llama_LlamaBridge_nativeCompletionWithOptions(
            env,
            thiz,
            handle,
            prompt,
            maxTokens,
            nan,
            nan,
            -1,
            nan,
            -1,
            nan,
            nan,
            nullptr,
            -1,
            listener);
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
