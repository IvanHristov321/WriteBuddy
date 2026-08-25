# Bulgarian Voice Notes — Android Studio Project

Native Android app for voice recording with **best-available Bulgarian transcription** using Whisper.cpp. Saves locally; companion app syncs notes over WiFi to your other devices.

## Project Structure

```
android_voice_app/
├── app/                                    # Main recording/transcription app
│   ├── src/main/
│   │   ├── AndroidManifest.xml              # Permissions: RECORD_AUDIO, INTERNET, STORAGE
│   │   ├── java/com/example/android_voice_notes/
│   │   │   ├── MainActivity.kt             # Main UI, recording controls, WiFi send
│   │   │   ├── VoiceRecorder.kt            # Audio recording (MediaRecorder)
│   │   │   └── transcription/
│   │   │       └── TranscriptionManager.kt  # JNI bridge to Whisper.cpp
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt              # Native build config
│   │   │   └── whisper_wrapper.cpp          # JNI C++ implementation
│   │   ├── res/layout/
│   │   │   └── activity_main.xml
│   │   └── assets/                         # Place Bulgarian model here:
│   │       └── ggml-base.bg.bin            # Download from whisper.cpp releases
│   └── build.gradle
│
├── companion/                              # Companion app for WiFi file sync
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── AndroidManifest.xml          # Permissions: INTERNET, STORAGE
│   │   │   ├── java/com/example/synccompanion/
│   │   │   │   ├── MainActivity.kt         # WiFi server (port 5000)
│   │   │   │   └── FileTransferService.kt  # Background file receiver
│   │   │   └── res/layout/
│   │   │       └── activity_main.xml
│   │   └── build.gradle
│
├── settings.gradle                         # Multi-module project
├── build.gradle                            # Root build config
└── gradle.properties                       # AndroidX, Jetifier settings
```

## Features

| Feature | Status | Description |
|---------|--------|-------------|
| Voice Recording | ✅ Done | Records audio to WAV format using MediaRecorder |
| Bulgarian Transcription | ✅ Done | JNI bridge to Whisper.cpp for Bulgarian language |
| Local Storage | ✅ Done | Saves .wav + .txt files to device storage |
| WiFi Sync | ✅ Done | Companion app receives files over local WiFi |
| Android Studio Ready | ✅ Done | Full Gradle project with CMake for native code |

## Setting Up in Android Studio

### 1. Open Project
```
File → Open → Select android_voice_app/ folder
```

### 2. Install NDK (Required for Whisper.cpp)
```
Tools → SDK Manager → SDK Tools → NDK (Side by side) → Check latest version → Apply
```

### 3. Download Bulgarian Whisper Model
```bash
# Download ggml-base model (smaller, faster) or ggml-small (more accurate)
wget https://github.com/ggerganov/whisper.cpp/releases/download/v1.5.0/ggml-base.bin
# Rename to bg model name expected by app
mv ggml-base.bin ggml-base.bg.bin

# Or download the small model for better accuracy
wget https://github.com/ggerganov/whisper.cpp/releases/download/v1.5.0/ggml-small.bin
mv ggml-small.bin ggml-small.bg.bin
```

### 4. Add Model to Assets
```
Copy ggml-base.bg.bin to: app/src/main/assets/
```

### 5. Build & Run
```
Build → Make Project (Ctrl+F9)
Run → Run 'app' (Shift+F10)
```

## Best Bulgarian Transcription Models

| Model | Size | Speed | Accuracy | Best For |
|-------|------|-------|----------|----------|
| `ggml-tiny.bg.bin` | ~75 MB | ⚡⚡⚡⚡⚡ | ★★☆☆☆ | Quick tests |
| `ggml-base.bg.bin` | ~140 MB | ⚡⚡⚡⚡ | ★★★☆☆ | Balanced |
| `ggml-small.bg.bin` | ~465 MB | ⚡⚡⚡ | ★★★★☆ | **Recommended** |
| `ggml-medium.bg.bin` | ~1.4 GB | ⚡⚡ | ★★★★★ | Best accuracy |

> Download from: https://github.com/ggerganov/whisper.cpp/releases

## How to Use

### Main App (Voice Notes)
1. **Record**: Tap "🎤 Record" button → speak in Bulgarian
2. **Transcribe**: Automatic after recording stops
3. **View**: See transcription in the text area
4. **Send**: Tap "📤 Send to Companion" → enter companion device IP

### Companion App (File Receiver)
1. **Start Server**: Tap "Start WiFi Server"
2. **Note IP**: Display shows IP (e.g., `192.168.1.100:5000`)
3. **Receive**: Notes auto-save when main app connects
4. **Done**: Files appear in app's storage folder

## Extending the Project

| Feature | File | Description |
|---------|------|-------------|
| Better recording | `VoiceRecorder.kt` | Add noise cancellation, gain control |
| mDNS discovery | `MainActivity.kt` | Auto-discover companion on network |
| Background sync | `FileTransferService.kt` | Sync even when app is closed |
| Note list/history | Add `RecyclerView` | Browse past recordings |
| Encrypted transfer | Socket TLS | Secure notes over WiFi |
| Cloud backup | Add cloud provider | Backup to Google Drive/Dropbox |

## Troubleshooting

### "Native library not found"
- Ensure NDK is installed: `Tools → SDK Manager → SDK Tools → NDK`
- Rebuild project: `Build → Clean Project → Make Project`

### "Model file not found"
- Check model is in: `app/src/main/assets/ggml-base.bg.bin`
- Check spelling matches exactly

### "WiFi connection failed"
- Ensure both devices on same WiFi network
- Check firewall allows port 5000
- Try disabling VPN if enabled

## License

MIT License - Use freely for personal or commercial projects.
