package com.nexora.android.runtime.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {

    private val screenWidth = 1080
    private val screenHeight = 2400
    private val hudWidth = 300
    private val hudHeight = 200

    @Test
    fun `coloca el HUD arriba a la derecha por defecto`() {
        val bounds = OverlayGeometry.initial(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            width = hudWidth,
            height = hudHeight,
            margin = 12,
            topInsetPx = 60,
        )

        assertEquals(screenWidth - hudWidth - 12, bounds.x)
        assertEquals(72, bounds.y)
    }

    @Test
    fun `sujeta una posicion negativa al margen`() {
        assertEquals(12, OverlayGeometry.clampX(-400, hudWidth, screenWidth))
        assertEquals(12, OverlayGeometry.clampY(-400, hudHeight, screenHeight))
    }

    @Test
    fun `sujeta una posicion que se sale por la derecha`() {
        val maxX = screenWidth - hudWidth - 12
        assertEquals(maxX, OverlayGeometry.clampX(9_999, hudWidth, screenWidth))
    }

    @Test
    fun `un HUD mas ancho que la pantalla se ancla al margen izquierdo`() {
        assertEquals(12, OverlayGeometry.clampX(50, 2_000, screenWidth))
    }

    @Test
    fun `arrastrar mueve la ventana y respeta los bordes`() {
        val start = HudBounds(x = 100, y = 100, width = hudWidth, height = hudHeight)

        val moved = OverlayGeometry.move(start, dx = 50f, dy = 30f, screenWidth, screenHeight)
        assertEquals(150, moved.x)
        assertEquals(130, moved.y)

        val clamped = OverlayGeometry.move(start, dx = -900f, dy = 0f, screenWidth, screenHeight)
        assertEquals(12, clamped.x)
    }

    @Test
    fun `el arrastre trunca los deltas en vez de redondear`() {
        val start = HudBounds(x = 100, y = 100, width = hudWidth, height = hudHeight)
        val moved = OverlayGeometry.move(start, dx = 0.9f, dy = 0.9f, screenWidth, screenHeight)
        assertEquals(100, moved.x)
        assertEquals(100, moved.y)
    }

    @Test
    fun `snapToEdge pega a la izquierda si esta en la mitad izquierda`() {
        val bounds = HudBounds(x = 40, y = 300, width = hudWidth, height = hudHeight)
        assertEquals(12, OverlayGeometry.snapToEdge(bounds, screenWidth).x)
    }

    @Test
    fun `snapToEdge pega a la derecha si esta en la mitad derecha`() {
        val bounds = HudBounds(x = 700, y = 300, width = hudWidth, height = hudHeight)
        assertEquals(screenWidth - hudWidth - 12, OverlayGeometry.snapToEdge(bounds, screenWidth).x)
    }

    @Test
    fun `next alterna entre compacto y completo`() {
        assertEquals(HudMode.EXPANDED, HudMode.COMPACT.next())
        assertEquals(HudMode.COMPACT, HudMode.EXPANDED.next())
        assertTrue(HudMode.EXPANDED.widthDp > HudMode.COMPACT.widthDp)
    }
}
