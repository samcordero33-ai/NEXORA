package com.nexora.android.runtime.audio

/**
 * Parámetros concretos con los que se abre un `AudioTrack`.
 *
 * Es la traducción de [AudioSettings] (Fase 7) al vocabulario de
 * `android.media`, en una clase **pura** para poder verificarla en JVM: el
 * tamaño de búfer es donde se rompen las cosas (demasiado pequeño → chasquidos;
 * demasiado grande → latencia audible), y conviene probar la aritmética sin un
 * dispositivo.
 *
 * @property sampleRateHz 44 100 o 48 000.
 * @property channelCount 2 (estéreo), que es lo que usan los juegos.
 * @property bytesPerSample 2 (PCM 16 bits).
 * @property bufferBytes Tamaño del búfer del `AudioTrack`, alineado a frame.
 * @property lowLatency Si se pide `PERFORMANCE_MODE_LOW_LATENCY`.
 */
data class AudioSinkConfig(
    val sampleRateHz: Int,
    val channelCount: Int,
    val bytesPerSample: Int,
    val bufferBytes: Int,
    val lowLatency: Boolean,
) {

    /** Bytes de un frame (todas las muestras de todos los canales). */
    val bytesPerFrame: Int get() = channelCount * bytesPerSample

    /** Frames que caben en el búfer. */
    val bufferFrames: Int get() = bufferBytes / bytesPerFrame

    /** Latencia teórica de un búfer lleno, en milisegundos. */
    val latencyMs: Int get() = (bufferFrames * 1000) / sampleRateHz

    /** Formato de canal de Android (`CHANNEL_OUT_STEREO` = 0x0C). */
    val channelMask: Int get() = if (channelCount == 1) 0x04 else 0x0C

    companion object {

        const val BYTES_PER_SAMPLE = 2

        /**
         * Construye la configuración.
         *
         * El búfer es **el doble** del periodo elegido: `AudioTrack` consume
         * mientras la app produce, y con un solo periodo cualquier retraso de
         * planificación produce un hueco (chasquido). El mínimo del sistema
         * (`minBufferBytes`) manda si es mayor, porque por debajo de él
         * `AudioTrack` no arranca en muchos dispositivos.
         *
         * @param minBufferBytes Resultado de `AudioTrack.getMinBufferSize`.
         */
        fun from(
            settings: AudioSettings,
            minBufferBytes: Int,
            channelCount: Int = 2,
        ): AudioSinkConfig {
            val bytesPerFrame = channelCount * BYTES_PER_SAMPLE
            val desired = settings.bufferFrames.frames * bytesPerFrame * DOUBLE_BUFFERING
            val raw = maxOf(desired, minBufferBytes.coerceAtLeast(bytesPerFrame))
            val aligned = align(raw, bytesPerFrame)
            return AudioSinkConfig(
                sampleRateHz = settings.sampleRate.hz,
                channelCount = channelCount,
                bytesPerSample = BYTES_PER_SAMPLE,
                bufferBytes = aligned,
                lowLatency = settings.bufferFrames.frames <= LOW_LATENCY_MAX_FRAMES,
            )
        }

        /** Sube [value] al siguiente múltiplo de [frame] (mínimo un frame). */
        fun align(value: Int, frame: Int): Int {
            if (frame <= 0) return value
            val frames = (value + frame - 1) / frame
            return maxOf(1, frames) * frame
        }

        private const val DOUBLE_BUFFERING = 2
        private const val LOW_LATENCY_MAX_FRAMES = 192
    }
}
