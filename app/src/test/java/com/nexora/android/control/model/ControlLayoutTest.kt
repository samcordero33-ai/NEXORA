package com.nexora.android.control.model

import com.nexora.android.control.data.local.ControlButtonEntity
import com.nexora.android.control.data.local.ControlProfileEntity
import com.nexora.android.control.data.local.layoutFrom
import com.nexora.android.control.data.local.toEntities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas del modelo de la disposición táctil y de su paso por base de datos.
 *
 * Kotlin puro: las entidades no necesitan el código generado por Room para
 * construirse, así que el mapeo se prueba igual que el modelo.
 */
class ControlLayoutTest {

    @Test
    fun `la disposicion por defecto trae todas las acciones`() {
        val layout = ControlLayout.defaults("game")

        assertEquals(ControlAction.ALL.size, layout.controls.size)
        assertEquals(ControlAction.ALL, layout.controls.map { it.action })
        assertTrue(layout.controlFor(ControlAction.DPAD)?.visible == true)
        assertFalse(layout.controlFor(ControlAction.JOYSTICK)?.visible == true)
    }

    @Test
    fun `las coordenadas y el tamano se recortan a limites utilizables`() {
        val control = HudControl(
            action = ControlAction.SHIFT,
            centerX = 1.8f,
            centerY = -0.4f,
            size = 2f,
        ).clamped()

        assertEquals(1f, control.centerX)
        assertEquals(0f, control.centerY)
        assertEquals(ControlLayout.MAX_SIZE, control.size)
    }

    @Test
    fun `cambiar un boton conserva el orden de la lista`() {
        val layout = ControlLayout.defaults("game")
        val moved = layout.controlFor(ControlAction.ATTACK_MOVE)!!.copy(centerX = 0.5f)

        val updated = layout.withControl(moved)

        assertEquals(ControlAction.ALL, updated.controls.map { it.action })
        assertEquals(0.5f, updated.controlFor(ControlAction.ATTACK_MOVE)!!.centerX)
        assertEquals(layout.controls.size, updated.controls.size)
    }

    @Test
    fun `una accion desconocida en la base de datos se descarta sin romper la lectura`() {
        val layout = layoutFrom(
            gameId = "game",
            profile = null,
            buttons = listOf(
                ControlButtonEntity(
                    gameId = "game",
                    actionId = "group_1",
                    centerX = 0.4f,
                    centerY = 0.6f,
                    size = 0.2f,
                    visible = false,
                ),
                ControlButtonEntity(
                    gameId = "game",
                    actionId = "no_existe",
                    centerX = 0f,
                    centerY = 0f,
                    size = 0.1f,
                    visible = true,
                ),
            ),
        )

        assertEquals(ControlAction.ALL.size, layout.controls.size)
        assertEquals(0.4f, layout.controlFor(ControlAction.CONTROL_GROUP_1)!!.centerX)
        assertFalse(layout.controlFor(ControlAction.CONTROL_GROUP_1)!!.visible)
        assertEquals(
            ControlAction.SHIFT.defaultCenterX,
            layout.controlFor(ControlAction.SHIFT)!!.centerX,
        )
    }

    @Test
    fun `guardar y leer devuelve la misma disposicion`() {
        val original = ControlLayout.defaults("game")
            .withControl(
                HudControl(
                    action = ControlAction.CONTROL_GROUP_2,
                    centerX = 0.31f,
                    centerY = 0.72f,
                    size = 0.18f,
                    visible = false,
                ),
            )
            .copy(overlayOpacity = 0.4f, dragBoxEnabled = false)

        val (profile, buttons) = original.toEntities(updatedAt = 1_700_000_000_000L)
        val restored = layoutFrom(
            gameId = "game",
            profile = profile,
            buttons = buttons,
        )

        assertEquals(original.controls, restored.controls)
        assertEquals(0.4f, restored.overlayOpacity)
        assertFalse(restored.dragBoxEnabled)
        assertEquals(1_700_000_000_000L, restored.updatedAtMillis)
    }

    @Test
    fun `un juego sin perfil guardado usa la transparencia por defecto`() {
        val layout = layoutFrom(
            gameId = "game",
            profile = null,
            buttons = emptyList(),
        )

        assertEquals(ControlLayout.DEFAULT_OPACITY, layout.overlayOpacity)
        assertTrue(layout.dragBoxEnabled)
    }

    @Test
    fun `el perfil guardado manda sobre los valores por defecto`() {
        val layout = layoutFrom(
            gameId = "game",
            profile = ControlProfileEntity(
                gameId = "game",
                overlayOpacity = 0.25f,
                dragBoxEnabled = false,
                updatedAt = 42L,
            ),
            buttons = emptyList(),
        )

        assertEquals(0.25f, layout.overlayOpacity)
        assertFalse(layout.dragBoxEnabled)
    }

    @Test
    fun `los identificadores de accion son estables y unicos`() {
        val ids = ControlAction.ALL.map { it.id }

        assertEquals(ids.size, ids.distinct().size)
        ControlAction.ALL.forEach { action ->
            assertEquals(action, ControlAction.fromId(action.id))
        }
        assertEquals(null, ControlAction.fromId("inventado"))
        assertEquals(null, ControlAction.fromId(null))
    }
}
