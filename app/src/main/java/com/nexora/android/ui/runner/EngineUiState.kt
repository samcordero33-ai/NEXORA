package com.nexora.android.ui.runner

/**
 * Estado de la instalación del runtime nativo (Fase 8).
 *
 * Refleja lo que hay **de verdad** en `filesDir/runtime`: qué componentes se
 * extrajeron, cuáles no venían en el APK y cuál fue el último error. La UI no
 * puede inventar un "motor listo": si `canLaunch` es `false`, el botón de
 * lanzar no debe prometer nada.
 */
data class RuntimeBinaryUiState(
    val checked: Boolean = false,
    val installing: Boolean = false,
    val version: String = "",
    val components: List<String> = emptyList(),
    val missing: List<String> = emptyList(),
    val canLaunch: Boolean = false,
    val message: String = "Runtime sin comprobar",
    val error: String? = null,
)

/**
 * Estado del puente de entrada táctil.
 *
 * `transport` dice a qué nodo se está escribiendo (`evdev:/dev/input/event3`),
 * y `sentEvents` cuántos eventos `evdev` han salido. Si no hay nodo accesible,
 * `enabled` es `false` y el motivo queda en `lastError`: el HUD sigue
 * funcionando en pantalla, pero no inyecta nada.
 */
data class InputBridgeUiState(
    val enabled: Boolean = false,
    val transport: String = "sin abrir",
    val sentEvents: Int = 0,
    val lastError: String? = null,
)

/**
 * Estado del sumidero de audio real.
 *
 * `state` es el del [com.nexora.android.runtime.audio.AudioPipeline]:
 * `IDLE`, `WAITING` (esperando PCM del motor), `PLAYING`, `STOPPED`, `FAILED`.
 */
data class AudioBridgeUiState(
    val state: String = "IDLE",
    val sampleRateHz: Int = 48_000,
    val bufferFrames: Int = 0,
    val latencyMs: Int = 0,
    val secondsPlayed: Double = 0.0,
    val underruns: Int = 0,
    val error: String? = null,
)
