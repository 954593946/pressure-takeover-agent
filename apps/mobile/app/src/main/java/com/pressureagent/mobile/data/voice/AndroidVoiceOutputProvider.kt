package com.pressureagent.mobile.data.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.pressureagent.mobile.domain.voice.VoiceOutputProvider
import java.util.Locale
import java.util.UUID

/**
 * Real Android TTS using [android.speech.tts.TextToSpeech].
 *
 * Usage:
 * - Swap [MockVoiceOutputProvider] → this in [VoiceModule].
 * - Call [initialize] once with application context.
 * - Set language to Chinese: tts.language = Locale.CHINESE
 */
class AndroidVoiceOutputProvider(private val context: Context) : VoiceOutputProvider {

    private var tts: TextToSpeech? = null

    override var isSpeaking: Boolean = false
        private set

    /** Initialize TTS engine. Call once during app startup. */
    fun initialize(onReady: () -> Unit = {}) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isSpeaking = true }
                    override fun onDone(utteranceId: String?) { isSpeaking = false }
                    @Deprecated("Use onError(utteranceId, errorCode)")
                    override fun onError(utteranceId: String?) { isSpeaking = false }
                    override fun onError(utteranceId: String?, errorCode: Int) { isSpeaking = false }
                })
                onReady()
            }
        }
    }

    override fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    override fun stop() {
        tts?.stop()
        isSpeaking = false
    }

    fun shutdown() {
        tts?.shutdown()
    }
}
