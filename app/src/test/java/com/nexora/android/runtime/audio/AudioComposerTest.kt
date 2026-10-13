package com.nexora.android.runtime.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioBackendTest {

    @Test
    fun `wireValue de los backends es estable`() {
        // Estos strings van al que acaban por medio del storage; si cambian,
        // cualquier DB de usuario queda ilegible.
        assertEquals("aaudio", AudioBackend.AAUDIO.wireValue)
        assertEquals("opensles", AudioBackend.OPENSL_ES.wireValue)
        assertEquals("pulse", AudioBackend.PULSE_AUTO.wireValue)
    }

    @Test
    fun `fromWireValue degrada desconocidos a DEFAULT`() {
        assertEquals(AudioBackend.PULSE_AUTO, AudioBackend.fromWireValue(null))
        assertEquals(AudioBackend.PULSE_AUTO, AudioBackend.fromWireValue(""))
        assertEquals(AudioBackend.PULSE_AUTO, AudioBackend.fromWireValue("alsa-inexistente"))
        assertEquals(AudioBackend.AAUDIO, AudioBackend.fromWireValue("aaudio"))
    }
}

class AudioSampleRateTest {

    @Test
    fun `defaults son 48 kHz`() {
        assertEquals(48_000, AudioSampleRate.DEFAULT.hz)
    }

    @Test
    fun `fromWireValue degrada a DEFAULT si el dato es invalido`() {
        assertEquals(AudioSampleRate.DEFAULT, AudioSampleRate.fromWireValue(null))
        assertEquals(AudioSampleRate.DEFAULT, AudioSampleRate.fromWireValue("22050"))
        assertEquals(AudioSampleRate.HZ_44100, AudioSampleRate.fromWireValue("44100"))
    }
}

class AudioBufferFramesTest {

    @Test
    fun `todos los tamanos son positivos`() {
        AudioBufferFrames.values().forEach {
            assertTrue("frames > 0", it.frames > 0)
        }
    }

    @Test
    fun `fromWireValue degrada a DEFAULT si el dato es invalido`() {
        assertEquals(AudioBufferFrames.DEFAULT, AudioBufferFrames.fromWireValue(null))
        assertEquals(AudioBufferFrames.DEFAULT, AudioBufferFrames.fromWireValue("64"))
        assertEquals(AudioBufferFrames.SMALL_96, AudioBufferFrames.fromWireValue("96"))
    }
}

class AudioComposerTest {

    @Test
    fun `compone backend, rate y buffer siempre`() {
        val out = AudioComposer.compose(AudioSettings.DEFAULT)
        assertEquals(AudioBackend.DEFAULT.wireValue, out.environment["NEXORA_AUDIO_BACKEND"])
        assertEquals(AudioSampleRate.DEFAULT.wireValue, out.environment["NEXORA_AUDIO_RATE"])
        assertEquals(AudioBufferFrames.DEFAULT.wireValue, out.environment["NEXORA_AUDIO_BUFFER"])
        assertTrue(out.warnings.isEmpty())
    }

    @Test
    fun `PULSE_LATENCY_MSEC solo aparece en modo pulse con preventClicks`() {
        val conClicks = AudioSettings(
            backend = AudioBackend.PULSE_AUTO,
            preventClicks = true,
            bufferFrames = AudioBufferFrames.MEDIUM_192,
            sampleRate = AudioSampleRate.HZ_48000,
        )
        val sinClicks = conClicks.copy(preventClicks = false)
        val aaudio = conClicks.copy(backend = AudioBackend.AAUDIO)

        assertNotNull(AudioComposer.compose(conClicks).environment["PULSE_LATENCY_MSEC"])
        assertFalse(AudioComposer.compose(sinClicks).environment.containsKey("PULSE_LATENCY_MSEC"))
        assertFalse(AudioComposer.compose(aaudio).environment.containsKey("PULSE_LATENCY_MSEC"))
    }

    @Test
    fun `NEXORA_PREVENT_CLICKS se exporta solo cuando esta activo`() {
        val on = AudioSettings(preventClicks = true)
        val off = AudioSettings(preventClicks = false)
        assertEquals("1", AudioComposer.compose(on).environment["NEXORA_PREVENT_CLICKS"])
        assertFalse(AudioComposer.compose(off).environment.containsKey("NEXORA_PREVENT_CLICKS"))
    }

    @Test
    fun `latencyMillis calcula bien para 192 frames a 48 kHz`() {
        assertEquals(4, AudioComposer.latencyMillis(AudioBufferFrames.MEDIUM_192, AudioSampleRate.HZ_48000))
        assertEquals(2, AudioComposer.latencyMillis(AudioBufferFrames.SMALL_96, AudioSampleRate.HZ_48000))
        assertEquals(10, AudioComposer.latencyMillis(AudioBufferFrames.LARGE_480, AudioSampleRate.HZ_48000))
    }

    @Test
    fun `advertir buffer muy grande a 44,1 kHz`() {
        val s = AudioSettings(
            bufferFrames = AudioBufferFrames.XLARGE_1024,
            sampleRate = AudioSampleRate.HZ_44100,
        )
        val out = AudioComposer.compose(s)
        assertTrue("warnings no vacío", out.warnings.isNotEmpty())
        assertTrue(out.warnings.any { it.contains("latencia", ignoreCase = true) })
    }

    @Test
    fun `advertir OpenSL ES con buffer muy pequeno`() {
        val s = AudioSettings(
            backend = AudioBackend.OPENSL_ES,
            bufferFrames = AudioBufferFrames.SMALL_96,
        )
        val out = AudioComposer.compose(s)
        assertTrue(out.warnings.any { it.contains("OpenSL", ignoreCase = true) })
    }

    @Test
    fun `AudioSettings fromStorage degrada cualquier campo`() {
        val s = AudioSettings.fromStorage(
            backend = "foobar",
            sampleRate = "32000",
            bufferFrames = null,
            preventClicks = null,
        )
        assertEquals(AudioBackend.DEFAULT, s.backend)
        assertEquals(AudioSampleRate.DEFAULT, s.sampleRate)
        assertEquals(AudioBufferFrames.DEFAULT, s.bufferFrames)
        assertTrue(s.preventClicks)
    }
}