package com.nexora.android.settings

/**
 * Configuración de la aplicación.
 *
 * Fase 1: solo el modelo y sus valores por defecto.
 * Fase 2: se añaden los topes del escáner de almacenamiento. Todavía no hay
 * persistencia (ni DataStore ni SharedPreferences): los ajustes se leerán de ahí
 * cuando exista la pantalla que los modifique.
 *
 * @property keepScreenOnWhilePlaying Mantener la pantalla encendida durante una
 *   sesión de juego. Es el valor por defecto sensato en un launcher de juegos.
 * @property showDeviceInfoPanel Mostrar la sección "Información del dispositivo".
 * @property preferredResolutionScale Escala de resolución del render, de 0.5 a
 *   1.0. Preparado para cuando exista la capa gráfica; hoy no se usa.
 * @property scanMaxDepth Profundidad máxima que desciende el escáner.
 * @property scanEntryBudget Presupuesto de entradas (archivos + carpetas) por
 *   escaneo. Un valor alto encuentra más juegos en microSD grandes a cambio de
 *   tardar más.
 * @property detectOnlyLikelyExecutables Descartar desinstaladores, instaladores
 *   y redistribuibles (`unins000.exe`, `setup.exe`, `vcredist_x64.exe`...).
 *   Está activado por defecto: sin él, la biblioteca se llena de ruido.
 */
data class AppSettings(
    val keepScreenOnWhilePlaying: Boolean = true,
    val showDeviceInfoPanel: Boolean = true,
    val preferredResolutionScale: Float = 1.0f,
    val scanMaxDepth: Int = 8,
    val scanEntryBudget: Int = 60_000,
    val detectOnlyLikelyExecutables: Boolean = true,
) {
    companion object {
        /** Valores por defecto de la aplicación. */
        val DEFAULT = AppSettings()
    }
}
