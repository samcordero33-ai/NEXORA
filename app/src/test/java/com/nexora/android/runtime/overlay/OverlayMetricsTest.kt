package com.nexora.android.runtime.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayMetricsTest {

    private val device = DeviceSample(
        fps = 42,
        frameTimeMs = 23.8,
        ramUsedBytes = 3_000L * 1024L * 1024L,
        ramTotalBytes = 8_000L * 1024L * 1024L,
        cpuPercent = 35,
        cpuTempCelsius = 52,
        gpuTempCelsius = 55,
    )

    @Test
    fun `usa los fps del motor cuando son frescos`() {
        val metrics = OverlayComposer.compose(
            engine = EngineSample(fps = 60, pid = 1234, active = true, updatedAtMs = 1_000L),
            device = device,
            nowMs = 2_000L,
            elapsedMs = 5_000L,
        )

        assertEquals(60, metrics.fps)
        assertEquals(FpsSource.ENGINE, metrics.fpsSource)
        assertEquals(1234L, metrics.enginePid)
        assertEquals(true, metrics.sessionActive)
    }

    @Test
    fun `descarta los fps del motor si estan viejos`() {
        val metrics = OverlayComposer.compose(
            engine = EngineSample(fps = 60, pid = 1234, active = true, updatedAtMs = 1_000L),
            device = device,
            nowMs = 10_000L,
            elapsedMs = 5_000L,
            engineStaleMs = 3_000L,
        )

        assertEquals(42, metrics.fps)
        assertEquals(FpsSource.OVERLAY, metrics.fpsSource)
    }

    @Test
    fun `sin medidas la fuente es NONE y la etiqueta es un guion`() {
        val metrics = OverlayComposer.compose(
            engine = EngineSample.IDLE,
            device = DeviceSample.EMPTY,
            nowMs = 0L,
            elapsedMs = 0L,
        )

        assertEquals(FpsSource.NONE, metrics.fpsSource)
        assertEquals("—", metrics.fpsLabel)
        assertEquals(OverlayTone.NEUTRAL, metrics.fpsTone)
    }

    @Test
    fun `calcula el porcentaje de ram y su semaforo`() {
        val metrics = OverlayComposer.compose(
            engine = EngineSample.IDLE,
            device = device,
            nowMs = 0L,
            elapsedMs = 0L,
        )

        assertEquals(37, metrics.ramPercent)
        assertEquals(OverlayTone.GOOD, metrics.ramTone)
        assertEquals("37% RAM · 35% CPU", metrics.summaryLabel)
    }

    @Test
    fun `los umbrales marcan aviso y peligro`() {
        assertEquals(OverlayTone.GOOD, OverlayThresholds.fpsTone(60, FpsSource.ENGINE))
        assertEquals(OverlayTone.WARN, OverlayThresholds.fpsTone(40, FpsSource.ENGINE))
        assertEquals(OverlayTone.BAD, OverlayThresholds.fpsTone(15, FpsSource.ENGINE))
        assertEquals(OverlayTone.WARN, OverlayThresholds.ramTone(80))
        assertEquals(OverlayTone.BAD, OverlayThresholds.ramTone(95))
        assertEquals(OverlayTone.BAD, OverlayThresholds.tempTone(90))
    }

    @Test
    fun `formatea bytes y duraciones`() {
        assertEquals("0 B", OverlayFormat.bytes(0L))
        assertEquals("512 B", OverlayFormat.bytes(512L))
        assertEquals("1.0 KB", OverlayFormat.bytes(1_024L))
        assertEquals("1.5 GB", OverlayFormat.bytes((1.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("00:00", OverlayFormat.duration(0L))
        assertEquals("01:05", OverlayFormat.duration(65_000L))
        assertEquals("1:00:00", OverlayFormat.duration(3_600_000L))
    }

    @Test
    fun `la etiqueta de sesion distingue pid activo y sin sesion`() {
        val withPid = OverlayMetrics(sessionActive = true, enginePid = 77L)
        assertEquals("PID 77", withPid.sessionLabel)

        val active = OverlayMetrics(sessionActive = true)
        assertEquals("SESIÓN ACTIVA", active.sessionLabel)

        val idle = OverlayMetrics()
        assertEquals("SIN SESIÓN", idle.sessionLabel)
    }
}
