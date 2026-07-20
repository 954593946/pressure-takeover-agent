package com.pressureagent.mobile.domain.voice

import kotlinx.coroutines.flow.Flow

/**
 * Voice input — Speech-to-Text (ASR).
 */
interface VoiceInputProvider {

    /** Start listening. Returns a Flow of partial/final results. */
    fun listen(): Flow<VoiceInputEvent>

    /** Stop listening and get final result (if any). */
    fun stop()

    /** Whether currently listening. */
    val isListening: Boolean
}

/** Events emitted during voice recognition. */
sealed class VoiceInputEvent {
    /** Microphone opened, waiting for speech. */
    data object ListeningStarted : VoiceInputEvent()

    /** Partial (interim) recognition result. */
    data class PartialResult(val text: String) : VoiceInputEvent()

    /** Final recognition result. */
    data class FinalResult(val text: String) : VoiceInputEvent()

    /** No speech detected / timeout. */
    data object NoSpeech : VoiceInputEvent()

    /** Recognition error. */
    data class Error(val message: String) : VoiceInputEvent()
}
