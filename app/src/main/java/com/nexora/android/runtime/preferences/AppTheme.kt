package com.nexora.android.runtime.preferences

/**
 * Tema de la aplicación.
 *
 * - [DARK] fuerza el tema oscuro (recomendado en sesiones largas de juego).
 * - [LIGHT] fuerza el tema claro (útil en exteriores, pantallas muy reflectantes).
 * - [SYSTEM] sigue la preferencia del sistema.
 */
enum class AppTheme(val wireValue: String, val label: String) {
    DARK("dark", "Oscuro"),
    LIGHT("light", "Claro"),
    SYSTEM("system", "Sigue el sistema");

    companion object {
        val DEFAULT: AppTheme = DARK

        fun fromWireValue(value: String?): AppTheme = when (value) {
            DARK.wireValue -> DARK
            LIGHT.wireValue -> LIGHT
            SYSTEM.wireValue -> SYSTEM
            else -> DEFAULT
        }
    }
}

/**
 * Preferencias globales inmutables.
 *
 * @property theme Tema visual aplicado por la raíz de Compose.
 * @property animationsEnabled Si `false`, las transiciones de pantalla se
 *   hacen instantáneas (mejor en dispositivos muy antiguos).
 * @property autoExportLogs Si `true`, cuando el motor marca estado `Failed`,
 *   NEXORA exporta automáticamente un `.txt` con el log a la carpeta de
 *   documentos. El usuario también puede hacerlo a mano.
 * @property defaultBackend Backend de audio por defecto que se usará cuando
 *   un título no tenga override propio (suele ser `pulse`).
 */
data class GlobalPreferences(
    val theme: AppTheme = AppTheme.DEFAULT,
    val animationsEnabled: Boolean = true,
    val autoExportLogs: Boolean = false,
    val defaultBackend: String = "pulse",
) {
    companion object {
        val DEFAULT: GlobalPreferences = GlobalPreferences()
    }

    /** Devuelve una copia normalizada: por ahora identidad, pero deja sitio
     *  para futuras reglas (p. ej. forzar dark si `theme` está corrupto).
     */
    fun normalized(): GlobalPreferences = this
}