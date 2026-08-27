package com.example.android_voice_notes

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    }

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (isRecording) return true

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
            Log.e(TAG, "Invalid buffer size: $minBufferSize")
            return false
        }
        
        val bufferSize = minBufferSize * 2

        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )

        for (source in sources) {
            try {
                audioRecord = AudioRecord(
                    source,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    Log.d(TAG, "AudioRecord initialized with source: $source")
                    break
                } else {
                    Log.w(TAG, "Failed to initialize AudioRecord with source: $source")
                    audioRecord?.release()
                    audioRecord = null
                }
            } catch (e: Exception) {
                Log.w(TAG, "Exception initializing AudioRecord with source $source: ${e.message}")
            }
        }

        if (audioRecord == null) {
            Log.e(TAG, "All audio sources failed")
            return false
        }

        try {
            audioRecord?.startRecording()
            
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.e(TAG, "AudioRecord failed to enter recording state")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            isRecording = true

            val thread = Thread({
                writeAudioDataToFile(bufferSize)
            }, "AudioRecordingThread")
            recordingThread = thread
            thread.start()
            Log.d(TAG, "Recording started successfully: ${outputFile.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Exception starting recording: ${e.message}")
            audioRecord?.release()
            audioRecord = null
            return false
        }
    }

    private fun writeAudioDataToFile(bufferSize: Int) {
        val data = ByteArray(bufferSize)
        var os: FileOutputStream? = null
        try {
            os = FileOutputStream(outputFile)
            
            // Write placeholder WAV header
            writeWavHeader(os, CHANNEL_CONFIG, SAMPLE_RATE, AUDIO_FORMAT)

            while (isRecording) {
                val read = audioRecord?.read(data, 0, bufferSize) ?: 0
                if (read > 0) {
                    os.write(data, 0, read)
                } else if (read < 0) {
                    Log.e(TAG, "Error reading audio data: $read")
                    break
                } else {
                    // Small sleep to prevent tight loop if read returns 0
                    Thread.sleep(10)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing audio data to file", e)
        } finally {
            try {
                os?.close()
                updateWavHeader(outputFile)
            } catch (e: IOException) {
                Log.e(TAG, "Error closing file output stream", e)
            }
        }
    }

    fun stopRecording() {
        if (!isRecording) return

        isRecording = false
        try {
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord = null
        recordingThread?.join(1000) // Wait at most 1s for thread to finish
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
        if (!file.exists()) return
        var raf: RandomAccessFile? = null
        try {
            raf = RandomAccessFile(file, "rw")
            val fileSize = raf.length()
            if (fileSize < 44) return
            
            val dataSize = fileSize - 44

            // RIFF chunk size
            raf.seek(4)
            raf.write(intToByteArray((fileSize - 8).toInt()))

            // data chunk size
            raf.seek(40)
            raf.write(intToByteArray(dataSize.toInt()))
        } catch (e: Exception) {
            Log.e(TAG, "Error updating WAV header", e)
        } finally {
            raf?.close()
        }
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
