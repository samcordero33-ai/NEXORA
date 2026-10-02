package com.nexora.android.engine

/**
 * Contrato del runtime de NEXORA.
 *
 * FASE 1: deliberadamente **sin implementación**. Aquí no hay Wine, Box64, DXVK
 * ni ningún motor de traducción: todavía no se integra nada de eso. Esta
 * interfaz solo fija la forma que tendrán las integraciones futuras para que la
 * UI y el resto del código no dependan de una tecnología concreta.
 *
 * Cuando llegue el momento, cada backend (traducción de syscalls, capa gráfica,
 * empaquetado de prefijos) implementará esta interfaz y se registrará en la
 * capa de inyección de dependencias.
 */
interface RuntimeEngine {

    /** Nombre legible del backend, para la pantalla de información. */
    val displayName: String

    /** Estado actual del backend. */
    val status: EngineStatus

    /**
     * Prepara el entorno de ejecución (prefijo, variables, librerías).
     *
     * @return `true` si el entorno queda listo para [launch].
     */
    suspend fun prepare(): Boolean

    /**
     * Lanza un ejecutable dentro del runtime.
     *
     * @param executablePath Ruta del ejecutable dentro del prefijo.
     * @param arguments Argumentos que se pasan al ejecutable.
     */
    suspend fun launch(executablePath: String, arguments: List<String> = emptyList()): LaunchResult
}

/**
 * Estado de un backend del runtime.
 *
 * [Unsupported] existe porque NEXORA corre en dispositivos muy distintos: si el
 * hardware o el sistema no cumplen los requisitos, hay que decirlo en la UI en
 * vez de fallar en silencio al arrancar un juego.
 */
sealed interface EngineStatus {
    /** El backend aún no está integrado en esta versión. */
    data object NotIntegrated : EngineStatus

    /** Integrado y listo. */
    data object Ready : EngineStatus

    /** Integrado pero requiere descarga o configuración adicional. */
    data class NeedsSetup(val reason: String) : EngineStatus

    /** El dispositivo no puede ejecutar este backend. */
    data class Unsupported(val reason: String) : EngineStatus
}

/** Resultado de un intento de lanzamiento. */
sealed interface LaunchResult {
    data object Success : LaunchResult
    data class Failure(val reason: String) : LaunchResult
}
