// Kawkab verdict reader. Links the exact llama runtime shipped by Llamatik 2.0.0.
// Apache-2.0; readout protocol follows the pinned Jev model's published format.
#include <jni.h>
#include <llama.h>
#include <ggml-backend.h>
#include <algorithm>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>

namespace {
std::mutex mutex;
using Model = std::unique_ptr<llama_model, decltype(&llama_model_free)>;
Model model(nullptr, llama_model_free);
std::string model_path;
std::string text(JNIEnv *env, jstring value) {
    if (!value) throw std::runtime_error("Missing decision input");
    const char *raw = env->GetStringUTFChars(value, nullptr);
    if (!raw) throw std::runtime_error("Unable to read decision input");
    std::string result(raw);
    env->ReleaseStringUTFChars(value, raw);
    return result;
}
std::vector<llama_token> encode(const llama_vocab *vocab, const std::string &s) {
    const int count = llama_tokenize(vocab, s.data(), s.size(), nullptr, 0, false, false);
    if (count >= 0) return {};
    std::vector<llama_token> out(-count);
    if (llama_tokenize(vocab, s.data(), s.size(), out.data(), out.size(), false, false) != -count)
        throw std::runtime_error("Tokenizer mismatch");
    return out;
}
}
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_app_kawkab_android_voice_JevRuntime_scores(JNIEnv *env, jobject, jstring path, jstring state,
                                               jstring question, jobjectArray options) {
    std::lock_guard<std::mutex> guard(mutex);
    try {
        const auto requested = text(env, path);
        if (!model || model_path != requested) {
            model.reset();
            ggml_backend_load_all();
            llama_backend_init();
            auto params = llama_model_default_params();
            params.n_gpu_layers = 0;
            model.reset(llama_model_load_from_file(requested.c_str(), params));
            if (!model) throw std::runtime_error("Decision model could not be loaded");
            model_path = requested;
        }
        const auto *vocab = llama_model_get_vocab(model.get());
        if (encode(vocab, " yes") != std::vector<llama_token>{9542} ||
            encode(vocab, " no") != std::vector<llama_token>{874} ||
            encode(vocab, " ->") != std::vector<llama_token>{1411})
            throw std::runtime_error("Decision model vocabulary does not match the pinned readout");
        std::vector<llama_token> ids;
        auto append = [&](const std::string &s) {
            auto part = encode(vocab, s);
            ids.insert(ids.end(), part.begin(), part.end());
        };
        append("State:\n"); append(text(env, state)); append("\n\n");
        const auto prefix = ids.size();
        append("Question [choice]: " + text(env, question) + "\nOptions:\n");
        const int count = env->GetArrayLength(options);
        if (count < 2 || count > 20) throw std::runtime_error("A typed choice needs 2 to 20 options");
        std::vector<std::string> names;
        for (int i = 0; i < count; ++i) {
            auto value = static_cast<jstring>(env->GetObjectArrayElement(options, i));
            names.push_back(text(env, value));
            env->DeleteLocalRef(value);
            append("- "); append(names.back()); append("\n");
        }
        append("Judge each option:\n");
        std::vector<int> slots;
        for (const auto &name : names) {
            append(name); ids.push_back(1411); slots.push_back(ids.size() - 1); append("\n");
        }
        if (ids.size() - prefix > 2048 || ids.size() > 4096)
            throw std::runtime_error("Decision exceeds the phone context budget; nothing was truncated");
        auto params = llama_context_default_params();
        params.n_ctx = 4096; params.n_batch = 4096; params.n_ubatch = 512;
        params.n_threads = 2; params.n_threads_batch = 2;
        std::unique_ptr<llama_context, decltype(&llama_free)> ctx(
            llama_init_from_model(model.get(), params), llama_free);
        if (!ctx) throw std::runtime_error("Decision context unavailable");
        auto batch = llama_batch_init(ids.size(), 0, 1);
        batch.n_tokens = ids.size();
        for (int i = 0; i < batch.n_tokens; ++i) {
            batch.token[i] = ids[i]; batch.pos[i] = i; batch.n_seq_id[i] = 1;
            batch.seq_id[i][0] = 0;
            batch.logits[i] = std::find(slots.begin(), slots.end(), i) != slots.end();
        }
        const int status = llama_decode(ctx.get(), batch);
        llama_batch_free(batch);
        if (status != 0) throw std::runtime_error("Decision forward pass failed");
        std::vector<jdouble> scores;
        for (int slot : slots) {
            const auto *logits = llama_get_logits_ith(ctx.get(), slot);
            if (!logits) throw std::runtime_error("Missing verdict logits");
            scores.push_back(static_cast<double>(logits[9542]) - logits[874]);
        }
        auto out = env->NewDoubleArray(count);
        if (out) env->SetDoubleArrayRegion(out, 0, count, scores.data());
        return out;
    } catch (const std::exception &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}
extern "C" JNIEXPORT void JNICALL
Java_app_kawkab_android_voice_JevRuntime_release(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> guard(mutex);
    model.reset(); model_path.clear();
}
