package com.nexora.android.runtime.input

import com.nexora.android.engine.input.InputEvent
import com.nexora.android.engine.input.KeyStroke
import com.nexora.android.engine.input.Vec2
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Destino de los eventos ya codificados.
 *
 * Separar el **mapeo** (puro, testeable) del **transporte** (I/O) permite
 * probar todos los gestos sin un dispositivo y cambiar de destino sin tocar la
 * lógica: nodo `evdev` hoy, socket o `uinput` mañana.
 */
interface InputTransport {

    /** Envía un lote ya codificado. Devuelve `false` si el destino falló. */
    fun send(events: List<UinputEvent>): Boolean

    /** Cierra el destino. Debe ser idempotente. */
    fun close()

    /** Descripción legible para el diagnóstico. */
    val description: String
}

/**
 * Escribe estructuras `input_event` en un nodo de entrada del kernel.
 *
 * **Por qué no se crea el dispositivo aquí:** crear un `uinput` nuevo exige
 * `ioctl(UI_DEV_SETUP)` + `ioctl(UI_DEV_CREATE)`, y `ioctl` no existe en la
 * API pública de Android sin JNI. Escribir en un nodo **ya existente** sí se
 * puede desde Kotlin, y es exactamente lo que hace `sendevent`.
 *
 * El nodo debe ser accesible por la app. En un dispositivo sin root eso
 * significa que lo haya creado un helper privilegiado (o el propio servidor X
 * de Android, que expone su dispositivo de entrada). Si no es accesible, el
 * transporte lo reporta y el puente queda en modo "sin salida" en vez de
 * lanzar excepciones en cada toque.
 */
class EvdevInputTransport(
    private val devicePath: String,
) : InputTransport {

    private val lock = Any()
    private var stream: FileOutputStream? = null
    private var failed = false

    @Volatile
    var lastError: String? = null
        private set

    override val description: String get() = "evdev:$devicePath"

    /** `true` si el nodo existe y se puede abrir para escritura. */
    fun isAvailable(): Boolean {
        val file = File(devicePath)
        return file.exists() && file.canWrite()
    }

    override fun send(events: List<UinputEvent>): Boolean {
        if (events.isEmpty()) return true
        synchronized(lock) {
            if (failed) return false
            val output = try {
                stream ?: FileOutputStream(devicePath, true).also { stream = it }
            } catch (error: Exception) {
                failed = true
                lastError = "No se pudo abrir $devicePath: ${error.message}"
                return false
            }
            return try {
                for (event in events) {
                    output.write(event.encode())
                }
                output.flush()
                true
            } catch (error: Exception) {
                failed = true
                lastError = "Error escribiendo en $devicePath: ${error.message}"
                false
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            runCatching { stream?.close() }
            stream = null
        }
    }

    companion object {

        /**
         * Busca un nodo de entrada escribible.
         *
         * Se prueban primero los nodos `event*` ya creados (escribir en ellos
         * inyecta el evento, igual que `sendevent`) y después `/dev/uinput`,
         * que solo sirve si un helper ya creó el dispositivo virtual.
         *
         * Devuelve `null` si no hay ninguno: en un Android sin root es lo
         * normal, y el puente lo reporta en vez de fallar en cada toque.
         */
        fun findWritableNode(): String? {
            val events = File("/dev/input")
                .listFiles { file -> file.name.startsWith("event") }
                ?.sortedBy { it.name }
                ?.filter { it.canWrite() }
                .orEmpty()
            if (events.isNotEmpty()) return events.first().absolutePath

            val uinput = File("/dev/uinput")
            return if (uinput.exists() && uinput.canWrite()) uinput.absolutePath else null
        }
    }
}

/**
 * Transporte en memoria: guarda lo enviado para inspeccionarlo en pruebas y
 * para el registro de gestos de la UI.
 */
class RecordingInputTransport(
    private val capacity: Int = 512,
) : InputTransport {

    private val buffer = CopyOnWriteArrayList<UinputEvent>()
    private val counter = AtomicInteger(0)

    override val description: String get() = "memoria"

    override fun send(events: List<UinputEvent>): Boolean {
        buffer += events
        counter.addAndGet(events.size)
        while (buffer.size > capacity) {
            buffer.removeAt(0)
        }
        return true
    }

    override fun close() = Unit

    /** Eventos registrados, en orden. */
    fun events(): List<UinputEvent> = buffer.toList()

    /** Número total de eventos enviados desde el inicio. */
    fun total(): Int = counter.get()
}

/**
 * Puente entre los gestos de la Fase 5 y el sistema de entrada del dispositivo.
 *
 * Convierte cada [InputEvent] en la secuencia `evdev` equivalente:
 *
 * | Gesto | Secuencia |
 * |---|---|
 * | `CursorMove` | `ABS_X` + `ABS_Y` + `SYN_REPORT` |
 * | `PrimaryClick` | mover + `BTN_LEFT 1` + SYN + `BTN_LEFT 0` + SYN |
 * | `SecondaryClick` | mover + `BTN_RIGHT 1` + SYN + `BTN_RIGHT 0` + SYN |
 * | `SelectionRect` | pulsar, recorrer el rectángulo en 8 pasos, soltar |
 * | `CameraZoom` | `REL_WHEEL` ±1 por muesca (máx. 3) |
 * | `CameraPan` | desplazamiento relativo del absoluto anterior |
 * | `KeyAction` | `EV_KEY` de cada tecla, en orden, y SYN |
 *
 * Es **puro en el mapeo** (no abre nada): el envío lo hace el
 * [InputTransport]. Por eso se prueba en JVM verificando la secuencia exacta.
 */
class InputBridge(
    var mapping: ViewportMapping,
    private val transport: InputTransport,
    var enabled: Boolean = true,
) {

    private var lastX = Uinput.ABS_MAX_VALUE / 2
    private var lastY = Uinput.ABS_MAX_VALUE / 2

    @Volatile
    var sentEvents: Int = 0
        private set

    @Volatile
    var lastError: String? = null
        private set

    @Volatile
    var lastRejection: String? = null
        private set

    /** Envía el evento. Devuelve `false` si está desactivado o el destino falló. */
    fun dispatch(event: InputEvent): Boolean {
        if (!enabled) {
            lastRejection = "Puente desactivado"
            return false
        }
        if (!mapping.isValid) {
            lastRejection = "Sin superficie de juego válida"
            return false
        }
        val events = encode(event)
        if (events.isEmpty()) {
            lastRejection = "Evento sin traducción: ${event.describe()}"
            return false
        }
        val ok = transport.send(events)
        if (ok) {
            sentEvents += events.size
            lastRejection = null
        } else {
            lastError = "El transporte rechazó ${event.describe()}"
        }
        return ok
    }

    /** Traduce sin enviar: es lo que verifican las pruebas. */
    fun encode(event: InputEvent): List<UinputEvent> = when (event) {
        is InputEvent.CursorMove -> moveTo(event.position)

        is InputEvent.PrimaryClick -> moveTo(event.position) +
            key(Uinput.BTN_LEFT, true) + Uinput.sync() +
            key(Uinput.BTN_LEFT, false) + Uinput.sync()

        is InputEvent.SecondaryClick -> moveTo(event.position) +
            key(Uinput.BTN_RIGHT, true) + Uinput.sync() +
            key(Uinput.BTN_RIGHT, false) + Uinput.sync()

        is InputEvent.SelectionRect -> dragBox(event.start, event.end)

        is InputEvent.CameraZoom -> {
            val notches = mapping.zoomToWheelNotches(event.factor)
            buildList {
                repeat(kotlin.math.abs(notches)) {
                    add(UinputEvent(Uinput.EV_REL, Uinput.REL_WHEEL, if (notches > 0) 1 else -1))
                    add(Uinput.sync())
                }
            }
        }

        is InputEvent.CameraPan -> moveBy(event.delta)

        is InputEvent.KeyAction -> keyStroke(event.stroke, event.isPressed)
    }

    /** Reancla el cursor sin enviar nada (al abrir una sesión nueva). */
    fun resetCursor() {
        lastX = Uinput.ABS_MAX_VALUE / 2
        lastY = Uinput.ABS_MAX_VALUE / 2
    }

    private fun moveTo(point: Vec2): List<UinputEvent> {
        val (x, y) = mapping.toAbsolute(point)
        lastX = x
        lastY = y
        return listOf(
            UinputEvent(Uinput.EV_ABS, Uinput.ABS_X, x),
            UinputEvent(Uinput.EV_ABS, Uinput.ABS_Y, y),
            Uinput.sync(),
        )
    }

    private fun moveBy(delta: Vec2): List<UinputEvent> {
        val (dx, dy) = mapping.deltaToAbsolute(delta)
        lastX = (lastX + dx).coerceIn(0, Uinput.ABS_MAX_VALUE)
        lastY = (lastY + dy).coerceIn(0, Uinput.ABS_MAX_VALUE)
        return listOf(
            UinputEvent(Uinput.EV_ABS, Uinput.ABS_X, lastX),
            UinputEvent(Uinput.EV_ABS, Uinput.ABS_Y, lastY),
            Uinput.sync(),
        )
    }

    /**
     * Recuadro de selección: pulsar, arrastrar y soltar.
     *
     * Se interpolan [STEPS] posiciones intermedias porque un solo salto del
     * origen al destino no dibuja la caja: el juego recibe un clic y un
     * movimiento, no un arrastre. Con pasos, cualquier motor de RTS reconoce el
     * gesto.
     */
    private fun dragBox(start: Vec2, end: Vec2): List<UinputEvent> {
        val events = mutableListOf<UinputEvent>()
        events += moveTo(start)
        events += key(Uinput.BTN_LEFT, true)
        events += Uinput.sync()
        for (step in 1..STEPS) {
            val t = step.toFloat() / STEPS
            val point = Vec2(
                start.x + (end.x - start.x) * t,
                start.y + (end.y - start.y) * t,
            )
            events += moveTo(point)
        }
        events += key(Uinput.BTN_LEFT, false)
        events += Uinput.sync()
        return events
    }

    /**
     * Pulsación de teclas.
     *
     * Al **pulsar** se bajan las teclas en orden (`Ctrl` antes que `1`); al
     * **soltar** se levantan en orden inverso, que es como lo hace un teclado
     * real y como lo espera un juego que mira el estado de las modificadoras.
     */
    private fun keyStroke(stroke: KeyStroke, pressed: Boolean): List<UinputEvent> {
        val keys = if (pressed) stroke.keys else stroke.keys.reversed()
        val events = mutableListOf<UinputEvent>()
        for (virtualKey in keys) {
            val linuxCode = KeyCodeMapper.toLinux(virtualKey.keyCode)
            if (linuxCode == null) {
                lastRejection = "Tecla sin equivalente evdev: ${virtualKey.label}"
                continue
            }
            events += key(linuxCode, pressed)
        }
        if (events.isNotEmpty()) events += Uinput.sync()
        return events
    }

    private fun key(code: Int, pressed: Boolean) = UinputEvent(
        type = Uinput.EV_KEY,
        code = code,
        value = if (pressed) Uinput.KEY_PRESS else Uinput.KEY_RELEASE,
    )

    companion object {
        /** Pasos intermedios del recuadro: suficientes para un arrastre real. */
        const val STEPS = 8
    }
}
