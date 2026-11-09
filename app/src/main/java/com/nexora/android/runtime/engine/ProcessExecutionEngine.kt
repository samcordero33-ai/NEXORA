package com.nexora.android.runtime.engine

import com.nexora.android.runtime.binary.BinaryComponent
import com.nexora.android.runtime.binary.RuntimeLayout
import java.util.concurrent.atomic.AtomicReference

/**
 * Orquestador real de procesos: convierte un `ComposedEnvironment` (Fase 6)
 * en un `ProcessBuilder` con binarios instalados, bibliotecas y entorno
 * completo.
 *
 * Implementa [ProcessLauncher], así que encaja **sin cambios** en el
 * [RuntimeEngine] de la Fase 6: el motor sigue siendo la máquina de estados y
 * este es el eslabón que antes era un stub.
 *
 * Flujo de un lanzamiento:
 * 1. Resolver el [RuntimeLayout] instalado. Sin layout no hay binario y se
 *    lanza [BinaryNotInstalledException].
 * 2. Construir la línea de comandos con [WineCommandBuilder] (Box64 delante
 *    solo si el dispositivo es ARM y el traductor está instalado).
 * 3. Completar el entorno con [ExecEnvironment] (`PATH`, `LD_LIBRARY_PATH`,
 *    `HOME`, `TMPDIR`, `PULSE_SERVER`).
 * 4. Delegar en el lanzador interno ([AndroidProcessLauncher] en producción,
 *    un grabador en pruebas).
 *
 * El último plan queda accesible en [lastPlan] para que la UI y el diagnóstico
 * puedan mostrar exactamente qué se ejecutó.
 */
class ProcessExecutionEngine(
    private val inner: ProcessLauncher,
    private val layoutProvider: () -> RuntimeLayout?,
    private val abis: List<String>,
    private val tmpDirProvider: () -> String? = { null },
) : ProcessLauncher {

    private val planRef = AtomicReference<WineCommandBuilder.Plan?>(null)

    /** Último plan construido, `null` si todavía no se lanzó nada. */
    val lastPlan: WineCommandBuilder.Plan? get() = planRef.get()

    override fun launch(
        command: List<String>,
        environment: Map<String, String>,
        workingDirectory: String?,
    ): ProcessHandle {
        val layout = layoutProvider()
            ?: throw BinaryNotInstalledException(command.firstOrNull() ?: "wine64")

        val exePath = command.firstOrNull()
            ?: throw IllegalArgumentException("El comando no puede estar vacío")

        val plan = WineCommandBuilder.build(
            layout = layout,
            exePath = exePath,
            abis = abis,
            extraArgs = command.drop(1),
        )
        planRef.set(plan)

        val env = ExecEnvironment.build(
            base = environment,
            layout = layout,
            usingTranslator = plan.usingTranslator,
            tmpDir = tmpDirProvider(),
        )

        // El directorio de trabajo es la carpeta del `.exe`: muchos juegos
        // resuelven rutas relativas a su propio directorio y fallan si se
        // arrancan desde otro sitio.
        val cwd = workingDirectory ?: exePath.substringBeforeLast('/', missingDelimiterValue = layout.rootPath)

        return inner.launch(
            command = plan.command,
            environment = env,
            workingDirectory = cwd,
        )
    }

    /** Comando de inicialización del prefijo, si hay `wineboot` instalado. */
    fun bootstrapCommand(): List<String>? =
        layoutProvider()?.let { WineCommandBuilder.bootstrap(it) }

    /** Componentes que faltan para poder lanzar, para la UI. */
    fun missingForLaunch(): List<BinaryComponent> {
        val layout = layoutProvider() ?: return listOf(BinaryComponent.WINE)
        return layout.missingComponents(
            listOf(BinaryComponent.WINE, BinaryComponent.TOOLS),
        )
    }
}
