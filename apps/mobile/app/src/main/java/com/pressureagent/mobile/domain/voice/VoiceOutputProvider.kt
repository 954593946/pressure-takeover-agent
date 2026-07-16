package com.pressureagent.mobile.domain.voice

/**
 * Voice output — Text-to-Speech (TTS).
 *
 * Implementations:
 * - [MockVoiceOutputProvider] — no-op, just logs.
 * - AndroidVoiceOutputProvider → uses android.speech.tts.TextToSpeech.
 * - Cloud TTS provider → uses cloud API.
 */
interface VoiceOutputProvider {

    /** Speak the given text. Interrupts any current speech. */
    fun speak(text: String)

    /** Stop any ongoing speech. */
    fun stop()

    /** Whether currently speaking. */
    val isSpeaking: Boolean
}
