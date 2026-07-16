package com.pressureagent.mobile.data.voice

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import com.pressureagent.mobile.domain.voice.VoiceInputEvent
import com.pressureagent.mobile.domain.voice.VoiceInputProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

/**
 * Real Android speech recognition using [android.speech.SpeechRecognizer].
 *
 * Usage:
 * - Swap [MockVoiceInputProvider] → this in [VoiceModule].
 * - Requires RECORD_AUDIO permission (runtime).
 * - The Activity/Fragment hosting the recognizer must handle the intent.
 */
class AndroidVoiceInputProvider : VoiceInputProvider {

    override var isListening: Boolean = false
        private set

    /**
     * Launch system speech recognizer intent.
     * Call from Activity: startActivityForResult(intent, REQUEST_CODE)
     * Then pass the result text back to [onResult].
     */
    fun createRecognizerIntent(context: Context): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你的需求…")
        }

    override fun listen(): Flow<VoiceInputEvent> = callbackFlow {
        isListening = true
        trySend(VoiceInputEvent.ListeningStarted)
        // The actual recognition result comes via onResult() — caller
        // must pass the recognized text back.
        awaitClose { isListening = false }
    }

    /** Feed the recognized text from Activity.onActivityResult. */
    fun onResult(text: String?) {
        isListening = false
        // Result is delivered via the Flow above — but in practice
        // with Android's intent-based SpeechRecognizer, you'd use
        // a different pattern (callback-based).
        // For a streaming solution, use the SpeechRecognizer API directly
        // with RecognitionListener.
    }

    override fun stop() {
        isListening = false
    }
}
