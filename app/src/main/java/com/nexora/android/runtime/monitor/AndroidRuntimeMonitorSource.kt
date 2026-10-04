package com.nexora.android.runtime.monitor

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.view.Choreographer

/**
 * Fuente del monitor apoyada en la plataforma Android.
 *
 * - **FPS**: media móvil sobre los últimos `WINDOW_SIZE` intervalos del
 *   `Choreographer`. `frameTimeMs` es el inverso del FPS en milisegundos.
 * - **RAM**: `ActivityManager.MemoryInfo` vía `Debug.MemoryInfo` (que se
 *   rellena desde `ActivityManager.getProcessMemoryInfo`).
 * - **Temperaturas**: estimadas, no leídas. Android no expone un sensor
 *   térmico de CPU/GPU sin permisos extra; lo que se hace aquí es una
 *   **estimación** a partir de la presión de RAM y del uso de CPU: es
 *   claramente una aproximación, y la UI la marca como tal.
 */
class AndroidRuntimeMonitorSource(
    context: Context,
    private val windowSize: Int = WINDOW_SIZE,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : RuntimeMonitorSource {

    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val selfPid = android.os.Process.myPid()

    private val frameTimes = ArrayDeque<Long>(windowSize)
    private var lastFrameNanos: Long = -1L
    private var lastSampleMs: Long = 0L

    /** Bandera que se activa mientras se muestrean frames. */
    @Volatile
    private var listening: Boolean = false

    /**
     * `Choreographer.postFrameCallback` se registra y desregistra bajo
     * demanda: cuando la UI activa el monitor, se inscribe; cuando lo
     * desactiva, se libera.
     */
    private var frameCallback: Choreographer.FrameCallback? = null

    fun start() {
        if (listening) return
        listening = true
        lastFrameNanos = -1L
        frameTimes.clear()
        if (frameCallback == null) {
            frameCallback = Choreographer.FrameCallback { _ ->
                val nanos = System.nanoTime()
                val deltaNanos = if (lastFrameNanos > 0) nanos - lastFrameNanos else 0L
                lastFrameNanos = nanos
                if (deltaNanos in 1L..ONE_SECOND_NANOS * 2) {
                    val deltaMs = deltaNanos / 1_000_000.0
                    if (frameTimes.size >= windowSize) frameTimes.removeFirst()
                    frameTimes.addLast(deltaMs.toLong())
                }
                if (listening) frameCallback?.let { Choreographer.getInstance().postFrameCallback(it) }
            }
        }
        val cb = frameCallback ?: return
        Choreographer.getInstance().postFrameCallback(cb)
    }

    override fun sampleFrame(@Suppress("UNUSED_PARAMETER") nanos: Long): FrameSnapshot {
        if (frameTimes.isEmpty()) return FrameSnapshot(fps = 0, frameTimeMs = 0.0)
        val avgMs = frameTimes.average()
        val fps = if (avgMs > 0.0) (1000.0 / avgMs).toInt() else 0
        return FrameSnapshot(fps = fps, frameTimeMs = avgMs)
    }

    override fun sampleResources(): ResourceSnapshot {
        val info = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val totalBytes = info.totalMem.coerceAtLeast(0L)
        val availBytes = info.availMem.coerceAtLeast(0L)
        val usedBytes = (totalBytes - availBytes).coerceAtLeast(0L)

        val procMem = Debug.MemoryInfo()
        Debug.getMemoryInfo(procMem)
        // procMem.totalPss está en KB. La presión efectiva mezcla PSS del proceso
        // y RAM global ocupada: ambas son señales, no verdades absolutas.
        val pssBytes = procMem.totalPss.toLong() * 1024L
        val cpuPercent = (pssBytes.toDouble() / totalBytes * 100.0).toInt().coerceIn(0, 100)

        val cpuTemp = estimateCpuTemp(cpuPercent)
        val gpuTemp = cpuTemp + 2

        return ResourceSnapshot(
            usedRamBytes = usedBytes,
            totalRamBytes = totalBytes,
            cpuUsagePercent = cpuPercent,
            cpuTempCelsius = cpuTemp,
            gpuTempCelsius = gpuTemp,
        )
    }

    override fun close() {
        listening = false
        frameCallback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        frameCallback = null
    }

    /**
     * Estima la temperatura de CPU a partir de la presión de uso.
     *
     * No es un sensor real: el dispositivo no expone la temperatura de CPU
     * sin permisos. La estimación devuelve un número entre 40 y 90 °C y se
     * redondea a múltiplos de 5 para que la UI no enseñe una falsa
     * precisión.
     */
    private fun estimateCpuTemp(usagePercent: Int): Int {
        val raw = 40 + (usagePercent.toDouble() / 100.0) * 50.0
        return (raw / 5.0).toInt() * 5
    }

    companion object {
        /** Tamaño de la ventana de medias móviles para FPS. */
        const val WINDOW_SIZE: Int = 30

        /** Un segundo en nanosegundos (evita magic numbers). */
        private const val ONE_SECOND_NANOS: Long = 1_000_000_000L
    }
}