package com.nexora.android.runtime.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicLong

/**
 * Salida de audio real sobre `AudioTrack`.
 *
 * Sustituye al stub de la Fase 7: aquella versión solo **componía** variables
 * de entorno (`NEXORA_AUDIO_RATE`, `PULSE_LATENCY_MSEC`); esta abre un
 * `AudioTrack` en modo stream y escribe PCM 16 bits.
 *
 * Decisiones que importan:
 *
 * - **`USAGE_GAME` + `CONTENT_TYPE_MUSIC`**: Android trata así el flujo como
 *   audio de juego (sin ducking agresivo, sin normalización de volumen) y
 *   elige la ruta de menor latencia disponible.
 * - **`PERFORMANCE_MODE_LOW_LATENCY`** cuando el búfer es ≤ 192 frames: pide
 *   la ruta rápida (AAudio por debajo) y evita el mezclador profundo.
 * - **`WRITE_BLOCKING`**: el hilo productor se bloquea cuando el búfer está
 *   lleno, que es el control de flujo natural. Sin él habría que dormir a mano
 *   y se acumularía retraso.
 * - **Un fallo no tumba la sesión**: si `AudioTrack` no arranca (dispositivo
 *   sin salida, formato no soportado), el motor sigue ejecutando el juego sin
 *   sonido y la UI lo dice. Un juego mudo es mejor que un cierre inesperado.
 */
class AudioTrackSink(
    private val config: AudioSinkConfig,
) {

    enum class State { IDLE, PLAYING, FAILED, CLOSED }

    private var track: AudioTrack? = null
    private val written = AtomicLong(0)
    private var underrunCount = 0

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var lastError: String? = null
        private set

    /** Bytes escritos desde que se abrió. */
    val bytesWritten: Long get() = written.get()

    /** `true` si el `AudioTrack` está reproduciendo. */
    val isPlaying: Boolean get() = track?.playState == AudioTrack.PLAYSTATE_PLAYING

    /** Abre y arranca el `AudioTrack`. Idempotente. */
    fun open(): Boolean {
        if (state == State.PLAYING) return true
        return try {
            val minBuffer = AudioTrack.getMinBufferSize(
                config.sampleRateHz,
                config.channelMask,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                state = State.FAILED
                lastError = "El dispositivo no soporta ${config.sampleRateHz} Hz estéreo PCM 16"
                return false
            }

            val builder = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(config.sampleRateHz)
                        .setChannelMask(config.channelMask)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(config.bufferBytes, minBuffer))

            if (config.lowLatency) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            val created = builder.build()
            if (created.state != AudioTrack.STATE_INITIALIZED) {
                created.release()
                state = State.FAILED
                lastError = "AudioTrack no se inicializó (estado ${created.state})"
                return false
            }
            created.play()
            track = created
            state = State.PLAYING
            lastError = null
            true
        } catch (error: Exception) {
            state = State.FAILED
            lastError = "No se pudo abrir AudioTrack: ${error.message}"
            false
        }
    }

    /**
     * Escribe un bloque PCM.
     *
     * @return bytes aceptados; 0 si el sink no está en marcha.
     */
    fun write(buffer: ByteArray, offset: Int = 0, size: Int = buffer.size): Int {
        val active = track ?: return 0
        if (state != State.PLAYING) return 0
        return try {
            val result = active.write(buffer, offset, size, AudioTrack.WRITE_BLOCKING)
            if (result > 0) {
                written.addAndGet(result.toLong())
                val performance = runCatching { active.underrunCount }.getOrDefault(0)
                if (performance > underrunCount) underrunCount = performance
            } else if (result < 0) {
                state = State.FAILED
                lastError = "AudioTrack.write devolvió $result"
            }
            maxOf(result, 0)
        } catch (error: Exception) {
            state = State.FAILED
            lastError = "Error escribiendo audio: ${error.message}"
            0
        }
    }

    /** Pausa sin liberar el recurso. */
    fun pause() {
        runCatching { track?.pause() }
        if (state == State.PLAYING) state = State.IDLE
    }

    /** Vuelve a arrancar tras [pause]. */
    fun resume(): Boolean {
        val active = track ?: return open()
        return try {
            active.play()
            state = State.PLAYING
            true
        } catch (error: Exception) {
            state = State.FAILED
            lastError = error.message
            false
        }
    }

    /** Detiene y libera el `AudioTrack`. Idempotente. */
    fun close() {
        val active = track ?: run {
            state = State.CLOSED
            return
        }
        track = null
        runCatching { active.stop() }
        runCatching { active.release() }
        state = State.CLOSED
    }

    /** Estadísticas para el diagnóstico. */
    fun stats(): Stats = Stats(
        state = state,
        sampleRateHz = config.sampleRateHz,
        bufferBytes = config.bufferBytes,
        bufferFrames = config.bufferFrames,
        latencyMs = config.latencyMs,
        bytesWritten = written.get(),
        underruns = underrunCount,
        lastError = lastError,
    )

    data class Stats(
        val state: State,
        val sampleRateHz: Int,
        val bufferBytes: Int,
        val bufferFrames: Int,
        val latencyMs: Int,
        val bytesWritten: Long,
        val underruns: Int,
        val lastError: String?,
    ) {
        /** Segundos de audio escritos. */
        val secondsPlayed: Double
            get() = if (sampleRateHz <= 0) 0.0
            else bytesWritten.toDouble() / (sampleRateHz * 2.0 * 2.0)
    }

    companion object {
        /** Atajo para saber si el dispositivo soporta un formato. */
        fun supports(sampleRateHz: Int, channelCount: Int = 2): Boolean =
            AudioTrack.getMinBufferSize(
                sampleRateHz,
                if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
            ) > 0

        /** Volumen de referencia (0..1) usado al abrir el sink. */
        const val DEFAULT_VOLUME = 1.0f
    }
}
