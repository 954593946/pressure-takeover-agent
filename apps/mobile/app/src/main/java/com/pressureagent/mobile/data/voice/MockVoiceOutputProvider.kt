package com.pressureagent.mobile.data.voice

import android.util.Log
import com.pressureagent.mobile.domain.voice.VoiceOutputProvider

/**
 * Mock voice output — logs to console instead of speaking.
 *
 * In a real app, replace with AndroidVoiceOutputProvider which wraps
 * android.speech.tts.TextToSpeech.
 */
class MockVoiceOutputProvider : VoiceOutputProvider {

    override var isSpeaking: Boolean = false
        private set

    override fun speak(text: String) {
        isSpeaking = true
        Log.d("AURI-TTS", "[模拟播报] $text")
        // In real impl, TextToSpeech.speak() is async; here we just set speaking briefly
        isSpeaking = false
    }

    override fun stop() {
        isSpeaking = false
    }
}
