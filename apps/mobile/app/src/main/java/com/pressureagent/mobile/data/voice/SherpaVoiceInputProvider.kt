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
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Offline ASR using sherpa-onnx streaming Transducer (bilingual zh-en).
 *
 * Recognizer is created once on a background thread at construction time
 * and reused across all [listen] calls — model loading (~80 MB) happens
 * only once, so subsequent voice activations are near-instant.
 *
 * Only one recognition session can be active at a time; overlapping calls
 * are rejected to prevent AudioRecord resource exhaustion and crashes.
 */
class SherpaVoiceInputProvider(private val context: Context) : VoiceInputProvider, Closeable {

    // ── Recognizer: preloaded once, reused forever ───────────────

    @Volatile
    private var recognizer: OnlineRecognizer? = null

    // CountDownLatch so listen() can wait for the preload to finish
    private val preloadLatch = CountDownLatch(1)
    @Volatile
    private var preloadError: String? = null

    // Per-listen state — only one session at a time
    private val listenInProgress = AtomicBoolean(false)
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

    // ── Eager preload ────────────────────────────────────────────

    init {
        Thread {
            try {
                android.util.Log.d("AURI-ASR", "Preloading sherpa-onnx models on background thread...")
                val t0 = System.currentTimeMillis()
                val rec = buildRecognizer()
                recognizer = rec
                android.util.Log.d("AURI-ASR", "Preload complete in ${System.currentTimeMillis() - t0}ms")
            } catch (e: Exception) {
                android.util.Log.e("AURI-ASR", "Preload failed", e)
                preloadError = e.message
            } finally {
                preloadLatch.countDown()
            }
        }.apply {
            name = "sherpa-preload"
            isDaemon = true
            priority = Thread.NORM_PRIORITY
            start()
        }
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

    // ── Recognizer builder ───────────────────────────────────────

    private fun buildRecognizer(): OnlineRecognizer {
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

    /** Wait for the preloaded recognizer, or build one synchronously if preload failed. */
    private fun awaitRecognizer(): OnlineRecognizer {
        // Wait up to 10s for the preload thread to finish
        preloadLatch.await(10, TimeUnit.SECONDS)
        val existing = recognizer
        if (existing != null) return existing
        // Preload didn't finish in time or failed — build synchronously
        android.util.Log.w("AURI-ASR", "Preload not ready, building synchronously (preloadError=$preloadError)")
        val rec = buildRecognizer()
        recognizer = rec
        return rec
    }

    /** Release the recognizer — call when the provider is no longer needed. */
    override fun close() {
        try { recognizer?.release() } catch (_: Exception) {}
        recognizer = null
    }

    // ── VoiceInputProvider implementation ─────────────────────────

    override fun listen(): Flow<VoiceInputEvent> = callbackFlow {
        // ── Guard: prevent overlapping sessions ──────────────────
        if (!listenInProgress.compareAndSet(false, true)) {
            send(VoiceInputEvent.Error("语音识别已在运行中"))
            return@callbackFlow
        }

        withContext(Dispatchers.IO) {
            stopRequested.set(false)
            var stream: OnlineStream? = null
            var audioRec: AudioRecord? = null
            var lastPartial = ""

            try {
                // Wait for preloaded recognizer (near-instant after first use)
                val rec = awaitRecognizer()
                stream = rec.createStream().also { currentStream = it }
                android.util.Log.d("AURI-ASR", "Stream created, opening microphone...")

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

                if (audioRec.state != AudioRecord.STATE_INITIALIZED) {
                    send(VoiceInputEvent.Error("麦克风初始化失败"))
                    return@withContext
                }

                audioRec.startRecording()
                isListening = true
                send(VoiceInputEvent.ListeningStarted)
                android.util.Log.d("AURI-ASR", "Mic active, listening...")

                // 100ms chunks for responsive stop
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
                        android.util.Log.d("AURI-ASR", "Stop — endpoint=${rec.isEndpoint(stream)} manual=${stopRequested.get()} final='$finalText'")
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
                if (lastPartial.isNotBlank()) {
                    try { send(VoiceInputEvent.FinalResult(lastPartial)) } catch (_: Exception) {}
                }
                send(VoiceInputEvent.Error("语音异常: ${e.message}"))
            } finally {
                // ── Thorough cleanup, every path ─────────────────
                isListening = false
                try { audioRec?.stop() } catch (_: Exception) {}
                try { audioRec?.release() } catch (_: Exception) {}
                try { stream?.release() } catch (_: Exception) {}
                // Recognizer stays cached — only stream + audio are per-session
                audioRecord = null
                currentStream = null
                listenInProgress.set(false)
                android.util.Log.d("AURI-ASR", "Session cleanup done")
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
