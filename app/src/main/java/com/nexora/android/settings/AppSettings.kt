package com.nexora.android.settings

/**
 * Configuración de la aplicación.
 *
 * Fase 1: solo el modelo y sus valores por defecto. Todavía no hay
 * persistencia (ni DataStore ni SharedPreferences): los ajustes se leerán de
 * ahí cuando exista la pantalla que los modifique.
 *
 * @property keepScreenOnWhilePlaying Mantener la pantalla encendida durante una
 *   sesión de juego. Es el valor por defecto sensato en un launcher de juegos.
 * @property showDeviceInfoPanel Mostrar la sección "Información del dispositivo".
 * @property preferredResolutionScale Escala de resolución del render, de 0.5 a
 *   1.0. Preparado para cuando exista la capa gráfica; hoy no se usa.
 */
data class AppSettings(
    val keepScreenOnWhilePlaying: Boolean = true,
    val showDeviceInfoPanel: Boolean = true,
    val preferredResolutionScale: Float = 1.0f,
) {
    companion object {
        /** Valores por defecto de la aplicación. */
        val DEFAULT = AppSettings()
    }
}
