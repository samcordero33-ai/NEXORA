package com.nexora.android.runtime.engine

import com.nexora.android.runtime.binary.BinaryComponent
import com.nexora.android.runtime.binary.RuntimeLayout
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Layout de mentira con las rutas que usaría una instalación real. */
private fun layout(
    wine: Boolean = true,
    box64: Boolean = true,
    tools: Boolean = true,
): RuntimeLayout {
    val binaries = buildMap {
        if (wine) put(BinaryComponent.WINE, "/data/app/runtime/bin/wine64")
        if (box64) put(BinaryComponent.BOX64, "/data/app/runtime/bin/box64")
        if (tools) put(BinaryComponent.TOOLS, "/data/app/runtime/bin/wineboot")
    }
    return RuntimeLayout(rootPath = "/data/app/runtime", version = "8", binaries = binaries)
}

class WineCommandBuilderTest {

    @Test
    fun `en ARM con box64 antepone el traductor`() {
        val plan = WineCommandBuilder.build(
            layout = layout(),
            exePath = "/games/hl2.exe",
            abis = listOf("arm64-v8a", "armeabi-v7a"),
        )

        assertTrue(plan.usingTranslator)
        assertEquals(
            listOf("/data/app/runtime/bin/box64", "/data/app/runtime/bin/wine64", "/games/hl2.exe"),
            plan.command,
        )
    }

    @Test
    fun `en ARM sin box64 avisa y lanza solo wine`() {
        val plan = WineCommandBuilder.build(
            layout = layout(box64 = false),
            exePath = "/games/hl2.exe",
            abis = listOf("arm64-v8a"),
        )

        assertFalse(plan.usingTranslator)
        assertEquals(2, plan.command.size)
        assertTrue(plan.notes.any { it.contains("sin Box64") })
    }

    @Test
    fun `en x86_64 no se usa traductor aunque este instalado`() {
        val plan = WineCommandBuilder.build(
            layout = layout(),
            exePath = "/games/hl2.exe",
            abis = listOf("x86_64"),
        )

        assertFalse(plan.usingTranslator)
        assertEquals("/data/app/runtime/bin/wine64", plan.command.first())
        assertTrue(plan.notes.any { it.contains("x86_64") })
    }

    @Test
    fun `los argumentos extra se conservan al final`() {
        val plan = WineCommandBuilder.build(
            layout = layout(),
            exePath = "/games/hl2.exe",
            abis = listOf("x86_64"),
            extraArgs = listOf("-windowed", "-novid"),
        )

        assertEquals(listOf("-windowed", "-novid"), plan.command.takeLast(2))
    }

    @Test
    fun `sin wine no hay plan`() {
        try {
            WineCommandBuilder.build(layout(wine = false), "/games/hl2.exe", listOf("arm64-v8a"))
            fail("debía lanzar BinaryNotInstalledException")
        } catch (_: BinaryNotInstalledException) {
            // correcto
        }
    }

    @Test
    fun `bootstrap usa wineboot si esta instalado`() {
        assertEquals(listOf("/data/app/runtime/bin/wineboot"), WineCommandBuilder.bootstrap(layout()))
        assertNull(WineCommandBuilder.bootstrap(layout(tools = false)))
    }
}

class ExecEnvironmentTest {

    private val base = mapOf(
        "WINEPREFIX" to "/data/app/prefixes/hl2",
        "DXVK_HUD" to "fps",
        "BOX64_DYNAREC" to "1",
        "WINEDEBUG" to "",
    )

    @Test
    fun `antepone las rutas del runtime al PATH y a LD_LIBRARY_PATH`() {
        val env = ExecEnvironment.build(base, layout(), usingTranslator = true, tmpDir = "/data/app/tmp")

        assertTrue(env.getValue("PATH").startsWith("/data/app/runtime/bin:"))
        assertTrue(env.getValue("LD_LIBRARY_PATH").startsWith("/data/app/runtime/lib"))
        assertTrue(env.getValue("LD_LIBRARY_PATH").contains("/data/app/runtime/lib/wine"))
        assertEquals("/data/app/runtime/lib", env.getValue("BOX64_LD_LIBRARY_PATH"))
    }

    @Test
    fun `respeta WINEPREFIX y las variables del compositor`() {
        val env = ExecEnvironment.build(base, layout(), usingTranslator = false)

        assertEquals("/data/app/prefixes/hl2", env.getValue("WINEPREFIX"))
        assertEquals("fps", env.getValue("DXVK_HUD"))
        assertEquals("1", env.getValue("BOX64_DYNAREC"))
    }

    @Test
    fun `rellena HOME TMPDIR y PULSE_SERVER si faltan`() {
        val env = ExecEnvironment.build(base, layout(), usingTranslator = false, tmpDir = "/data/app/tmp")

        assertEquals("/data/app/tmp", env.getValue("TMPDIR"))
        assertEquals("/data/app/tmp", env.getValue("XDG_RUNTIME_DIR"))
        assertEquals("unix:/data/app/tmp/pulse/native", env.getValue("PULSE_SERVER"))
        assertEquals("/data/app/runtime/prefixes", env.getValue("HOME"))
    }

    @Test
    fun `las variables vacias se descartan y WINEDEBUG cae al valor silencioso`() {
        val env = ExecEnvironment.build(base, layout(), usingTranslator = false)

        assertFalse(env.containsKey("WINEDEBUG") && env.getValue("WINEDEBUG").isEmpty())
        assertEquals("-all", env.getValue("WINEDEBUG"))
    }

    @Test
    fun `sin traductor no se define BOX64_LD_LIBRARY_PATH`() {
        val env = ExecEnvironment.build(base, layout(), usingTranslator = false)

        assertFalse(env.containsKey("BOX64_LD_LIBRARY_PATH"))
    }
}

/** Lanzador que no arranca nada: guarda lo que le piden. */
private class RecordingLauncher : ProcessLauncher {
    var command: List<String> = emptyList()
    var environment: Map<String, String> = emptyMap()
    var workingDirectory: String? = null

    override fun launch(
        command: List<String>,
        environment: Map<String, String>,
        workingDirectory: String?,
    ): ProcessHandle {
        this.command = command
        this.environment = environment
        this.workingDirectory = workingDirectory
        return object : ProcessHandle {
            override val pid: Long = 42L
            override val isAlive: Boolean = false
            override val stdout: InputStream = ByteArrayInputStream(ByteArray(0))
            override val stderr: InputStream = ByteArrayInputStream(ByteArray(0))
            override fun kill() = Unit
            override fun waitFor(): Int = 0
        }
    }
}

class ProcessExecutionEngineTest {

    @Test
    fun `compone comando entorno y directorio de trabajo`() {
        val recorder = RecordingLauncher()
        val engine = ProcessExecutionEngine(
            inner = recorder,
            layoutProvider = { layout() },
            abis = listOf("arm64-v8a"),
            tmpDirProvider = { "/data/app/tmp" },
        )

        engine.launch(
            command = listOf("/games/hl2/hl2.exe"),
            environment = mapOf("WINEPREFIX" to "/data/app/prefixes/hl2"),
            workingDirectory = null,
        )

        assertEquals("/data/app/runtime/bin/box64", recorder.command.first())
        assertEquals("/data/app/runtime/bin/wine64", recorder.command[1])
        assertEquals("/games/hl2", recorder.workingDirectory)
        assertTrue(recorder.environment.getValue("PATH").contains("/data/app/runtime/bin"))
        assertEquals("unix:/data/app/tmp/pulse/native", recorder.environment.getValue("PULSE_SERVER"))
        assertEquals("/data/app/runtime/bin/wine64", engine.lastPlan?.winePath)
    }

    @Test
    fun `sin layout no lanza y lo dice`() {
        val engine = ProcessExecutionEngine(
            inner = RecordingLauncher(),
            layoutProvider = { null },
            abis = listOf("arm64-v8a"),
        )

        try {
            engine.launch(listOf("/games/hl2.exe"), emptyMap(), null)
            fail("debía lanzar BinaryNotInstalledException")
        } catch (error: BinaryNotInstalledException) {
            assertTrue(error.message!!.contains("hl2.exe"))
        }
    }

    @Test
    fun `missingForLaunch informa de lo que falta`() {
        val engine = ProcessExecutionEngine(
            inner = RecordingLauncher(),
            layoutProvider = { RuntimeLayout.empty("/tmp/runtime") },
            abis = listOf("arm64-v8a"),
        )

        assertEquals(
            listOf(BinaryComponent.WINE, BinaryComponent.TOOLS),
            engine.missingForLaunch(),
        )
    }
}
