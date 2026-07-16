package com.pressureagent.mobile.data.voice

import com.pressureagent.mobile.domain.voice.VoiceInputEvent
import com.pressureagent.mobile.domain.voice.VoiceInputProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mock voice input — simulates ASR with a predefined text.
 *
 * In a real app, replace with [AndroidVoiceInputProvider] which wraps
 * android.speech.SpeechRecognizer.
 */
class MockVoiceInputProvider : VoiceInputProvider {

    override var isListening: Boolean = false
        private set

    /** The text to "recognize". Set before calling [listen]. */
    var mockResult: String = "帮我创建任务"

    override fun listen(): Flow<VoiceInputEvent> = flow {
        isListening = true
        emit(VoiceInputEvent.ListeningStarted)

        // Simulate listening delay
        delay(800)

        // Simulate partial result
        val words = mockResult.split("")
        if (words.size > 2) {
            emit(VoiceInputEvent.PartialResult(words.take(words.size / 2).joinToString("")))
            delay(600)
        }

        // Emit final result
        emit(VoiceInputEvent.FinalResult(mockResult))
        isListening = false
    }

    override fun stop() {
        isListening = false
    }
}
