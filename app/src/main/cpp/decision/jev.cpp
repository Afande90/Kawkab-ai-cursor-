// JNI only; the app and on-phone check use the same verdict renderer.
#include <jni.h>
#include "jev.hpp"
#include <stdexcept>

namespace {
std::string text(JNIEnv *env, jstring value) {
    if (!value) throw std::runtime_error("Missing decision input");
    const auto *raw = env->GetStringChars(value, nullptr);
    if (!raw) throw std::runtime_error("Unable to read decision input");
    const int length = env->GetStringLength(value);
    std::string result;
    for (int i = 0; i < length; ++i) {
        unsigned cp = raw[i];
        if (cp >= 0xd800 && cp <= 0xdbff && i + 1 < length && raw[i + 1] >= 0xdc00 && raw[i + 1] <= 0xdfff)
            cp = 0x10000 + ((cp - 0xd800) << 10) + (raw[++i] - 0xdc00);
        else if (cp >= 0xd800 && cp <= 0xdfff) cp = 0xfffd;
        if (cp < 0x80) result += static_cast<char>(cp);
        else if (cp < 0x800) {
            result += static_cast<char>(0xc0 | (cp >> 6)); result += static_cast<char>(0x80 | (cp & 63));
        } else if (cp < 0x10000) {
            result += static_cast<char>(0xe0 | (cp >> 12)); result += static_cast<char>(0x80 | ((cp >> 6) & 63));
            result += static_cast<char>(0x80 | (cp & 63));
        } else {
            result += static_cast<char>(0xf0 | (cp >> 18)); result += static_cast<char>(0x80 | ((cp >> 12) & 63));
            result += static_cast<char>(0x80 | ((cp >> 6) & 63)); result += static_cast<char>(0x80 | (cp & 63));
        }
    }
    env->ReleaseStringChars(value, raw);
    return result;
}
}
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_app_kawkab_android_voice_JevRuntime_scores(JNIEnv *env, jobject, jstring path, jstring state,
                                               jstring question, jobjectArray options) {
    try {
        std::vector<std::string> names;
        const int count = env->GetArrayLength(options);
        for (int i = 0; i < count; ++i) {
            auto value = static_cast<jstring>(env->GetObjectArrayElement(options, i));
            names.push_back(text(env, value));
            env->DeleteLocalRef(value);
        }
        auto scores = kawkab::verdicts(text(env, path), text(env, state), text(env, question), names);
        auto out = env->NewDoubleArray(count);
        if (out) env->SetDoubleArrayRegion(out, 0, count, scores.data());
        return out;
    } catch (const std::exception &error) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), error.what());
        return nullptr;
    }
}
extern "C" JNIEXPORT void JNICALL
Java_app_kawkab_android_voice_JevRuntime_release(JNIEnv *, jobject) { kawkab::release_verdicts(); }
