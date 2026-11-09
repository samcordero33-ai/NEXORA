package com.nexora.android.runtime.input

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Un evento del subsistema `evdev` de Linux, tal y como lo consume el kernel.
 *
 * Se envía escribiendo la estructura `struct input_event` en un nodo de
 * entrada (`/dev/input/eventN`) o en `/dev/uinput`. En Android de 64 bits esa
 * estructura mide **24 bytes**:
 *
 * ```
 * offset  tamaño  campo
 * 0       8       timeval.tv_sec     (el kernel lo rellena; se envía 0)
 * 8       8       timeval.tv_usec
 * 16      2       type   (u16)
 * 18      2       code   (u16)
 * 20      4       value  (s32)
 * ```
 *
 * No hace falta JNI: escribir los bytes es exactamente lo que hace `sendevent`.
 * Crear el dispositivo (`UI_DEV_SETUP` + `UI_DEV_CREATE`) sí requiere `ioctl`,
 * y eso queda fuera de la app a propósito (ver [EvdevInputTransport]).
 */
data class UinputEvent(
    val type: Int,
    val code: Int,
    val value: Int,
) {

    /** Serializa la estructura completa, en orden de bytes del dispositivo. */
    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putLong(0L)          // tv_sec
        buffer.putLong(0L)          // tv_usec
        buffer.putShort(type.toShort())
        buffer.putShort(code.toShort())
        buffer.putInt(value)
        return buffer.array()
    }

    companion object {
        const val SIZE_BYTES = 24
    }
}

/**
 * Códigos `evdev` que usa el puente.
 *
 * Los valores son los del kernel (`linux/input-event-codes.h`), no los de
 * Win32: el HUD habla en teclas virtuales de Windows y [KeyCodeMapper] hace la
 * conversión una sola vez.
 */
object Uinput {

    const val EV_SYN = 0x00
    const val EV_KEY = 0x01
    const val EV_REL = 0x02
    const val EV_ABS = 0x03

    const val SYN_REPORT = 0x00

    const val ABS_X = 0x00
    const val ABS_Y = 0x01
    const val ABS_MAX_VALUE = 32767

    const val REL_WHEEL = 0x08

    const val BTN_LEFT = 0x110
    const val BTN_RIGHT = 0x111
    const val BTN_MIDDLE = 0x112

    const val KEY_PRESS = 1
    const val KEY_RELEASE = 0x00
    const val KEY_REPEAT = 0x02

    /** Cierra un paquete de eventos; sin él, el kernel no los procesa. */
    fun sync(): UinputEvent = UinputEvent(EV_SYN, SYN_REPORT, 0)
}

/**
 * Convierte un código de tecla virtual de Win32 (el que ya usa el HUD de la
 * Fase 5) al `KEY_*` de Linux.
 *
 * La tabla cubre solo las teclas que el HUD puede emitir. Una tecla
 * desconocida devuelve `null` y se descarta con un aviso, en vez de enviar un
 * código inventado que el juego interpretaría como otra cosa.
 */
object KeyCodeMapper {

    private val table: Map<Int, Int> = mapOf(
        0x11 to 29,   // Ctrl  -> KEY_LEFTCTRL
        0x10 to 42,   // Shift -> KEY_LEFTSHIFT
        0x12 to 56,   // Alt   -> KEY_LEFTALT
        0x20 to 57,   // Space
        0x0D to 28,   // Enter
        0x1B to 1,    // Esc
        0x31 to 2,    // 1
        0x32 to 3,    // 2
        0x33 to 4,    // 3
        0x41 to 30,   // A
        0x53 to 31,   // S
        0x44 to 32,   // D
        0x57 to 17,   // W
        0x26 to 103,  // Arrow up
        0x28 to 108,  // Arrow down
        0x25 to 105,  // Arrow left
        0x27 to 106,  // Arrow right
    )

    /** Código Linux, o `null` si el HUD puede emitir una tecla no mapeada. */
    fun toLinux(win32KeyCode: Int): Int? = table[win32KeyCode]

    /** Inversa, útil para el registro de diagnóstico. */
    fun toWin32(linuxKeyCode: Int): Int? =
        table.entries.firstOrNull { it.value == linuxKeyCode }?.key
}
