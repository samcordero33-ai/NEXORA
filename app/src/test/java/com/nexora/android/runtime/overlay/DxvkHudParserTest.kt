package com.nexora.android.runtime.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DxvkHudParserTest {

    @Test
    fun `lee el formato FPS con dos puntos`() {
        assertEquals(58, DxvkHudParser.parseFps("FPS: 58"))
        assertEquals(58, DxvkHudParser.parseFps("fps: 58.3"))
        assertEquals(60, DxvkHudParser.parseFps("FPS=60"))
    }

    @Test
    fun `lee el formato con la cifra delante`() {
        assertEquals(58, DxvkHudParser.parseFps("58 fps"))
        assertEquals(30, DxvkHudParser.parseFps("30.4 FPS"))
    }

    @Test
    fun `lee el formato framerate`() {
        assertEquals(72, DxvkHudParser.parseFps("Framerate 72"))
    }

    @Test
    fun `devuelve null en lineas sin fps`() {
        assertNull(DxvkHudParser.parseFps("fixme:heap:HeapSetInformation"))
        assertNull(DxvkHudParser.parseFps(""))
        assertNull(DxvkHudParser.parseFps("   "))
    }

    @Test
    fun `descarta cifras fuera de rango`() {
        assertNull(DxvkHudParser.parseFps("FPS: 0"))
        assertNull(DxvkHudParser.parseFps("FPS: 5000"))
    }

    @Test
    fun `reconoce lineas del hud`() {
        assertEquals(true, DxvkHudParser.isHudLine("DXVK: 1.10.3"))
        assertEquals(true, DxvkHudParser.isHudLine("GPU: 72%"))
        assertEquals(false, DxvkHudParser.isHudLine("wine: created the configuration directory"))
    }
}
