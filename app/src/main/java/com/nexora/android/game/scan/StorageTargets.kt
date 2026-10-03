package com.nexora.android.game.scan

import android.content.Context
import android.os.Environment
import java.io.File

/** Clase de volumen de almacenamiento detectado. */
enum class StorageKind {
    /** Almacenamiento compartido primario del dispositivo. */
    INTERNAL,

    /** Volumen extraíble: tarjeta microSD o memoria USB. */
    REMOVABLE,

    /** Volumen que no se pudo clasificar. */
    UNKNOWN,
}

/**
 * Directorio candidato a escanear.
 *
 * @property displayName Etiqueta legible para la UI.
 * @property directory Directorio a recorrer.
 * @property kind Volumen al que pertenece.
 */
data class ScanTarget(
    val displayName: String,
    val directory: File,
    val kind: StorageKind,
)

/**
 * Descubre los volúmenes de almacenamiento del dispositivo y las carpetas donde
 * suele instalarse juegos de PC.
 *
 * IMPORTANTE: este objeto **no** necesita permisos para enumerar rutas. Otra
 * cosa es poder leerlas: el acceso real depende de los permisos concedidos y se
 * comprueba antes de escanear (ver `StorageAccessController`).
 */
object StorageTargets {

    /**
     * Nombres de carpeta donde los usuarios suelen dejar juegos de PC.
     *
     * Se incluyen los nombres en español y los que usan los contenedores de
     * Windows habituales en Android (Winlator, ExaGear, Mobox, GameHub), porque
     * son la procedencia realista de los ejecutables que NEXORA va a indexar.
     */
    private val COMMON_GAME_FOLDER_NAMES: List<String> = listOf(
        "Games", "PC Games", "Juegos", "GameHub",
        "Download", "Downloads", "Descargas",
        "Documents", "Documentos",
        "ROMs", "Winlator", "ExaGear", "Mobox", "Termux",
    )

    /** Marcador que separa la raíz del volumen del directorio propio de la app. */
    private const val APP_SPECIFIC_MARKER = "/Android/data/"

    /**
     * Raíz del almacenamiento compartido primario.
     *
     * `Environment.getExternalStorageDirectory()` está deprecado desde API 29,
     * pero sigue siendo la única forma pública de obtener la ruta raíz del
     * volumen compartido; las alternativas (MediaStore, SAF) no exponen rutas.
     */
    @Suppress("DEPRECATION")
    fun primaryRoot(): File? =
        runCatching { Environment.getExternalStorageDirectory() }.getOrNull()

    /**
     * Raíces de los volúmenes extraíbles (microSD).
     *
     * `Context.getExternalFilesDirs()` devuelve un directorio propio de la app
     * en cada volumen montado. Recortando el sufijo `Android/data/<paquete>/...`
     * se obtiene la raíz del volumen, que es lo que interesa recorrer. Este
     * método evita reflection sobre `StorageVolume`, que no es API pública.
     */
    fun removableRoots(context: Context): List<File> {
        val primary = primaryRoot()?.absolutePath?.trimEnd('/')
        return context.getExternalFilesDirs(null)
            .filterNotNull()
            .mapNotNull { directory ->
                val path = directory.absolutePath
                val marker = path.indexOf(APP_SPECIFIC_MARKER)
                if (marker > 0) path.substring(0, marker) else null
            }
            .filter { it != primary }
            .distinct()
            .map(::File)
    }

    /**
     * Carpetas donde es razonable buscar juegos, en el almacenamiento interno y
     * en la microSD.
     *
     * Si en un volumen no existe ninguna de las carpetas habituales, se devuelve
     * la raíz del volumen: es mejor escanear la raíz con presupuesto acotado que
     * no encontrar nada por haber adivinado mal el nombre de la carpeta.
     */
    fun commonGameDirectories(context: Context): List<ScanTarget> {
        val targets = LinkedHashMap<String, ScanTarget>()

        primaryRoot()?.let { root ->
            addCommonDirectories(targets, root, StorageKind.INTERNAL)
        }
        removableRoots(context).forEach { root ->
            addCommonDirectories(targets, root, StorageKind.REMOVABLE)
        }

        return targets.values.toList()
    }

    /**
     * Volúmenes montados, como objetivos de escaneo sobre su raíz.
     *
     * Útil para un escaneo profundo; la UI de la Fase 2 usa
     * [commonGameDirectories], que es bastante más rápido.
     */
    fun storageRoots(context: Context): List<ScanTarget> {
        val targets = LinkedHashMap<String, ScanTarget>()
        primaryRoot()?.let { root ->
            targets[root.absolutePath] = ScanTarget("Almacenamiento interno", root, StorageKind.INTERNAL)
        }
        removableRoots(context).forEachIndexed { index, root ->
            val label = if (index == 0) "Tarjeta microSD" else "Almacenamiento externo ${index + 1}"
            targets[root.absolutePath] = ScanTarget(label, root, StorageKind.REMOVABLE)
        }
        return targets.values.toList()
    }

    private fun addCommonDirectories(
        sink: LinkedHashMap<String, ScanTarget>,
        root: File,
        kind: StorageKind,
    ) {
        var found = false
        for (folderName in COMMON_GAME_FOLDER_NAMES) {
            val directory = File(root, folderName)
            if (!directory.isDirectory) continue
            found = true
            val path = directory.absolutePath
            sink[path] = ScanTarget("${kind.label()} · $folderName", directory, kind)
        }
        if (!found) {
            val path = root.absolutePath
            sink[path] = ScanTarget(kind.label(), root, kind)
        }
    }

    private fun StorageKind.label(): String = when (this) {
        StorageKind.INTERNAL -> "Almacenamiento interno"
        StorageKind.REMOVABLE -> "Tarjeta microSD"
        StorageKind.UNKNOWN -> "Almacenamiento externo"
    }
}
