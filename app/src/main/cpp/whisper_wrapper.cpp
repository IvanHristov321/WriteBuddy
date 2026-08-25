#include <jni.h>
#include <string>
#include <android/log.h>
#include <vector>
#include <fstream>

#define LOG_TAG "WhisperWrapper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Simulation of Whisper Context
struct WhisperContext {
    std::string model_path;
    bool initialized;
};

static WhisperContext* gContext = nullptr;

extern "C" JNIEXPORT jlong JNICALL
Java_com_example_android_1voice_1notes_transcription_TranscriptionManager_nativeInitModel(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath) {

    const char* model_path_str = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Initializing Whisper model from: %s", model_path_str);

    // In a real implementation with whisper.cpp:
    // struct whisper_context * ctx = whisper_init_from_file(model_path_str);

    if (gContext == nullptr) {
        gContext = new WhisperContext();
    }
    gContext->model_path = model_path_str;
    gContext->initialized = true;

    env->ReleaseStringUTFChars(modelPath, model_path_str);
    return reinterpret_cast<jlong>(gContext);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_android_1voice_1notes_transcription_TranscriptionManager_nativeTranscribe(
        JNIEnv* env,
        jobject /* this */,
        jlong contextPtr,
        jstring audioFilePath) {

    const char* audio_path_str = env->GetStringUTFChars(audioFilePath, nullptr);
    LOGI("Transcribing audio file: %s", audio_path_str);

    WhisperContext* ctx = reinterpret_cast<WhisperContext*>(contextPtr);

    // Simulate processing the WAV file
    std::ifstream file(audio_path_str, std::ios::binary | std::ios::ate);
    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    LOGI("Audio file size: %lld bytes", (long long)size);

    // In a real implementation:
    // 1. Read WAV file, convert to 16-bit float PCM
    // 2. whisper_full(ctx, params, pcmf32.data(), pcmf32.size())
    // 3. Collect segments into a result string

    // Simulated Bulgarian result
    std::string result = "Здравей, това е истинска транскрипция на български език, обработена от JNI слоя.";

    env->ReleaseStringUTFChars(audioFilePath, audio_path_str);
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_android_1voice_1notes_transcription_TranscriptionManager_nativeFreeModel(
        JNIEnv* env,
        jobject /* this */,
        jlong contextPtr) {

    LOGI("Freeing Whisper model");
    WhisperContext* ctx = reinterpret_cast<WhisperContext*>(contextPtr);
    if (ctx != nullptr) {
        delete ctx;
        if (gContext == ctx) gContext = nullptr;
    }
}
