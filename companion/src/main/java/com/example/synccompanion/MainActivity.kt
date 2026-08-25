package com.example.synccompanion

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class MainActivity : AppCompatActivity() {
    private lateinit var statusText: TextView
    private lateinit var transferStatusText: TextView
    private var serverSocket: ServerSocket? = null
    private var isReceiving: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.status_text)
        transferStatusText = findViewById(R.id.transfer_status_text)
        val startServerBtn = findViewById<Button>(R.id.start_server_btn)
        val sendNotesBtn = findViewById<Button>(R.id.send_notes_btn)

        startServerBtn.setOnClickListener {
            if (serverSocket == null) {
                startWifiServer()
            } else {
                Toast.makeText(this, "Server already running", Toast.LENGTH_SHORT).show()
            }
        }

        sendNotesBtn.setOnClickListener {
            val ip = getWiFiIpAddress()
            statusText.text = "My IP: $ip"
            Toast.makeText(this, "Your IP is $ip", Toast.LENGTH_LONG).show()
        }
    }

    private fun startWifiServer() {
        Thread {
            try {
                serverSocket = ServerSocket(5000)
                isReceiving = true
                runOnUiThread {
                    statusText.text = "Server listening on port 5000"
                    Toast.makeText(this, "Server started", Toast.LENGTH_SHORT).show()
                }

                while (isReceiving) {
                    val socket = serverSocket?.accept() ?: break
                    handleIncomingSocket(socket)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(this, "Error starting server: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun handleIncomingSocket(socket: Socket) {
        Thread {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val content = reader.readText()
                
                val outputDir = getExternalFilesDir(null)
                val outputFile = File(outputDir, "note_${System.currentTimeMillis()}.txt")
                
                FileOutputStream(outputFile).use { it.write(content.toByteArray()) }
                
                runOnUiThread {
                    transferStatusText.text = "Received note: ${content.take(20)}..."
                    Toast.makeText(this, "Note received and saved!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                socket.close()
            }
        }.start()
    }

    private fun getWiFiIpAddress(): String {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return Formatter.formatIpAddress(wm.connectionInfo.ipAddress)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isReceiving = false
        try {
            serverSocket?.close()
        } catch (e: Exception) { }
    }
}
