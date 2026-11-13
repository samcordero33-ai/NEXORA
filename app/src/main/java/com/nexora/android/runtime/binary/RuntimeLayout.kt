package com.nexora.android.runtime.binary

import java.io.File

/**
 * Disposición del runtime ya instalado en el dispositivo.
 *
 * Es el **único** objeto que el motor necesita para saber dónde está cada
 * cosa: binarios, bibliotecas y prefijos. Kotlin puro, sin Android, para que
 * la lógica de lanzamiento se pruebe en JVM.
 *
 * @property rootPath Directorio raíz de la instalación (`filesDir/runtime`).
 * @property version Versión del manifiesto que se instaló.
 * @property binaries Ruta absoluta del ejecutable principal de cada
 *   componente. Un componente ausente simplemente no aparece.
 */
data class RuntimeLayout(
    val rootPath: String,
    val version: String,
    val binaries: Map<BinaryComponent, String>,
) {

    /** Carpeta de ejecutables; es lo primero del `PATH` del proceso hijo. */
    val binDir: String get() = "$rootPath/bin"

    /** Carpeta de bibliotecas; va a `LD_LIBRARY_PATH`. */
    val libDir: String get() = "$rootPath/lib"

    /** Prefijos de Wine de cada juego (Fase 6 los crea). */
    val prefixesDir: String get() = "$rootPath/prefixes"

    /** Ruta del binario, o `null` si el componente no está instalado. */
    fun binary(component: BinaryComponent): String? = binaries[component]

    fun has(component: BinaryComponent): Boolean = binaries.containsKey(component)

    /** `true` si hay con qué arrancar algo (Wine, con o sin Box64). */
    val canLaunch: Boolean get() = has(BinaryComponent.WINE)

    /**
     * `true` si hace falta el traductor: en un dispositivo ARM64 los binarios
     * de Windows son x86_64 y hay que traducirlos; en un x86_64 nativo no.
     */
    fun needsTranslator(abis: List<String>): Boolean =
        abis.any { it.contains("arm", ignoreCase = true) } && has(BinaryComponent.BOX64)

    /** Componentes presentes, para la UI. */
    val installedComponents: List<BinaryComponent>
        get() = BinaryComponent.entries.filter { binaries.containsKey(it) }

    /** Componentes del manifiesto que no llegaron a instalarse. */
    fun missingComponents(expected: Collection<BinaryComponent>): List<BinaryComponent> =
        expected.filter { !binaries.containsKey(it) }

    companion object {

        /** Layout de un directorio sin nada instalado. */
        fun empty(rootPath: String, version: String = RuntimeManifest.CURRENT_VERSION): RuntimeLayout =
            RuntimeLayout(rootPath = rootPath, version = version, binaries = emptyMap())

        /**
         * Reconstruye el layout leyendo el disco: solo declara un componente si
         * su binario existe y es ejecutable. Así, borrar un archivo a mano
         * degrada a "no instalado" en vez de fallar al lanzar.
         */
        fun fromDisk(
            root: File,
            version: String,
            entries: List<BinaryEntry>,
        ): RuntimeLayout {
            val binaries = linkedMapOf<BinaryComponent, String>()
            for (entry in entries) {
                if (binaries.containsKey(entry.component)) continue
                val file = File(root, entry.primaryBinaryPath())
                if (!file.isFile) continue
                if (entry.executable && !file.canExecute()) continue
                val isPrimary = entry.archive || entry.component.primaryBinary == entry.fileName
                if (entry.executable || isPrimary) {
                    binaries[entry.component] = file.absolutePath
                }
            }
            return RuntimeLayout(
                rootPath = root.absolutePath,
                version = version,
                binaries = binaries,
            )
        }
    }
}
