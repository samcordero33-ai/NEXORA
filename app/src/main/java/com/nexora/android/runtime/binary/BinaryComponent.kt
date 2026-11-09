package com.nexora.android.runtime.binary

/**
 * Componentes nativos que NEXORA sabe instalar.
 *
 * Cada componente tiene un identificador estable (el que aparece en el
 * manifiesto de `assets/runtime/manifest.txt`), la carpeta donde se espera su
 * carga útil y el nombre del ejecutable principal, si lo tiene.
 *
 * El identificador es **contrato de datos**: se escribe en el manifiesto y en
 * el sello de instalación, así que no se renombra sin migrar.
 */
enum class BinaryComponent(
    val id: String,
    val label: String,
    val primaryBinary: String? = null,
) {
    /** Traductor x86_64 → ARM64. Opcional si el dispositivo ya es x86_64. */
    BOX64("box64", "Box64 (traductor x86_64)", "box64"),

    /** Contenedor Wine. Sin él no hay nada que ejecutar. */
    WINE("wine", "Wine (contenedor Windows)", "wine64"),

    /** DLLs de DXVK: `d3d9`, `d3d11`, `dxgi`… */
    DXVK("dxvk", "DXVK (Direct3D sobre Vulkan)"),

    /** Driver Vulkan Turnip para Adreno. */
    TURNIP("turnip", "Mesa Turnip (Vulkan Adreno)"),

    /** Utilidades del prefijo: `wineboot`, `wineserver`, `winetricks`. */
    TOOLS("tools", "Utilidades del prefijo", "wineboot");

    companion object {

        /** Resuelve por identificador; `null` si el manifiesto trae basura. */
        fun fromId(id: String): BinaryComponent? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }
}

/**
 * Una entrada del manifiesto: qué archivo se copia, a dónde y con qué permiso.
 *
 * @property component Componente al que pertenece.
 * @property assetPath Ruta dentro de `assets/` (o del directorio de origen).
 * @property targetPath Ruta relativa al directorio de instalación. **Nunca**
 *   absoluta: el instalador rechaza lo que se salga del destino.
 * @property sha256 Huella esperada en hexadecimal. Vacía = no se verifica
 *   (útil para pruebas y para cargas que el usuario aporta a mano).
 * @property executable Si hay que marcar el archivo como ejecutable.
 * @property sizeBytes Tamaño esperado, o `-1` si no se declara.
 */
data class BinaryEntry(
    val component: BinaryComponent,
    val assetPath: String,
    val targetPath: String,
    val sha256: String = "",
    val executable: Boolean = false,
    val sizeBytes: Long = -1L,
) {

    /** `true` si hay una huella declarada que se pueda comparar. */
    val hasChecksum: Boolean get() = sha256.isNotBlank()

    /** Nombre del archivo destino, para mensajes y para el resolver. */
    val fileName: String get() = targetPath.substringAfterLast('/')
}
