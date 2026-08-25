cmake_minimum_required(VERSION 3.22.1)
project("whisper_transcription")

# Find the Android NDK
find_library(log-lib log)

# Add Whisper.cpp library (you need to build or download whisper.cpp first)
# For now, we set up the structure
add_library(
    whisper_transcription
    SHARED
    whisper_wrapper.cpp
)

target_include_directories(whisper_transcription PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

target_link_libraries(whisper_transcription ${log-lib})
