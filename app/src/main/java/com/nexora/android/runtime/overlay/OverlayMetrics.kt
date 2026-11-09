package com.nexora.android.runtime.overlay

/** De dónde salen los FPS que se están mostrando. */
enum class FpsSource(val label: String) {
    /** Todavía no hay medida. */
    NONE("sin medida"),

    /** Medidos por el `Choreographer` del propio overlay. */
    OVERLAY("overlay"),

    /** Publicados por el motor (líneas de `DXVK_HUD` en stderr). */
    ENGINE("motor"),
}

/** Semáforo visual del HUD. */
enum class OverlayTone { NEUTRAL, GOOD, WARN, BAD }

/**
 * Lectura del dispositivo en un instante, tal y como la entrega
 * `RuntimeMonitorSource`.
 *
 * Es una copia independiente de `RuntimeMonitorSnapshot` para que la capa de
 * overlay no dependa del monitor de la Fase 6: el servicio flotante puede
 * vivir sin la pantalla de ejecución montada.
 */
data class DeviceSample(
    val fps: Int,
    val frameTimeMs: Double,
    val ramUsedBytes: Long,
    val ramTotalBytes: Long,
    val cpuPercent: Int,
    val cpuTempCelsius: Int,
    val gpuTempCelsius: Int,
) {
    companion object {
        val EMPTY: DeviceSample = DeviceSample(
            fps = 0,
            frameTimeMs = 0.0,
            ramUsedBytes = 0L,
            ramTotalBytes = 0L,
            cpuPercent = 0,
            cpuTempCelsius = 0,
            gpuTempCelsius = 0,
        )
    }
}

/**
 * Lo que el motor de ejecución publica sobre sí mismo.
 *
 * `fps` es `null` cuando el motor no publica cifras (lo normal si
 * `DXVK_HUD` está desactivado o si no hay binarios corriendo). `updatedAtMs`
 * permite descartar una cifra vieja: un juego cerrado no puede seguir
 * marcando 60 FPS.
 */
data class EngineSample(
    val fps: Int? = null,
    val pid: Long = 0L,
    val active: Boolean = false,
    val updatedAtMs: Long = 0L,
) {
    companion object {
        val IDLE: EngineSample = EngineSample()
    }
}

/**
 * Métricas finales que pinta el HUD.
 *
 * Es un dato inmutable: el servicio lo recalcula cada 500 ms y Compose
 * recompone. Nada de estado mutable compartido entre hilos.
 */
data class OverlayMetrics(
    val fps: Int = 0,
    val fpsSource: FpsSource = FpsSource.NONE,
    val frameTimeMs: Double = 0.0,
    val ramUsedBytes: Long = 0L,
    val ramTotalBytes: Long = 0L,
    val cpuPercent: Int = 0,
    val cpuTempCelsius: Int = 0,
    val gpuTempCelsius: Int = 0,
    val enginePid: Long = 0L,
    val sessionActive: Boolean = false,
    val elapsedMs: Long = 0L,
) {

    /** Uso de RAM global, 0–100. `0` si el total es desconocido. */
    val ramPercent: Int
        get() = if (ramTotalBytes <= 0L) {
            0
        } else {
            ((ramUsedBytes * 100L) / ramTotalBytes).toInt().coerceIn(0, 100)
        }

    val fpsTone: OverlayTone get() = OverlayThresholds.fpsTone(fps, fpsSource)
    val ramTone: OverlayTone get() = OverlayThresholds.ramTone(ramPercent)
    val cpuTone: OverlayTone get() = OverlayThresholds.cpuTone(cpuPercent)
    val tempTone: OverlayTone get() = OverlayThresholds.tempTone(cpuTempCelsius)

    /** Texto grande del HUD: los FPS, o `—` si no hay medida. */
    val fpsLabel: String
        get() = if (fpsSource == FpsSource.NONE || fps <= 0) "—" else fps.toString()

    /** Segunda línea del modo compacto. */
    val summaryLabel: String
        get() = "${ramPercent}% RAM · ${cpuPercent}% CPU"

    /** Estado de sesión, en texto corto para el encabezado. */
    val sessionLabel: String
        get() = when {
            sessionActive && enginePid > 0L -> "PID $enginePid"
            sessionActive -> "SESIÓN ACTIVA"
            else -> "SIN SESIÓN"
        }

    val elapsedLabel: String get() = OverlayFormat.duration(elapsedMs)
    val ramLabel: String get() = OverlayFormat.bytes(ramUsedBytes)
}

/**
 * Umbrales del semáforo.
 *
 * Los cortes son deliberadamente simples y explícitos: 50/30 FPS y 75/90 % de
 * RAM. Cualquier número más fino sería precisión inventada — el FPS medido por
 * `Choreographer` depende del compositor del sistema, no solo del juego.
 */
object OverlayThresholds {

    const val FPS_GOOD = 50
    const val FPS_WARN = 30
    const val RAM_WARN_PERCENT = 75
    const val RAM_BAD_PERCENT = 90
    const val CPU_WARN_PERCENT = 80
    const val TEMP_WARN_CELSIUS = 75
    const val TEMP_BAD_CELSIUS = 85

    fun fpsTone(fps: Int, source: FpsSource): OverlayTone = when {
        source == FpsSource.NONE || fps <= 0 -> OverlayTone.NEUTRAL
        fps >= FPS_GOOD -> OverlayTone.GOOD
        fps >= FPS_WARN -> OverlayTone.WARN
        else -> OverlayTone.BAD
    }

    fun ramTone(percent: Int): OverlayTone = when {
        percent >= RAM_BAD_PERCENT -> OverlayTone.BAD
        percent >= RAM_WARN_PERCENT -> OverlayTone.WARN
        percent > 0 -> OverlayTone.GOOD
        else -> OverlayTone.NEUTRAL
    }

    fun cpuTone(percent: Int): OverlayTone = when {
        percent >= CPU_WARN_PERCENT -> OverlayTone.WARN
        percent > 0 -> OverlayTone.GOOD
        else -> OverlayTone.NEUTRAL
    }

    fun tempTone(celsius: Int): OverlayTone = when {
        celsius >= TEMP_BAD_CELSIUS -> OverlayTone.BAD
        celsius >= TEMP_WARN_CELSIUS -> OverlayTone.WARN
        celsius > 0 -> OverlayTone.GOOD
        else -> OverlayTone.NEUTRAL
    }
}

/** Formateo de las cifras del HUD. */
object OverlayFormat {

    /** Bytes a texto legible con una decimal (`1,4 GB`). */
    fun bytes(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = 0
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        return if (index == 0) {
            "${value.toLong()} ${units[index]}"
        } else {
            "%.1f %s".format(value, units[index])
        }
    }

    /** Milisegundos a `mm:ss` (o `h:mm:ss` si pasa de una hora). */
    fun duration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = ms / 1000L
        val seconds = totalSeconds % 60L
        val minutes = (totalSeconds / 60L) % 60L
        val hours = totalSeconds / 3600L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}

/**
 * Mezcla lo que dice el motor con lo que mide el overlay.
 *
 * Regla: **el motor manda si su cifra es fresca**. `DXVK_HUD` publica los FPS
 * reales del render; el `Choreographer` solo ve los frames de la ventana del
 * overlay, que en un juego que corre en otra superficie no son los mismos. Si
 * el motor no publica nada (o lo publicó hace más de [DEFAULT_ENGINE_STALE_MS]),
 * se usa la medida del overlay y se etiqueta como tal. Nunca se inventa un
 * número: si no hay ninguna de las dos, la fuente es [FpsSource.NONE].
 */
object OverlayComposer {

    /** Antigüedad máxima de una cifra del motor para considerarla válida. */
    const val DEFAULT_ENGINE_STALE_MS: Long = 3_000L

    fun compose(
        engine: EngineSample,
        device: DeviceSample,
        nowMs: Long,
        elapsedMs: Long,
        engineStaleMs: Long = DEFAULT_ENGINE_STALE_MS,
    ): OverlayMetrics {
        val engineFps = engine.fps
        val engineFresh = engineFps != null &&
            engineFps > 0 &&
            nowMs - engine.updatedAtMs in 0..engineStaleMs

        val fps = if (engineFresh) engineFps!! else device.fps
        val source = when {
            engineFresh -> FpsSource.ENGINE
            device.fps > 0 -> FpsSource.OVERLAY
            else -> FpsSource.NONE
        }

        return OverlayMetrics(
            fps = fps.coerceAtLeast(0),
            fpsSource = source,
            frameTimeMs = if (engineFresh) {
                if (fps > 0) 1000.0 / fps else 0.0
            } else {
                device.frameTimeMs
            },
            ramUsedBytes = device.ramUsedBytes,
            ramTotalBytes = device.ramTotalBytes,
            cpuPercent = device.cpuPercent,
            cpuTempCelsius = device.cpuTempCelsius,
            gpuTempCelsius = device.gpuTempCelsius,
            enginePid = engine.pid,
            sessionActive = engine.active,
            elapsedMs = elapsedMs.coerceAtLeast(0L),
        )
    }
}
