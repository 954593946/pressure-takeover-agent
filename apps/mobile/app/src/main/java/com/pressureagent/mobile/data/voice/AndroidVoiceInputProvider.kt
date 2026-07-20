package com.pressureagent.mobile.data.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.pressureagent.mobile.domain.voice.VoiceInputEvent
import com.pressureagent.mobile.domain.voice.VoiceInputProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Real Android speech recognition using [SpeechRecognizer] + [RecognitionListener].
 *
 * For devices without SpeechRecognizer, the ChatScreen falls back to
 * Intent-based recognition via [RecognizerIntent.ACTION_RECOGNIZE_SPEECH].
 */
class AndroidVoiceInputProvider(private val context: Context) : VoiceInputProvider {

    private var recognizer: SpeechRecognizer? = null
    override var isListening: Boolean = false
        private set

    override fun listen(): Flow<VoiceInputEvent> = callbackFlow {
        val send: (VoiceInputEvent) -> Unit = { trySend(it) }

        withContext(Dispatchers.Main) {
            try {
                recognizer?.destroy()
                val sr = SpeechRecognizer.createSpeechRecognizer(context).also {
                    recognizer = it
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.CHINESE)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }

                sr.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        send(VoiceInputEvent.ListeningStarted)
                    }
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        isListening = false
                        if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                            send(VoiceInputEvent.NoSpeech)
                        } else {
                            val msg = when (error) {
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "没有录音权限"
                                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "网络错误"
                                else -> "识别失败"
                            }
                            send(VoiceInputEvent.Error(msg))
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) send(VoiceInputEvent.FinalResult(text))
                        else send(VoiceInputEvent.NoSpeech)
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim() ?: ""
                        if (text.isNotBlank()) send(VoiceInputEvent.PartialResult(text))
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                sr.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                send(VoiceInputEvent.Error("语音启动失败: ${e.message}"))
            }
        }

        awaitClose {
            isListening = false
            try {
                recognizer?.stopListening()
                recognizer?.destroy()
            } catch (_: Exception) {}
            recognizer = null
        }
    }

    override fun stop() {
        isListening = false
        try { recognizer?.stopListening() } catch (_: Exception) {}
    }
}
