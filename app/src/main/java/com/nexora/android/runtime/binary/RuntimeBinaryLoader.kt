package com.nexora.android.runtime.binary

import android.content.Context
import java.io.File

/**
 * Instala el runtime en `filesDir/runtime` la primera vez y lo reutiliza
 * después.
 *
 * El sello (`runtime/.manifest.sha256`) evita recorrer y verificar cientos de
 * megabytes en cada arranque: si el manifiesto no ha cambiado, el layout se
 * reconstruye leyendo el disco, que es barato. Si cambia —o si el usuario
 * pulsa "Reinstalar"— se vuelve a extraer lo que falte.
 *
 * No hay `AssetManager` en las pruebas unitarias, así que toda la lógica de
 * decisión vive en [BinaryInstaller] y [RuntimeLayout]; esta clase solo une
 * esas piezas con `Context`.
 */
class RuntimeBinaryLoader(
    private val context: Context,
    private val assetRoot: String = "runtime",
) {

    /** Resultado de asegurar el runtime, con lo necesario para la UI. */
    data class Outcome(
        val layout: RuntimeLayout,
        val manifest: RuntimeManifest,
        val report: InstallReport?,
        val problems: List<String>,
        val message: String,
    )

    private val root: File get() = File(context.filesDir, "runtime")

    /** Sello de la última instalación correcta. */
    private val stampFile: File get() = File(root, ".manifest.sha256")

    /**
     * Asegura que el runtime está extraído.
     *
     * @param force Vuelve a extraer aunque el sello coincida.
     */
    fun ensureInstalled(force: Boolean = false): Outcome {
        val manifestText = readManifest() ?: return missingManifest()
        val parsed = RuntimeManifest.parse(manifestText)
        val stamp = Sha256.of("${parsed.manifest.version}\n$manifestText".toByteArray())

        root.mkdirs()

        val alreadyInstalled = !force &&
            stampFile.isFile &&
            stampFile.readText().trim() == stamp

        val report = if (alreadyInstalled) {
            null
        } else {
            val installer = BinaryInstaller(
                source = AssetBinarySource(context.assets, assetRoot),
                root = root,
            )
            val result = installer.install(parsed.manifest.entries)
            if (result.ok) {
                stampFile.writeText(stamp)
            }
            result
        }

        val layout = RuntimeLayout.fromDisk(
            root = root,
            version = parsed.manifest.version,
            entries = parsed.manifest.entries,
        )

        val message = when {
            report == null -> "Runtime ya instalado (v${layout.version})"
            report.failures.isNotEmpty() ->
                "Instalación incompleta: ${report.failures.first().reason}"
            layout.canLaunch -> "Runtime instalado: ${layout.installedComponents.joinToString { it.label }}"
            else -> "Manifiesto leído, pero no hay binarios empaquetados"
        }

        return Outcome(
            layout = layout,
            manifest = parsed.manifest,
            report = report,
            problems = parsed.problems,
            message = message,
        )
    }

    private fun readManifest(): String? = try {
        context.assets.open("$assetRoot/manifest.txt").bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }

    private fun missingManifest(): Outcome {
        root.mkdirs()
        return Outcome(
            layout = RuntimeLayout.empty(root.absolutePath),
            manifest = RuntimeManifest.EMPTY,
            report = null,
            problems = listOf("No se encontró assets/$assetRoot/manifest.txt"),
            message = "Sin manifiesto de runtime: no hay binarios que instalar",
        )
    }
}

/**
 * [BinarySource] sobre `AssetManager`.
 *
 * `exists` se implementa abriendo el asset y cerrándolo: `AssetManager` no
 * tiene un `exists` fiable para archivos individuales, y `list()` no es
 * recursivo.
 */
class AssetBinarySource(
    private val assets: android.content.res.AssetManager,
    private val assetRoot: String,
) : BinarySource {

    override fun exists(path: String): Boolean = try {
        assets.open("$assetRoot/${path.trimStart('/')}").close()
        true
    } catch (_: Exception) {
        false
    }

    override fun open(path: String): java.io.InputStream =
        assets.open("$assetRoot/${path.trimStart('/')}")
}
