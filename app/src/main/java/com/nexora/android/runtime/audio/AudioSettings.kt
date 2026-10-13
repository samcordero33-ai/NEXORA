package com.nexora.android.runtime.audio

/**
 * Preferencias de audio del runtime, persistidas por juego.
 *
 * El objetivo no es reinventar la rueda del audio de Android, sino darle al
 * usuario tres perillas que **sí afectan al motor emulado** (DXVK/Wine/WASAPI):
 * el backend elegido por la app, el tamaño de buffer y la frecuencia de
 * muestreo. La prevención de chasquidos es una política local que se traduce
 * a `PULSE_LATENCY_MSEC` cuando [AudioBackend.PULSE_AUTO] está activo.
 */
data class AudioSettings(
    val backend: AudioBackend = AudioBackend.DEFAULT,
    val sampleRate: AudioSampleRate = AudioSampleRate.DEFAULT,
    val bufferFrames: AudioBufferFrames = AudioBufferFrames.DEFAULT,
    val preventClicks: Boolean = true,
) {
    companion object {
        val DEFAULT: AudioSettings = AudioSettings()

        /**
         * Recupera un [AudioSettings] desde el almacenamiento.
         *
         * Si cualquiera de los campos no encaja con un valor conocido, se usa el
         * valor por defecto: preferimos un audio que arranca a uno que falla
         * porque alguien escribió basura en la base de datos.
         */
        fun fromStorage(
            backend: String?,
            sampleRate: String?,
            bufferFrames: String?,
            preventClicks: Boolean?,
        ): AudioSettings = AudioSettings(
            backend = AudioBackend.fromWireValue(backend),
            sampleRate = AudioSampleRate.fromWireValue(sampleRate),
            bufferFrames = AudioBufferFrames.fromWireValue(bufferFrames),
            preventClicks = preventClicks ?: true,
        )
    }

    /** Devuelve una copia normalizada: por ahora es identidad, pero deja el
     *  sitio para reglas futuras (ej. forzar buffer grande si sampleRate = 44,1).
     */
    fun normalized(): AudioSettings = this
}