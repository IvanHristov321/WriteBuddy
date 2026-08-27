package com.example.android_voice_notes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.android_voice_notes.databinding.ActivityMainBinding
import com.example.android_voice_notes.transcription.TranscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Socket

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    
    private lateinit var transcriptionManager: TranscriptionManager
    private var voiceRecorder: VoiceRecorder? = null
    private var isRecording = false
    private var isInitializing = true
    private var currentFile: File? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(this, "Необходим е достъп до микрофона", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transcriptionManager = TranscriptionManager(this)
        
        // Initialize in background
        lifecycleScope.launch(Dispatchers.IO) {
            val success = transcriptionManager.initialize()
            withContext(Dispatchers.Main) {
                isInitializing = false
                if (success) {
                    binding.statusText.text = "Готов за запис"
                    binding.recordButton.isEnabled = true
                } else {
                    binding.statusText.text = "Грешка при инициализиране"
                    Toast.makeText(this@MainActivity, "Грешка: моделът или библиотеката не могат да бъдат заредени", Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.recordButton.isEnabled = false
        binding.statusText.text = "Инициализиране на модела..."

        binding.recordButton.setOnClickListener {
            if (isInitializing) return@setOnClickListener
            
            if (isRecording) {
                stopRecording()
            } else {
                checkAndStartRecording()
            }
        }

        binding.sendButton.setOnClickListener {
            sendToCompanion()
        }
    }

    private fun checkAndStartRecording() {
        when {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                startRecording()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startRecording() {
        Log.d(TAG, "startRecording() called")
        val storageDir = getExternalFilesDir(null)
        if (storageDir == null) {
            Log.e(TAG, "Storage not available")
            Toast.makeText(this, "Storage not available", Toast.LENGTH_SHORT).show()
            return
        }

        currentFile = File(storageDir, "recording_${System.currentTimeMillis()}.wav")
        Log.d(TAG, "Creating new recording file: ${currentFile?.absolutePath}")
        voiceRecorder = VoiceRecorder(currentFile!!)
        
        if (voiceRecorder?.startRecording() == true) {
            Log.d(TAG, "VoiceRecorder started successfully")
            isRecording = true
            updateUIForRecording(true)
        } else {
            Log.e(TAG, "VoiceRecorder failed to start")
            Toast.makeText(this, "Грешка при започване на записа", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        Log.d(TAG, "stopRecording() called. currentFile: ${currentFile?.absolutePath}")
        voiceRecorder?.stopRecording()
        isRecording = false
        updateUIForRecording(false)
        
        binding.statusText.text = "Обработка и транскрипция..."
        binding.transcriptionText.text = "Моля изчакайте, транскрибиране...\n(Файл: ${currentFile?.name})"
        
        val fileToTranscribe = currentFile
        if (fileToTranscribe == null || !fileToTranscribe.exists() || fileToTranscribe.length() == 0L) {
            Log.e(TAG, "File to transcribe is invalid or empty!")
            binding.transcriptionText.text = "Грешка: Невалиден аудио файл."
            return
        }

        Log.d(TAG, "Launching transcription coroutine for file size: ${fileToTranscribe.length()}")
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "IO Thread: Calling transcriptionManager.transcribe")
                val transcription = transcriptionManager.transcribe(fileToTranscribe)
                Log.d(TAG, "IO Thread: Transcription finished. Length: ${transcription.length}")
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "Main Thread: Updating UI with transcription")
                    binding.transcriptionText.text = transcription
                    binding.statusText.text = "Записът е готов."
                    Toast.makeText(this@MainActivity, "Транскрипцията е готова", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Coroutine error during transcription", e)
                withContext(Dispatchers.Main) {
                    binding.transcriptionText.text = "Грешка при обработка: ${e.message}"
                }
            }
        }
    }

    private fun updateUIForRecording(recording: Boolean) {
        Log.d(TAG, "updateUIForRecording($recording)")
        if (recording) {
            binding.recordButton.text = "Спри запис"
            binding.recordButton.setIconResource(android.R.drawable.ic_media_pause)
            binding.statusText.text = "Записва се..."
            binding.transcriptionText.text = ""
        } else {
            binding.recordButton.text = "Започни запис"
            binding.recordButton.setIconResource(android.R.drawable.ic_btn_speak_now)
        }
    }

    private fun sendToCompanion() {
        val ip = binding.companionIpEdit.text.toString()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Въведете IP адрес", Toast.LENGTH_SHORT).show()
            return
        }

        val text = binding.transcriptionText.text.toString()
        if (text.isEmpty() || text.startsWith("Вашата транскрипция")) {
            Toast.makeText(this, "Няма текст за изпращане", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val socket = Socket(ip, 5000)
                socket.soTimeout = 5000
                val outputStream = socket.getOutputStream()
                outputStream.write(text.toByteArray())
                outputStream.close()
                socket.close()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Бележката е изпратена!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending note", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Грешка при изпращане: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        transcriptionManager.cleanup()
    }
}
