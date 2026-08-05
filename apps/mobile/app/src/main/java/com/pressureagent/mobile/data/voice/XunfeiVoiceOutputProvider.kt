package com.pressureagent.mobile.data.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.pressureagent.mobile.data.local.AppLogger
import com.pressureagent.mobile.domain.voice.VoiceOutputProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Voice output provider using Xunfei super-human-like TTS.
 *
 * Streams audio via [XunfeiTtsClient] WebSocket → [AudioTrack] playback.
 * Audio is played as PCM chunks arrive, minimizing first-word latency.
 */
class XunfeiVoiceOutputProvider(
    private val ttsClient: XunfeiTtsClient,
) : VoiceOutputProvider {

    private companion object {
        const val TAG = "XunfeiTTS-Out"
        const val SAMPLE_RATE = 16000
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null

    @Volatile
    override var isSpeaking: Boolean = false
        private set

    override fun speak(text: String) {
        if (text.isBlank()) return

        // Stop any current playback
        stop()

        playJob = scope.launch {
            isSpeaking = true
            try {
                ttsClient.synthesize(text).collect { result ->
                    if (!isActive) return@collect
                    when (result) {
                        is SynthesisResult.AudioChunk -> {
                            playChunk(result.pcm)
                        }
                        is SynthesisResult.Error -> {
                            AppLogger.e(TAG, "TTS error: ${result.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "TTS playback exception", e)
            } finally {
                releaseAudioTrack()
                isSpeaking = false
            }
        }
    }

    override fun stop() {
        playJob?.cancel()
        playJob = null
        releaseAudioTrack()
        isSpeaking = false
    }

    private fun playChunk(pcm: ByteArray) {
        try {
            val track = getOrCreateAudioTrack()
            track?.write(pcm, 0, pcm.size)
        } catch (e: Exception) {
            AppLogger.e(TAG, "AudioTrack write failed", e)
            releaseAudioTrack()
        }
    }

    private fun getOrCreateAudioTrack(): AudioTrack? {
        if (audioTrack?.state == AudioTrack.STATE_INITIALIZED) return audioTrack

        // Release any stale track
        releaseAudioTrack()

        val minBuffer = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufferSize = maxOf(minBuffer, 4096)

        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
                .also { track ->
                    track.play()
                    audioTrack = track
                }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create AudioTrack", e)
            null
        }
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }
}
