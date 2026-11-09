package com.nexora.android.runtime.engine

import com.nexora.android.runtime.binary.BinaryComponent
import com.nexora.android.runtime.binary.RuntimeLayout

/**
 * Construye la línea de comandos real que arranca un juego.
 *
 * Es Kotlin puro: recibe el [RuntimeLayout] ya instalado y decide **qué
 * binario** se ejecuta y **con qué argumentos**. Nada de esto toca el disco ni
 * lanza procesos, así que se prueba en JVM.
 *
 * La decisión importante es el traductor: en un dispositivo ARM64 los
 * ejecutables de Windows son x86_64 y necesitan Box64 por delante; en un
 * x86_64 nativo, Box64 sobra y solo estorba (añade una capa de traducción
 * innecesaria). El plan lo deja explícito para que la UI lo muestre.
 */
object WineCommandBuilder {

    /**
     * Plan de ejecución.
     *
     * @property command Línea completa, con rutas absolutas.
     * @property usingTranslator `true` si va Box64 por delante de Wine.
     * @property winePath Binario de Wine elegido (`wine64` o `wine`).
     * @property notes Avisos para el visor (traductor ausente, etc.).
     */
    data class Plan(
        val command: List<String>,
        val usingTranslator: Boolean,
        val winePath: String,
        val notes: List<String>,
    )

    /**
     * Arma el comando para [exePath].
     *
     * @param abis Arquitecturas soportadas por el dispositivo, en orden de
     *   preferencia (`Build.SUPPORTED_ABIS`).
     * @throws BinaryNotInstalledException si no hay Wine instalado.
     */
    fun build(
        layout: RuntimeLayout,
        exePath: String,
        abis: List<String>,
        extraArgs: List<String> = emptyList(),
    ): Plan {
        val wine = layout.binary(BinaryComponent.WINE)
            ?: throw BinaryNotInstalledException("wine64")

        val notes = mutableListOf<String>()
        val wantsTranslator = abis.any { it.contains("arm", ignoreCase = true) }
        val box64 = layout.binary(BinaryComponent.BOX64)

        val usingTranslator = wantsTranslator && box64 != null
        if (wantsTranslator && box64 == null) {
            notes += "Dispositivo ARM sin Box64 instalado: Wine solo podrá ejecutar " +
                "binarios de la misma arquitectura."
        }
        if (!wantsTranslator) {
            notes += "Dispositivo x86_64: no se usa traductor, se ejecuta Wine nativo."
        }

        val command = buildList {
            if (usingTranslator) add(box64!!)
            add(wine)
            add(exePath)
            addAll(extraArgs)
        }

        return Plan(
            command = command,
            usingTranslator = usingTranslator,
            winePath = wine,
            notes = notes,
        )
    }

    /**
     * Comando de inicialización del prefijo (`wineboot --init`).
     *
     * Se ejecuta **antes** del juego, una vez por prefijo: sin esto, el primer
     * arranque de un `WINEPREFIX` vacío falla porque no hay registro ni
     * `drive_c` completo.
     */
    fun bootstrap(layout: RuntimeLayout): List<String>? {
        val wineboot = layout.binary(BinaryComponent.TOOLS) ?: return null
        return listOf(wineboot)
    }

    /** `winetricks` con los verbos pedidos, si la utilidad está instalada. */
    fun winetricks(layout: RuntimeLayout, verbs: List<String>): List<String>? {
        if (verbs.isEmpty()) return null
        val winetricks = layout.binary(BinaryComponent.TOOLS) ?: return null
        return listOf(winetricks) + verbs
    }
}
