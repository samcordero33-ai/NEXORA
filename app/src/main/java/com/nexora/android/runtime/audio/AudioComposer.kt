package com.nexora.android.runtime.audio

/**
 * Función pura que produce las variables de entorno relacionadas con audio
 * que el motor de Wine/PulseAudio puede leer.
 *
 * Diseño:
 * - `NEXORA_AUDIO_BACKEND` indica el backend elegido (aaudio | opensles | pulse).
 * - `NEXORA_AUDIO_RATE` y `NEXORA_AUDIO_BUFFER` transportan la frecuencia y
 *   el tamaño de buffer.
 * - `PULSE_LATENCY_MSEC` se exporta **solo** cuando [AudioBackend.PULSE_AUTO]
 *   está activo y [AudioSettings.preventClicks] es true; traduce el tamaño de
 *   buffer a milisegundos para PulseAudio.
 * - `NEXORA_PREVENT_CLICKS` se exporta siempre que esté activo: el motor
 *   puede decidir si suaviza arranque/paro del sink.
 *
 * Devuelve un mapa inmutable y, si hay combinaciones dudosas (por ejemplo,
 * `XLARGE_1024` con `HZ_44100`), añade una advertencia para que la UI la
 * muestre antes de lanzar.
 */
object AudioComposer {

    fun compose(settings: AudioSettings): AudioComposition {
        val map = LinkedHashMap<String, String>()
        val warnings = mutableListOf<String>()

        map["NEXORA_AUDIO_BACKEND"] = settings.backend.wireValue
        map["NEXORA_AUDIO_RATE"] = settings.sampleRate.wireValue
        map["NEXORA_AUDIO_BUFFER"] = settings.bufferFrames.wireValue

        // PulseAudio latency solo tiene sentido en modo pulse.
        if (settings.backend == AudioBackend.PULSE_AUTO && settings.preventClicks) {
            val latencyMs = latencyMillis(settings.bufferFrames, settings.sampleRate)
            map["PULSE_LATENCY_MSEC"] = latencyMs.toString()
        }

        if (settings.preventClicks) {
            map["NEXORA_PREVENT_CLICKS"] = "1"
        }

        // Combinaciones potencialmente problemáticas.
        if (settings.bufferFrames == AudioBufferFrames.XLARGE_1024 &&
            settings.sampleRate == AudioSampleRate.HZ_44100
        ) {
            warnings += "Buffer muy grande a 44,1 kHz: la latencia puede ser molesta en juegos reactivos."
        }
        if (settings.bufferFrames == AudioBufferFrames.SMALL_96 &&
            settings.backend == AudioBackend.OPENSL_ES
        ) {
            warnings += "OpenSL ES no siempre respeta buffers muy pequeños; pueden aparecer chasquidos."
        }

        return AudioComposition(
            environment = map.toMap(),
            warnings = warnings.toList(),
        )
    }

    /**
     * Conversión frames → milisegundos para `PULSE_LATENCY_MSEC`. Se redondea
     * hacia abajo al múltiplo de 1 ms más cercano; con un buffer de 192 a
     * 48 kHz da 4 ms exactos.
     */
    fun latencyMillis(buffer: AudioBufferFrames, rate: AudioSampleRate): Int {
        val numerator = buffer.frames * 1000
        val result = numerator / rate.hz
        return result.coerceAtLeast(1)
    }
}

/** Resultado del [AudioComposer.compose]. */
data class AudioComposition(
    val environment: Map<String, String>,
    val warnings: List<String>,
)