package com.nexora.android.engine.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del motor de gestos.
 *
 * Son JVM puras: el intérprete no importa Android ni Compose, así que se puede
 * comprobar gesto a gesto sin emulador. Cada prueba reproduce una secuencia de
 * punteros y comprueba exactamente qué eventos salen.
 */
class TouchGestureInterpreterTest {

    private val config = GestureConfig(
        touchSlopPx = 10f,
        longPressMs = 400L,
        tapMaxMs = 300L,
        dragBoxEnabled = true,
        zoomSlopPx = 15f,
        panSlopPx = 12f,
    )

    private fun interpreter(dragBoxEnabled: Boolean = true) =
        TouchGestureInterpreter(config.copy(dragBoxEnabled = dragBoxEnabled))

    @Test
    fun `un toque corto mueve el cursor y hace clic izquierdo`() {
        val interpreter = interpreter()

        val down = interpreter.onPointerDown(1L, Vec2(100f, 100f), 0L)
        val up = interpreter.onPointerUp(1L, Vec2(102f, 101f), 120L)

        assertEquals(1, down.size)
        assertTrue(down.first() is InputEvent.CursorMove)
        assertEquals(
            listOf(
                InputEvent.CursorMove(Vec2(102f, 101f)),
                InputEvent.PrimaryClick(Vec2(102f, 101f)),
            ),
            up,
        )
    }

    @Test
    fun `arrastrar con un dedo dibuja el recuadro y lo cierra al soltar`() {
        val interpreter = interpreter()

        interpreter.onPointerDown(1L, Vec2(50f, 50f), 0L)
        interpreter.onPointerMove(1L, Vec2(200f, 180f), 80L)

        val box = interpreter.selectionBox
        assertEquals(InputEvent.SelectionRect(Vec2(50f, 50f), Vec2(200f, 180f)), box)

        val up = interpreter.onPointerUp(1L, Vec2(240f, 220f), 140L)
        assertEquals(1, up.size)
        assertTrue(up.first() is InputEvent.SelectionRect)
        assertNull(interpreter.selectionBox)
    }

    @Test
    fun `sin recuadro el arrastre solo mueve el cursor`() {
        val interpreter = interpreter(dragBoxEnabled = false)

        interpreter.onPointerDown(1L, Vec2(50f, 50f), 0L)
        val moves = interpreter.onPointerMove(1L, Vec2(200f, 180f), 80L)

        assertNull(interpreter.selectionBox)
        assertEquals(listOf(InputEvent.CursorMove(Vec2(200f, 180f))), moves)

        val up = interpreter.onPointerUp(1L, Vec2(210f, 190f), 120L)
        assertTrue(up.last() is InputEvent.PrimaryClick)
    }

    @Test
    fun `una pulsacion larga con un dedo hace clic derecho`() {
        val interpreter = interpreter()

        interpreter.onPointerDown(1L, Vec2(80f, 90f), 0L)
        val events = interpreter.onPointerMove(1L, Vec2(82f, 91f), 500L)

        assertTrue(events.any { it is InputEvent.SecondaryClick })

        // Al soltar no debe añadir además un clic izquierdo.
        val up = interpreter.onPointerUp(1L, Vec2(82f, 91f), 520L)
        assertTrue(up.none { it is InputEvent.PrimaryClick })
    }

    @Test
    fun `un toque con dos dedos hace clic derecho en el centro`() {
        val interpreter = interpreter()

        interpreter.onPointerDown(1L, Vec2(100f, 100f), 0L)
        interpreter.onPointerDown(2L, Vec2(200f, 100f), 10L)

        val up = interpreter.onPointerUp(2L, Vec2(200f, 100f), 120L)

        assertTrue(up.any { it is InputEvent.SecondaryClick })
        val click = up.filterIsInstance<InputEvent.SecondaryClick>().first()
        assertEquals(Vec2(150f, 100f), click.position)
    }

    @Test
    fun `pellizcar produce zoom y cancela el clic derecho`() {
        val interpreter = interpreter()

        interpreter.onPointerDown(1L, Vec2(100f, 100f), 0L)
        interpreter.onPointerDown(2L, Vec2(200f, 100f), 10L)
        val zoom = interpreter.onPointerMove(2L, Vec2(260f, 100f), 60L)

        assertTrue(zoom.any { it is InputEvent.CameraZoom })
        assertTrue((zoom.filterIsInstance<InputEvent.CameraZoom>().first()).factor > 1f)

        val up = interpreter.onPointerUp(2L, Vec2(260f, 100f), 120L)
        assertTrue(up.none { it is InputEvent.SecondaryClick })
    }

    @Test
    fun `dos dedos en paralelo desplazan la camara`() {
        val interpreter = interpreter()

        interpreter.onPointerDown(1L, Vec2(100f, 100f), 0L)
        interpreter.onPointerDown(2L, Vec2(200f, 100f), 10L)
        val pan = interpreter.onPointerMove(1L, Vec2(120f, 100f), 40L)
            .plus(interpreter.onPointerMove(2L, Vec2(220f, 100f), 40L))

        assertTrue(pan.any { it is InputEvent.CameraPan })
        assertTrue(interpreter.onPointerUp(1L, Vec2(220f, 100f), 90L).isEmpty())
    }

    @Test
    fun `un tercer dedo se ignora y no rompe el gesto`() {
        val interpreter = interpreter()

        interpreter.onPointerDown(1L, Vec2(100f, 100f), 0L)
        interpreter.onPointerDown(2L, Vec2(200f, 100f), 10L)
        val third = interpreter.onPointerDown(3L, Vec2(300f, 300f), 20L)

        assertTrue(third.isEmpty())
        assertEquals(2, interpreter.trackedPointerCount)
    }

    @Test
    fun `cancelar limpia el recuadro sin emitir eventos`() {
        val interpreter = interpreter()

        interpreter.onPointerDown(1L, Vec2(50f, 50f), 0L)
        interpreter.onPointerMove(1L, Vec2(200f, 200f), 60L)
        interpreter.onCancel()

        assertNull(interpreter.selectionBox)
        assertTrue(interpreter.onPointerUp(1L, Vec2(200f, 200f), 80L).isEmpty())
    }

    @Test
    fun `levantar un dedo de dos no dispara un clic al levantar el otro`() {
        val interpreter = interpreter()

        interpreter.onPointerDown(1L, Vec2(100f, 100f), 0L)
        interpreter.onPointerDown(2L, Vec2(200f, 100f), 10L)
        val firstUp = interpreter.onPointerUp(1L, Vec2(100f, 100f), 90L)
        val secondUp = interpreter.onPointerUp(2L, Vec2(200f, 100f), 140L)

        assertTrue(firstUp.any { it is InputEvent.SecondaryClick })
        assertTrue(secondUp.isEmpty())
    }

    @Test
    fun `el recuadro no se dibuja si el dedo solo tiembla`() {
        val interpreter = interpreter()

        interpreter.onPointerDown(1L, Vec2(100f, 100f), 0L)
        interpreter.onPointerMove(1L, Vec2(104f, 103f), 40L)

        assertNull(interpreter.selectionBox)
    }
}
