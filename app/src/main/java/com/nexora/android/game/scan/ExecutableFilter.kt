package com.nexora.android.game.scan

/**
 * Heurísticas para separar, dentro de un listado de `.exe`, el ejecutable de un
 * juego del ruido que lo rodea.
 *
 * Una instalación de PC típica convive con desinstaladores (`unins000.exe`),
 * instaladores (`setup.exe`), redistribuibles (`vcredist_x64.exe`, `dxsetup.exe`,
 * `oalinst.exe`, `physx.exe`) y herramientas auxiliares. Indexar todo eso en la
 * biblioteca la convierte en ilegible, así que el filtrado se hace aquí y no en
 * la UI.
 *
 * El filtrado es **solo por nombre y ruta**: NEXORA no abre ni analiza el
 * contenido de los binarios de Windows en ningún momento.
 */
object ExecutableFilter {

    private const val EXTENSION_EXE = "exe"

    /**
     * Nombres completos que nunca son un juego, comparados en minúsculas.
     *
     * Son los desinstaladores e instaladores más frecuentes de Inno Setup,
     * NSIS, InstallShield y Wise, más los redistribuibles de Microsoft, NVIDIA
     * y DirectX.
     */
    private val IGNORED_EXACT_NAMES: Set<String> = setOf(
        "unins000.exe", "unins001.exe", "unins002.exe", "unins003.exe",
        "uninstall.exe", "uninstaller.exe", "uninst.exe", "unins.exe",
        "unwise.exe", "unwise32.exe", "_iu14del.exe", "st6unst.exe",
        "setup.exe", "install.exe", "installer.exe", "setup32.exe",
        "dxsetup.exe", "dxwebsetup.exe", "dxdiag.exe", "oalinst.exe",
        "vcredist.exe", "vcredist_x86.exe", "vcredist_x64.exe",
        "vc_redist.x86.exe", "vc_redist.x64.exe",
        "dotnetfx.exe", "dotnetfx35.exe", "dotnetfx40.exe",
        "dotnetinstaller.exe", "ndp48-x86-x64-allos-enu.exe",
        "physx.exe", "physxinstaller.exe", "physxsystemsoftware.exe",
        "gfwlivesetup.exe", "xnafx40_redist.exe", "xnaframeworkredist.exe",
        "steamclean.exe", "steamerrorreporter.exe", "steamwebhelper.exe",
        "crashhandler.exe", "crashhandler64.exe", "crashreporter.exe",
        "uninstalltool.exe", "patch.exe", "updater.exe", "update.exe",
    )

    /** Prefijos que delatan un desinstalador, instalador o redistribuible. */
    private val IGNORED_PREFIXES: List<String> = listOf(
        "unins", "uninst", "unwise", "_iu", "uninstall",
        "setup", "install", "redist", "vcredist", "dotnet", "dotnetfx",
        "dxsetup", "dxwebsetup", "oalinst", "physx", "xnafx",
        "gfwlivesetup", "crashhandler", "crashreporter", "steamclean",
        "steamerrorreporter", "steamwebhelper", "uninstal",
    )

    /** Fragmentos que delatan una utilidad auxiliar en vez del juego. */
    private val TOOL_FRAGMENTS: List<String> = listOf(
        "unins", "uninst", "setup", "install", "uninstall", "redist",
        "vcredist", "dotnet", "crash", "reporter", "handler", "helper",
        "service", "server", "activat", "registr", "keygen", "crack",
        "dxsetup", "dxwebsetup", "oalinst", "physx", "benchmark",
        "config", "settings", "patch", "update", "updater", "editor",
        "sdk", "tool", "launcherconfig", "settingseditor",
    )

    /** Fragmentos que refuerzan la idea de que sí es el ejecutable del juego. */
    private val GAME_FRAGMENTS: List<String> = listOf(
        "shipping", "retail", "release", "final", "win64", "win32", "x64",
    )

    /**
     * Directorios que no se descienden.
     *
     * Los redistribuibles y las carpetas de soporte concentran miles de
     * ejecutables que nunca son el juego; descender por ellas consume el
     * presupuesto de escaneo sin aportar nada.
     */
    private val IGNORED_DIRECTORIES: Set<String> = setOf(
        "__redist", "_commonredist", "commonredist", "redist", "redistributables",
        "directx", "dx9", "dx11", "dx12", "physx", "vcredist", "dotnet",
        "prerequisites", "support", "installer", "installers", "uninstall",
        "uninstaller", "setup", "crashdumps", "logs", "log", "cache", "temp",
        "tmp", "android", "\$recycle.bin", "system volume information",
        ".git", "__pycache__", "node_modules", "build", "bin", "obj",
    )

    /** Sufijos técnicos que se recortan al componer el título mostrado. */
    private val TITLE_SUFFIX_PATTERN: Regex =
        Regex("(?i)(?:[\\s._-]*(?:win32|win64|x86|x64|shipping|retail|final|release|debug))+\$")

    private val NON_ALPHANUMERIC: Regex = Regex("[^a-z0-9]")

    /** `true` si el nombre de archivo corresponde a un ejecutable de Windows. */
    fun isExecutable(fileName: String): Boolean =
        fileName.substringAfterLast('.', missingDelimiterValue = "")
            .equals(EXTENSION_EXE, ignoreCase = true)

    /** `true` si el ejecutable es un desinstalador, instalador o herramienta. */
    fun isIgnoredExecutable(fileName: String): Boolean {
        val lower = fileName.lowercase()
        if (lower in IGNORED_EXACT_NAMES) return true
        if (IGNORED_PREFIXES.any { lower.startsWith(it) }) return true
        val stem = lower.substringBeforeLast('.')
        return TOOL_FRAGMENTS.any { stem.contains(it) }
    }

    /** `true` si el directorio no debe descenderse durante el escaneo. */
    fun isIgnoredDirectory(directoryName: String): Boolean =
        directoryName.lowercase() in IGNORED_DIRECTORIES

    /**
     * Puntúa un ejecutable candidato: a mayor puntuación, más probable es que
     * sea el ejecutable principal del juego.
     *
     * La señal más fuerte es que el nombre del archivo contenga el nombre de su
     * carpeta de instalación (`.../HalfLife2/hl2.exe`). Después influyen los
     * marcadores de build de los motores comerciales (`-Win64-Shipping` es la
     * convención de Unreal Engine) y el tamaño: un binario de 4 GB es el juego,
     * uno de 200 KB es una utilidad.
     */
    fun score(fileName: String, parentDirectoryName: String?, sizeBytes: Long): Int {
        val stem = normalize(fileName.substringBeforeLast('.'))
        var value = 0

        val parent = parentDirectoryName?.let(::normalize).orEmpty()
        if (parent.isNotEmpty() && (stem.contains(parent) || parent.contains(stem))) {
            value += 120
        }
        for (fragment in GAME_FRAGMENTS) {
            if (stem.contains(fragment)) value += 25
        }
        value += (kotlin.math.log10(sizeBytes.coerceAtLeast(1L).toDouble()) * 8).toInt()
        for (fragment in TOOL_FRAGMENTS) {
            if (stem.contains(fragment)) value -= 45
        }
        return value
    }

    /**
     * Compone un título legible a partir del nombre del archivo.
     *
     * `HollowKnight-Win64-Shipping.exe` -> `Hollow Knight`
     */
    fun deriveTitle(fileName: String): String {
        var title = fileName.substringBeforeLast('.')
        var previous: String
        do {
            previous = title
            title = title.replace(TITLE_SUFFIX_PATTERN, "")
        } while (title != previous && title.isNotBlank())

        title = title
            .replace(Regex("[._]+"), " ")
            .replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return title.ifBlank { fileName.substringBeforeLast('.') }
    }

    /** Normaliza para comparar: minúsculas y solo caracteres alfanuméricos. */
    private fun normalize(value: String): String =
        NON_ALPHANUMERIC.replace(value.lowercase(), "")
}
