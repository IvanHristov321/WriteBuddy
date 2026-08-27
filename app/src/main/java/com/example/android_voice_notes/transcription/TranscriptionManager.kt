package com.example.android_voice_notes.transcription

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Manager for handling speech-to-text transcription.
 */
class TranscriptionManager(private val context: Context) {

    companion object {
        private const val TAG = "TranscriptionManager"
        private const val DEFAULT_MODEL = "ggml-base.bg.bin"
        private val MODELS = arrayOf("ggml-large.bin", "ggml-small.bin", "ggml-base.bg.bin")
    }

    private var nativeContext: Long = 0L
    private var loadedModelName: String? = null

    init {
        // Load the native library
        try {
            System.loadLibrary("whisper_transcription")
            Log.d(TAG, "Native library loaded successfully")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to load native library", e)
        }
    }

    /**
     * Transcribes the given audio file to text in Bulgarian.
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
        } catch (e: Throwable) {
            Log.e(TAG, "Transcription error", e)
            "Грешка при разпознаване: ${e.message}"
        }
    }

    /**
     * Initialize the transcription engine by loading the best available model.
     */
    fun initialize(): Boolean {
        Log.d(TAG, "Initializing transcription engine...")
        val modelFile = getBestModelFile()
        
        if (modelFile == null || !modelFile.exists()) {
            Log.e(TAG, "No valid model file found")
            return false
        }
        
        Log.d(TAG, "Loading model: ${modelFile.name} (${modelFile.length() / 1024 / 1024} MB)")
        
        return try {
            nativeContext = nativeInitModel(modelFile.absolutePath)
            if (nativeContext != 0L) {
                loadedModelName = modelFile.name
                Log.d(TAG, "Model $loadedModelName initialized successfully")
                true
            } else {
                Log.e(TAG, "Native model initialization failed")
                false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Exception during model init", e)
            false
        }
    }

    /**
     * Clean up resources when the app is destroyed.
     */
    fun cleanup() {
        if (nativeContext != 0L) {
            Log.d(TAG, "Cleaning up native context...")
            nativeFreeModel(nativeContext)
            nativeContext = 0L
            loadedModelName = null
        }
    }

    private fun getBestModelFile(): File? {
        // 1. Check storage for existing large models
        for (modelName in MODELS) {
            val file = File(context.filesDir, modelName)
            if (file.exists() && file.length() > 10 * 1024 * 1024) { // > 10MB
                return file
            }
        }

        // 2. Search assets for the best model
        val assetList = context.assets.list("") ?: emptyArray()
        for (modelName in MODELS) {
            if (assetList.contains(modelName)) {
                val destFile = File(context.filesDir, modelName)
                Log.d(TAG, "Found $modelName in assets. Preparing...")
                
                try {
                    context.assets.open(modelName).use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (destFile.exists()) return destFile
                } catch (e: Exception) {
                    Log.e(TAG, "Error copying $modelName", e)
                }
            }
        }
        
        // 3. Fallback to default check
        val fallback = File(context.filesDir, DEFAULT_MODEL)
        return if (fallback.exists()) fallback else null
    }

    // Native methods
    private external fun nativeInitModel(modelPath: String): Long
    private external fun nativeTranscribe(contextPtr: Long, audioFilePath: String): String
    private external fun nativeFreeModel(contextPtr: Long)
}
