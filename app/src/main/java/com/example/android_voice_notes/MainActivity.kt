package com.example.android_voice_notes

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.android_voice_notes.transcription.TranscriptionManager
import java.io.File
import java.net.Socket

class MainActivity : AppCompatActivity() {
    private lateinit var recordButton: Button
    private lateinit var sendButton: Button
    private lateinit var statusText: TextView
    private lateinit var transcriptionText: TextView
    private lateinit var ipEdit: EditText
    
    private lateinit var transcriptionManager: TranscriptionManager
    private var voiceRecorder: VoiceRecorder? = null
    private var isRecording = false
    private var currentFile: File? = null

    companion object {
        private const val REQUEST_RECORD_AUDIO = 200
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recordButton = findViewById(R.id.record_button)
        sendButton = findViewById(R.id.send_button)
        statusText = findViewById(R.id.status_text)
        transcriptionText = findViewById(R.id.transcription_text)
        ipEdit = findViewById(R.id.companion_ip_edit)

        transcriptionManager = TranscriptionManager(this)
        transcriptionManager.initialize()

        recordButton.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                if (checkPermissions()) {
                    startRecording()
                } else {
                    requestPermissions()
                }
            }
        }

        sendButton.setOnClickListener {
            sendToCompanion()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
    }

    private fun startRecording() {
        val storageDir = getExternalFilesDir(null)
        if (storageDir == null) {
            Toast.makeText(this, "Storage not available", Toast.LENGTH_SHORT).show()
            return
        }

        currentFile = File(storageDir, "recording_${System.currentTimeMillis()}.wav")
        voiceRecorder = VoiceRecorder(currentFile!!)
        voiceRecorder?.startRecording()
        
        isRecording = true
        recordButton.text = "Спри запис"
        statusText.text = "Записва се..."
        transcriptionText.text = ""
    }

    private fun stopRecording() {
        voiceRecorder?.stopRecording()
        isRecording = false
        recordButton.text = "Започни запис"
        statusText.text = "Обработка..."
        
        // Transcribe in background thread
        Thread {
            val transcription = transcriptionManager.transcribe(currentFile!!)
            runOnUiThread {
                transcriptionText.text = transcription
                statusText.text = "Записът е готов."
            }
        }.start()
    }

    private fun sendToCompanion() {
        val ip = ipEdit.text.toString()
        if (ip.isEmpty()) {
            Toast.makeText(this, "Въведете IP адрес", Toast.LENGTH_SHORT).show()
            return
        }

        val text = transcriptionText.text.toString()
        if (text.isEmpty() || text.startsWith("Транскрипцията")) {
            Toast.makeText(this, "Няма текст за изпращане", Toast.LENGTH_SHORT).show()
            return
        }

        Thread {
            try {
                val socket = Socket(ip, 5000)
                val outputStream = socket.getOutputStream()
                outputStream.write(text.toByteArray())
                outputStream.close()
                socket.close()
                runOnUiThread {
                    Toast.makeText(this, "Бележката е изпратена!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending note", e)
                runOnUiThread {
                    Toast.makeText(this, "Грешка при изпращане: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        transcriptionManager.cleanup()
    }
}
