package com.example.android_voice_notes

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/**
 * Records audio in 16kHz Mono 16-bit PCM WAV format, required by Whisper.cpp.
 */
class VoiceRecorder(private val outputFile: File) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingThread: Thread? = null

    companion object {
        private const val TAG = "VoiceRecorder"
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private val BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
    }

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (isRecording) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            BUFFER_SIZE
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed")
            return
        }

        audioRecord?.startRecording()
        isRecording = true

        recordingThread = Thread({
            writeAudioDataToFile()
        }, "AudioRecordingThread")
        recordingThread?.start()
        Log.d(TAG, "Recording started: ${outputFile.absolutePath}")
    }

    private fun writeAudioDataToFile() {
        val data = ByteArray(BUFFER_SIZE)
        val os = FileOutputStream(outputFile)
        
        // Write placeholder WAV header
        writeWavHeader(os, CHANNEL_CONFIG, SAMPLE_RATE, AUDIO_FORMAT)

        while (isRecording) {
            val read = audioRecord?.read(data, 0, BUFFER_SIZE) ?: 0
            if (read > 0) {
                os.write(data, 0, read)
            }
        }

        os.close()
        updateWavHeader(outputFile)
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        recordingThread?.join()
        Log.d(TAG, "Recording stopped")
    }

    private fun writeWavHeader(os: FileOutputStream, channelConfig: Int, sampleRate: Int, audioFormat: Int) {
        val channels = if (channelConfig == AudioFormat.CHANNEL_IN_MONO) 1 else 2
        val bitsPerSample = if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8

        val header = ByteArray(44)
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        // ChunkSize (4-7) will be updated later
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16 // Subchunk1Size
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // AudioFormat (PCM = 1)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = blockAlign.toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        // Subchunk2Size (40-43) will be updated later

        os.write(header, 0, 44)
    }

    private fun updateWavHeader(file: File) {
        val raf = RandomAccessFile(file, "rw")
        val fileSize = raf.length()
        val dataSize = fileSize - 44

        // RIFF chunk size
        raf.seek(4)
        raf.write(intToByteArray((fileSize - 8).toInt()))

        // data chunk size
        raf.seek(40)
        raf.write(intToByteArray(dataSize.toInt()))

        raf.close()
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            (value shr 8 and 0xff).toByte(),
            (value shr 16 and 0xff).toByte(),
            (value shr 24 and 0xff).toByte()
        )
    }
}
