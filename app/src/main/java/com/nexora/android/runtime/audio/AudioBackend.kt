package com.nexora.android.runtime.audio

/**
 * Backends de audio soportados por el runtime.
 *
 * - [AAUDIO] usa `AAudio` (API 26+), recomendado por Google para baja
 *   latencia en Android moderno.
 * - [OPENSL_ES] usa `OpenSL ES` (legacy, pero estable en algunos dispositivos
 *   y motores como el de FEX-Emu que aún lo prefieren).
 * - [PULSE_AUTO] deja que el motor (Wine/Box64) elija el sink PulseAudio/PipeWire
 *   que tenga en el sistema; es el modo "automático" usado por defecto.
 *
 * [wireValue] es el valor textual que se exporta a la variable de entorno
 * que el motor lee (por ejemplo `NEXORA_AUDIO_BACKEND`).
 * [label] es la etiqueta legible en español para la UI.
 */
enum class AudioBackend(val wireValue: String, val label: String) {
    AAUDIO("aaudio", "AAudio (baja latencia)"),
    OPENSL_ES("opensles", "OpenSL ES (compatibilidad)"),
    PULSE_AUTO("pulse", "PulseAudio (automático)");

    companion object {
        val DEFAULT: AudioBackend = PULSE_AUTO

        /**
         * Resuelve un [AudioBackend] desde un valor externo (storage, deep link,
         * import de configuración). Si el valor no encaja con ninguno conocido,
         * degrada a [DEFAULT] en lugar de lanzar: la app debe poder arrancar
         * aunque el dato guardado esté corrupto.
         */
        fun fromWireValue(value: String?): AudioBackend = when (value) {
            AAUDIO.wireValue -> AAUDIO
            OPENSL_ES.wireValue -> OPENSL_ES
            PULSE_AUTO.wireValue -> PULSE_AUTO
            else -> DEFAULT
        }
    }
}

/**
 * Frecuencias de muestreo permitidas.
 *
 * 44,1 kHz es la frecuencia "CD"; 48 kHz es la que prefiere la mayoría de
 * motores de juego (DXVK, WASAPI, OpenAL). Mantenemos solo dos para evitar
 * combinaciones raras que disparen resampling dentro del Wine.
 */
enum class AudioSampleRate(val hz: Int, val wireValue: String, val label: String) {
    HZ_44100(44_100, "44100", "44,1 kHz (calidad CD)"),
    HZ_48000(48_000, "48000", "48 kHz (juegos / DXVK)");

    companion object {
        val DEFAULT: AudioSampleRate = HZ_48000

        fun fromWireValue(value: String?): AudioSampleRate = when (value) {
            HZ_44100.wireValue -> HZ_44100
            HZ_48000.wireValue -> HZ_48000
            else -> DEFAULT
        }
    }
}

/**
 * Tamaños de buffer típicos para evitar crackling (muy pequeño) o latencia
 * audible (muy grande). Los valores se expresan en **frames** por periodo:
 *
 * - 96  → ~2,0 ms a 48 kHz (modo "gaming" agresivo, recomendado en Adreno/Mali
 *   modernos).
 * - 192 → ~4,0 ms a 48 kHz (buen balance).
 * - 480 → ~10 ms a 48 kHz (estable en dispositivos antiguos).
 */
enum class AudioBufferFrames(val frames: Int, val wireValue: String, val label: String) {
    SMALL_96(96, "96", "Pequeño (~2 ms, baja latencia)"),
    MEDIUM_192(192, "192", "Medio (~4 ms, recomendado)"),
    LARGE_480(480, "480", "Grande (~10 ms, estable)"),
    XLARGE_1024(1024, "1024", "Muy grande (~21 ms, sin chasquidos)");

    companion object {
        val DEFAULT: AudioBufferFrames = MEDIUM_192

        fun fromWireValue(value: String?): AudioBufferFrames = when (value) {
            SMALL_96.wireValue -> SMALL_96
            MEDIUM_192.wireValue -> MEDIUM_192
            LARGE_480.wireValue -> LARGE_480
            XLARGE_1024.wireValue -> XLARGE_1024
            else -> DEFAULT
        }
    }
}