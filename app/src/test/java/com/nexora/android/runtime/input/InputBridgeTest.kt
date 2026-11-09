package com.nexora.android.runtime.input

import com.nexora.android.engine.input.InputEvent
import com.nexora.android.engine.input.KeyStroke
import com.nexora.android.engine.input.Vec2
import com.nexora.android.engine.input.VirtualKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UinputEventTest {

    @Test
    fun `la estructura input_event mide 24 bytes`() {
        val encoded = UinputEvent(Uinput.EV_KEY, Uinput.BTN_LEFT, 1).encode()

        assertEquals(24, encoded.size)
        assertEquals(24, UinputEvent.SIZE_BYTES)
    }

    @Test
    fun `codifica type code y value en little endian`() {
        val encoded = UinputEvent(Uinput.EV_ABS, Uinput.ABS_X, 1234).encode()

        // timeval a cero: los primeros 16 bytes
        assertTrue(encoded.take(16).all { it == 0.toByte() })
        assertEquals(0x03, encoded[16].toInt())
        assertEquals(0x00, encoded[17].toInt())
        assertEquals(0x00, encoded[18].toInt())
        assertEquals(0x00, encoded[19].toInt())
        // 1234 = 0x000004D2
        assertEquals(0xD2.toByte(), encoded[20])
        assertEquals(0x04.toByte(), encoded[21])
        assertEquals(0x00.toByte(), encoded[22])
        assertEquals(0x00.toByte(), encoded[23])
    }

    @Test
    fun `las teclas del HUD tienen equivalente evdev`() {
        assertEquals(29, KeyCodeMapper.toLinux(VirtualKey.CONTROL.keyCode))
        assertEquals(57, KeyCodeMapper.toLinux(VirtualKey.SPACE.keyCode))
        assertEquals(2, KeyCodeMapper.toLinux(VirtualKey.DIGIT_1.keyCode))
        assertEquals(30, KeyCodeMapper.toLinux(VirtualKey.KEY_A.keyCode))
    }
}

class ViewportMappingTest {

    private val mapping = ViewportMapping(
        surfaceWidth = 1080f,
        surfaceHeight = 2400f,
        desktopWidth = 1280,
        desktopHeight = 720,
    )

    @Test
    fun `el centro de la pantalla cae en el centro del escritorio`() {
        val desktop = mapping.toDesktop(Vec2(540f, 1200f))

        assertEquals(640f, desktop.x, 0.01f)
        assertEquals(360f, desktop.y, 0.01f)
    }

    @Test
    fun `el eje absoluto va de 0 a 32767`() {
        assertEquals(0 to 0, mapping.toAbsolute(Vec2(0f, 0f)))
        assertEquals(Uinput.ABS_MAX_VALUE to Uinput.ABS_MAX_VALUE, mapping.toAbsolute(Vec2(1080f, 2400f)))
    }

    @Test
    fun `un punto fuera de la superficie se recorta al borde`() {
        val (x, y) = mapping.toAbsolute(Vec2(5000f, -300f))

        assertEquals(Uinput.ABS_MAX_VALUE, x)
        assertEquals(0, y)
    }

    @Test
    fun `el zoom se convierte en muescas limitadas`() {
        assertEquals(0, mapping.zoomToWheelNotches(1.01f))
        assertEquals(2, mapping.zoomToWheelNotches(1.2f))
        assertEquals(-2, mapping.zoomToWheelNotches(0.8f))
        assertEquals(3, mapping.zoomToWheelNotches(3f))
    }

    @Test
    fun `un mapeo invalido no produce coordenadas`() {
        val invalid = ViewportMapping(0f, 0f, 1280, 720)

        assertFalse(invalid.isValid)
        assertEquals(0 to 0, invalid.toAbsolute(Vec2(10f, 10f)))
    }
}

class InputBridgeTest {

    private val mapping = ViewportMapping(1000f, 1000f, 1000, 1000)
    private lateinit var transport: RecordingInputTransport
    private lateinit var bridge: InputBridge

    private fun newBridge(enabled: Boolean = true): InputBridge {
        transport = RecordingInputTransport()
        bridge = InputBridge(mapping, transport, enabled)
        return bridge
    }

    @Test
    fun `mover el cursor emite dos ejes y un SYN`() {
        val events = newBridge().encode(InputEvent.CursorMove(Vec2(500f, 500f)))

        assertEquals(3, events.size)
        assertEquals(Uinput.EV_ABS, events[0].type)
        assertEquals(Uinput.ABS_X, events[0].code)
        assertEquals(16384, events[0].value)
        assertEquals(Uinput.ABS_Y, events[1].code)
        assertEquals(Uinput.EV_SYN, events[2].type)
    }

    @Test
    fun `un clic izquierdo baja y sube el boton`() {
        val events = newBridge().encode(InputEvent.PrimaryClick(Vec2(100f, 100f)))

        assertEquals(7, events.size)
        assertEquals(Uinput.BTN_LEFT, events[3].code)
        assertEquals(Uinput.KEY_PRESS, events[3].value)
        assertEquals(Uinput.BTN_LEFT, events[5].code)
        assertEquals(Uinput.KEY_RELEASE, events[5].value)
    }

    @Test
    fun `el clic derecho usa BTN_RIGHT`() {
        val events = newBridge().encode(InputEvent.SecondaryClick(Vec2(10f, 10f)))

        assertTrue(events.any { it.code == Uinput.BTN_RIGHT && it.value == Uinput.KEY_PRESS })
    }

    @Test
    fun `el recuadro pulsa arrastra en pasos y suelta`() {
        val events = newBridge().encode(
            InputEvent.SelectionRect(Vec2(100f, 100f), Vec2(900f, 900f)),
        )

        // mover (3) + pulsar (1) + SYN (1) + 8 pasos * 3 + soltar (1) + SYN (1)
        assertEquals(3 + 1 + 1 + InputBridge.STEPS * 3 + 1 + 1, events.size)
        assertTrue(events.any { it.code == Uinput.BTN_LEFT && it.value == Uinput.KEY_PRESS })
        assertTrue(events.any { it.code == Uinput.BTN_LEFT && it.value == Uinput.KEY_RELEASE })
        assertEquals(Uinput.EV_SYN, events.last().type)
    }

    @Test
    fun `el zoom emite muescas de rueda`() {
        val events = newBridge().encode(InputEvent.CameraZoom(Vec2(500f, 500f), 1.2f))

        assertEquals(4, events.size) // 2 muescas * (evento + SYN)
        assertEquals(Uinput.EV_REL, events[0].type)
        assertEquals(Uinput.REL_WHEEL, events[0].code)
        assertEquals(1, events[0].value)
    }

    @Test
    fun `el paneo mueve el cursor de forma relativa`() {
        val events = newBridge().encode(InputEvent.CameraPan(Vec2(100f, -100f)))

        assertEquals(3, events.size)
        // parte del centro (16384) y se desplaza un 10 % del eje
        assertTrue(events[0].value > 16384)
        assertTrue(events[1].value < 16384)
    }

    @Test
    fun `una tecla emite EV_KEY y SYN`() {
        val events = newBridge().encode(
            InputEvent.KeyAction(KeyStroke(VirtualKey.CONTROL, VirtualKey.DIGIT_1), true),
        )

        assertEquals(3, events.size)
        assertEquals(Uinput.EV_KEY, events[0].type)
        assertEquals(29, events[0].code)
        assertEquals(2, events[1].code)
        assertEquals(Uinput.EV_SYN, events[2].type)
    }

    @Test
    fun `al soltar se invierte el orden de las teclas`() {
        val events = newBridge().encode(
            InputEvent.KeyAction(KeyStroke(VirtualKey.CONTROL, VirtualKey.DIGIT_1), false),
        )

        assertEquals(2, events[0].code)
        assertEquals(29, events[1].code)
        assertEquals(Uinput.KEY_RELEASE, events[0].value)
    }

    @Test
    fun `el puente desactivado no envia nada`() {
        val bridge = newBridge(enabled = false)

        assertFalse(bridge.dispatch(InputEvent.CursorMove(Vec2(1f, 1f))))
        assertEquals(0, transport.total())
    }

    @Test
    fun `dispatch envia y cuenta los eventos`() {
        val bridge = newBridge()

        assertTrue(bridge.dispatch(InputEvent.CursorMove(Vec2(1f, 1f))))
        assertEquals(3, bridge.sentEvents)
        assertEquals(3, transport.total())
    }

    @Test
    fun `un mapeo sin superficie rechaza el evento`() {
        transport = RecordingInputTransport()
        val bridge = InputBridge(ViewportMapping(0f, 0f, 0, 0), transport)

        assertFalse(bridge.dispatch(InputEvent.CursorMove(Vec2(1f, 1f))))
        assertEquals("Sin superficie de juego válida", bridge.lastRejection)
    }
}
