package com.nexora.android.runtime.overlay

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Órdenes que el HUD flotante manda a la app. */
sealed interface OverlayCommand {

    /**
     * "Cerrar juego ya".
     *
     * Lo dispara el botón de emergencia cuando la superficie no responde. El
     * servicio no puede matar el proceso por su cuenta (el `Process` vive en
     * el `RuntimeViewModel`), así que publica la orden y es el ViewModel quien
     * ejecuta `kill()`. Si no hay ViewModel escuchando —sesión ya cerrada— la
     * orden simplemente no tiene efecto, y el HUD lo dice.
     */
    data object EmergencyKill : OverlayCommand

    /** El usuario cerró el HUD desde su propia ventana. */
    data object HudDismissed : OverlayCommand
}

/**
 * Canal en proceso entre el HUD flotante y el motor.
 *
 * El `OverlayService` y el `RuntimeViewModel` viven en el **mismo proceso**,
 * así que un singleton con dos `Flow` es suficiente: no hace falta
 * `BroadcastReceiver`, ni `bindService`, ni AIDL. Si algún día el servicio se
 * mueve a otro proceso, esta clase es el único punto que habría que cambiar.
 */
object OverlayMetricsBus {

    private val mutableEngine = MutableStateFlow(EngineSample.IDLE)

    /** Lo que el motor publica sobre sí mismo. */
    val engine: StateFlow<EngineSample> = mutableEngine.asStateFlow()

    private val mutableCommands = MutableSharedFlow<OverlayCommand>(
        replay = 0,
        extraBufferCapacity = 8,
    )

    /** Órdenes del HUD hacia la app. */
    val commands: SharedFlow<OverlayCommand> = mutableCommands.asSharedFlow()

    /** Publica una cifra de FPS leída del motor. */
    fun publishEngineFps(fps: Int?, nowMs: Long = System.currentTimeMillis()) {
        mutableEngine.update { current ->
            current.copy(
                fps = fps,
                active = true,
                updatedAtMs = nowMs,
            )
        }
    }

    /** Marca el arranque de una sesión con su `pid`. */
    fun publishSessionStarted(pid: Long, nowMs: Long = System.currentTimeMillis()) {
        mutableEngine.update {
            EngineSample(fps = null, pid = pid, active = true, updatedAtMs = nowMs)
        }
    }

    /** Marca el final de la sesión: el HUD deja de mostrar FPS del motor. */
    fun publishSessionStopped() {
        mutableEngine.value = EngineSample.IDLE
    }

    /** Envía una orden. Devuelve `false` si nadie la recibió (buffer lleno). */
    fun send(command: OverlayCommand): Boolean = mutableCommands.tryEmit(command)
}
