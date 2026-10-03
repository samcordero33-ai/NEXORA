package com.nexora.android.game.scan

import com.nexora.android.game.model.Game

/**
 * Límites del escaneo.
 *
 * Un escaneo sin límites sobre una microSD de 512 GB puede tardar minutos y
 * dejar la UI congelada. Todos los topes existen para que el escaneo sea
 * acotado, cancellable y con progreso honesto.
 *
 * @property maxDepth Profundidad máxima de directorios descendida.
 * @property entryBudget Número máximo de entradas (archivos + carpetas) que se
 *   visitan. Al alcanzarlo el escaneo se detiene y marca el resultado como
 *   truncado, en vez de seguir en silencio.
 * @property maxResults Número máximo de juegos devueltos.
 * @property maxGamesPerFolder Como máximo un ejecutable por carpeta de
 *   instalación: un mismo juego suele convivir con su launcher y su
 *   desinstalador, y eso debe aparecer como una sola entrada.
 * @property progressInterval Entradas visitadas entre dos emisiones de
 *   progreso. Emitir en cada archivo saturaría la recomposición.
 * @property filterNoise Descartar desinstaladores, instaladores y
 *   redistribuibles (`unins000.exe`, `setup.exe`, `vcredist_x64.exe`...).
 */
data class ScanConfig(
    val maxDepth: Int = 8,
    val entryBudget: Int = 60_000,
    val maxResults: Int = 200,
    val maxGamesPerFolder: Int = 1,
    val progressInterval: Int = 40,
    val filterNoise: Boolean = true,
) {
    companion object {
        /** Configuración por defecto. */
        val DEFAULT = ScanConfig()
    }
}

/**
 * Progreso de un escaneo en curso.
 *
 * @property currentPath Última ruta visitada; se muestra para que el usuario
 *   vea que el escaneo avanza y dónde está.
 * @property exhausted `true` cuando se agotó el presupuesto de entradas.
 */
data class ScanProgress(
    val directoriesScanned: Long = 0L,
    val filesScanned: Long = 0L,
    val executablesFound: Long = 0L,
    val currentPath: String = "",
    val budget: Int = ScanConfig.DEFAULT.entryBudget,
    val exhausted: Boolean = false,
) {
    /**
     * Fracción completada sobre el presupuesto de entradas, en [0, 1].
     *
     * No es el porcentaje real del árbol —no se conoce de antemano— sino la
     * fracción del presupuesto consumida. Es un valor determinado y creciente,
     * que es lo que necesita una barra de progreso.
     */
    val fraction: Float
        get() = ((directoriesScanned + filesScanned).toFloat() / budget.toFloat())
            .coerceIn(0f, 1f)

    companion object {
        /** Progreso inicial, antes de visitar ninguna entrada. */
        val EMPTY = ScanProgress()
    }
}

/**
 * Resultado de un escaneo.
 *
 * @property games Juegos detectados, ya filtrados y ordenados por relevancia.
 * @property executablesDismissed Ejecutables descartados por el filtro. Se
 *   reporta para que el usuario sepa que el escaneo vio esos archivos y los
 *   descartó a propósito, en vez de no haberlos encontrado.
 * @property truncated `true` si se alcanzó el presupuesto y puede haber
 *   juegos sin visitar.
 * @property targets Rutas o etiquetas de los orígenes escaneados.
 * @property errors Errores no fatales (carpetas sin permiso de lectura).
 */
data class ScanOutcome(
    val games: List<Game>,
    val directoriesScanned: Long,
    val filesScanned: Long,
    val executablesDismissed: Long,
    val truncated: Boolean,
    val targets: List<String>,
    val errors: List<String>,
)
