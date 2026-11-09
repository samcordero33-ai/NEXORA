package com.nexora.android.runtime.audio

import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Origen de PCM crudo que hay que reproducir.
 *
 * El motor emulado no puede llamar a `AudioTrack` directamente: es un proceso
 * Linux aparte. La vía que funciona sin JNI es un **FIFO** (o un archivo) donde
 * el motor escribe PCM y la app lee; de ahí [FifoPcmSource].
 *
 * La interfaz existe para poder sustituirlo en pruebas por un origen en
 * memoria, sin `AudioTrack` ni sistema de archivos.
 */
interface AudioPcmSource {

    /** Frecuencia del flujo; debe coincidir con la del `AudioTrack`. */
    val sampleRateHz: Int

    /** Canales del flujo (1 o 2). */
    val channelCount: Int

    /** Abre el flujo. Bloquea hasta que hay datos o hasta que se cierra. */
    fun open(): InputStream

    /** Descripción para el diagnóstico. */
    val description: String
}

/**
 * Lee PCM de un FIFO creado por el runtime.
 *
 * Se abre en modo lectura **bloqueante**: si el motor todavía no ha abierto el
 * otro extremo, `open()` espera. Es el comportamiento correcto —no hay nada
 * que reproducir todavía— y evita un bucle de reintentos.
 *
 * El FIFO se crea fuera de la app (`mkfifo`), porque crearlo requiere una
 * llamada al sistema que Kotlin no expone; el motor lo crea al arrancar el
 * contenedor.
 */
class FifoPcmSource(
    private val path: String,
    override val sampleRateHz: Int,
    override val channelCount: Int = 2,
) : AudioPcmSource {

    override val description: String get() = "fifo:$path"

    /** `true` si el FIFO ya existe. */
    fun exists(): Boolean = File(path).exists()

    override fun open(): InputStream = FileInputStream(path)
}

/**
 * Conecta un [AudioPcmSource] con un [AudioTrackSink].
 *
 * El bucle es deliberadamente simple: leer un bloque, escribir el bloque,
 * repetir. `AudioTrack.WRITE_BLOCKING` hace el control de flujo, así que no hay
 * que dormir ni calcular tiempos: si la app va por delante, se bloquea; si va
 * por detrás, se acumula un pequeño retraso y el contador de *underruns* lo
 * delata.
 *
 * Si el origen no existe todavía (el motor aún no ha creado el FIFO), el
 * pipeline no falla: queda en `WAITING` y reintenta, porque en un arranque
 * normal el juego tarda más en abrir el audio que la app en estar lista.
 */
class AudioPipeline(
    private val sinkFactory: (AudioSinkConfig) -> AudioTrackSink = { AudioTrackSink(it) },
    private val retryDelayMs: Long = 500L,
) {

    enum class State { IDLE, WAITING, PLAYING, STOPPED, FAILED }

    private var sink: AudioTrackSink? = null
    private var worker: Thread? = null

    @Volatile
    var state: State = State.IDLE
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var config: AudioSinkConfig? = null
        private set

    /** Arranca el bombeo. Idempotente: si ya está en marcha, no hace nada. */
    @Synchronized
    fun start(settings: AudioSettings, source: AudioPcmSource, minBufferBytes: Int): Boolean {
        if (state == State.PLAYING || state == State.WAITING) return true

        val sinkConfig = AudioSinkConfig.from(settings, minBufferBytes, source.channelCount)
        config = sinkConfig
        val active = sinkFactory(sinkConfig)
        if (!active.open()) {
            state = State.FAILED
            lastError = active.lastError ?: "AudioTrack no arrancó"
            return false
        }
        sink = active
        state = State.WAITING
        lastError = null

        val thread = Thread({ pump(source, active) }, "nexora-audio")
        thread.isDaemon = true
        worker = thread
        thread.start()
        return true
    }

    private fun pump(source: AudioPcmSource, active: AudioTrackSink) {
        var input: InputStream? = null
        try {
            while (true) {
                if (input == null) {
                    input = try {
                        source.open()
                    } catch (_: Exception) {
                        Thread.sleep(retryDelayMs)
                        continue
                    }
                    state = State.PLAYING
                }
                val activeInput = input ?: continue
                val buffer = ByteArray(BUFFER_BYTES)
                val read = activeInput.read(buffer)
                if (read <= 0) break
                val written = active.write(buffer, 0, read)
                if (written == 0 && active.state == AudioTrackSink.State.FAILED) {
                    state = State.FAILED
                    lastError = active.lastError
                    return
                }
            }
            state = State.STOPPED
        } catch (_: InterruptedException) {
            state = State.STOPPED
        } catch (error: Exception) {
            state = State.FAILED
            lastError = error.message ?: error.javaClass.simpleName
        } finally {
            runCatching { input?.close() }
        }
    }

    /** Detiene el bombeo y libera el `AudioTrack`. */
    @Synchronized
    fun stop() {
        worker?.interrupt()
        worker = null
        sink?.close()
        sink = null
        if (state != State.FAILED) state = State.STOPPED
    }

    /** Estadísticas actuales, o `null` si nunca se abrió. */
    fun stats(): AudioTrackSink.Stats? = sink?.stats()

    companion object {
        /** 20 ms a 48 kHz estéreo 16 bits: 3840 bytes. */
        const val BUFFER_BYTES = 4096
    }
}
