package com.example.android_voice_notes.transcription

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manager for handling speech-to-text transcription.
 * 
 * Uses JNI to call native code (whisper.cpp) for Bulgarian language transcription.
 * Place the Bulgarian model file (ggml-base.bg.bin) in app/src/main/assets/
 */
class TranscriptionManager(private val context: Context) {

    companion object {
        private const val TAG = "TranscriptionManager"
        private const val MODEL_NAME = "ggml-base.bg.bin"  // Bulgarian Whisper model
    }

    private var nativeContext: Long = 0L

    init {
        // Load the native library
        try {
            System.loadLibrary("whisper_transcription")
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    /**
     * Transcribes the given audio file to text in Bulgarian.
     * 
     * @param audioFile The audio file to transcribe (expected to be in WAV format)
     * @return The transcribed text
     */
    fun transcribe(audioFile: File): String {
        Log.d(TAG, "Starting transcription of file: ${audioFile.absolutePath}")
        
        if (!audioFile.exists()) {
            Log.e(TAG, "Audio file does not exist")
            return "Грешка: аудио файлът не съществува"
        }
        
        if (nativeContext == 0L) {
            Log.e(TAG, "Native context not initialized")
            return "Грешка: моделът не е зареден"
        }
        
        return try {
            nativeTranscribe(nativeContext, audioFile.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Transcription error", e)
            "Грешка при разпознаване: ${e.message}"
        }
    }

    /**
     * Initialize the transcription engine by loading the Bulgarian model.
     * This should be called once when the app starts (e.g., in Application.onCreate).
     */
    fun initialize() {
        Log.d(TAG, "Initializing transcription engine...")
        val modelFile = getModelFile()
        
        if (!modelFile.exists()) {
            Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
            Log.e(TAG, "Please download the Bulgarian Whisper model and place it in assets/")
            return
        }
        
        try {
            nativeContext = nativeInitModel(modelFile.absolutePath)
            Log.d(TAG, "Model initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize model", e)
        }
    }

    /**
     * Clean up resources when the app is destroyed.
     */
    fun cleanup() {
        if (nativeContext != 0L) {
            nativeFreeModel(nativeContext)
            nativeContext = 0L
        }
    }

    private fun getModelFile(): File {
        val modelFile = File(context.filesDir, MODEL_NAME)
        
        // If not in internal storage, copy from assets
        if (!modelFile.exists()) {
            try {
                context.assets.open(MODEL_NAME).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied model from assets to ${modelFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Model not found in assets", e)
            }
        }
        
        return modelFile
    }

    // Native methods (implemented in C++ via JNI)
    private external fun nativeInitModel(modelPath: String): Long
    private external fun nativeTranscribe(contextPtr: Long, audioFilePath: String): String
    private external fun nativeFreeModel(contextPtr: Long)
}
