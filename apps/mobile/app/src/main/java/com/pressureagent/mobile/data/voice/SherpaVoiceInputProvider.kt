package com.pressureagent.mobile.data.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.pressureagent.mobile.domain.voice.VoiceInputEvent
import com.pressureagent.mobile.domain.voice.VoiceInputProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline ASR using sherpa-onnx streaming Paraformer (bilingual zh-en).
 */
class SherpaVoiceInputProvider(private val context: Context) : VoiceInputProvider {

    private var recognizer: OnlineRecognizer? = null
    private var currentStream: OnlineStream? = null
    private var audioRecord: AudioRecord? = null

    @Volatile
    override var isListening: Boolean = false
        private set

    // Signal the recognition loop to stop
    private val stopRequested = AtomicBoolean(false)

    companion object {
        private const val ASSET_DIR = "models/sherpa-onnx-streaming-zipformer-bilingual-zh-en-2023-02-20"
        private const val LOCAL_DIR = "sherpa-models/zipformer-bilingual-zh-en"
    }

    // ── Model preparation ────────────────────────────────────────

    private fun copyModelFile(fileName: String): String {
        val target = File(context.filesDir, "$LOCAL_DIR/$fileName")
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            context.assets.open("$ASSET_DIR/$fileName").use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
        }
        return target.absolutePath
    }

    // ── Recognizer factory ────────────────────────────────────────

    private fun createRecognizer(): OnlineRecognizer {
        val encoderPath = copyModelFile("encoder-epoch-99-avg-1.int8.onnx")
        val decoderPath = copyModelFile("decoder-epoch-99-avg-1.int8.onnx")
        val joinerPath = copyModelFile("joiner-epoch-99-avg-1.int8.onnx")
        val tokensPath = copyModelFile("tokens.txt")

        val config = OnlineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
            modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = encoderPath,
                    decoder = decoderPath,
                    joiner = joinerPath
                ),
                tokens = tokensPath,
                numThreads = 2,
                modelType = "transducer"
            ),
            decodingMethod = "greedy_search",
            enableEndpoint = true
        )
        return OnlineRecognizer(null, config)
    }

    // ── VoiceInputProvider implementation ─────────────────────────

    override fun listen(): Flow<VoiceInputEvent> = callbackFlow {
        withContext(Dispatchers.IO) {
            android.util.Log.d("AURI-ASR", "Loading recognizer...")
            stopRequested.set(false)
            var rec: OnlineRecognizer? = null
            var stream: OnlineStream? = null
            var audioRec: AudioRecord? = null
            var lastPartial = ""

            try {
                rec = createRecognizer().also { recognizer = it }
                stream = rec.createStream().also { currentStream = it }
                android.util.Log.d("AURI-ASR", "Recognizer ready")

                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
                audioRec = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    maxOf(minBuf * 2, sampleRate)
                ).also { audioRecord = it }

                audioRec.startRecording()
                isListening = true
                send(VoiceInputEvent.ListeningStarted)
                android.util.Log.d("AURI-ASR", "Mic active")

                // 100ms chunks for faster stop response
                val chunkSamples = sampleRate / 10
                val buffer = ShortArray(chunkSamples)

                while (isActive && !stopRequested.get()) {
                    val samplesRead = audioRec.read(buffer, 0, buffer.size)
                    if (samplesRead <= 0) continue

                    val floatSamples = FloatArray(samplesRead) { i ->
                        buffer[i].toFloat() / 32768.0f
                    }

                    stream.acceptWaveform(floatSamples, sampleRate)

                    while (rec.isReady(stream)) {
                        rec.decode(stream)
                    }

                    val text = rec.getResult(stream).text
                    if (text.isNotBlank()) {
                        lastPartial = text
                        send(VoiceInputEvent.PartialResult(text))
                    }

                    // Auto endpoint or manual stop
                    if (rec.isEndpoint(stream) || stopRequested.get()) {
                        // Decode any remaining frames
                        while (rec.isReady(stream)) {
                            rec.decode(stream)
                        }
                        val finalText = rec.getResult(stream).text.ifBlank { lastPartial }
                        android.util.Log.d("AURI-ASR", "Stop - endpoint=${rec.isEndpoint(stream)} manual=${stopRequested.get()} final='$finalText'")
                        if (finalText.isNotBlank()) {
                            send(VoiceInputEvent.FinalResult(finalText))
                        } else {
                            send(VoiceInputEvent.NoSpeech)
                        }
                        rec.reset(stream)
                        break
                    }
                }
            } catch (e: SecurityException) {
                android.util.Log.e("AURI-ASR", "Permission denied", e)
                send(VoiceInputEvent.Error("没有录音权限"))
            } catch (e: Exception) {
                android.util.Log.e("AURI-ASR", "Error", e)
                // If we have partial text when crashing, try to send it
                if (lastPartial.isNotBlank()) {
                    try { send(VoiceInputEvent.FinalResult(lastPartial)) } catch (_: Exception) {}
                }
                send(VoiceInputEvent.Error("语音异常: ${e.message}"))
            } finally {
                isListening = false
                try { audioRec?.stop() } catch (_: Exception) {}
                try { audioRec?.release() } catch (_: Exception) {}
                try { stream?.release() } catch (_: Exception) {}
                try { rec?.release() } catch (_: Exception) {}
                audioRecord = null
                currentStream = null
                recognizer = null
                android.util.Log.d("AURI-ASR", "Cleanup done")
            }
        }

        awaitClose {
            stopRequested.set(true)
            stop()
        }
    }

    override fun stop() {
        stopRequested.set(true)
        isListening = false
    }
}
