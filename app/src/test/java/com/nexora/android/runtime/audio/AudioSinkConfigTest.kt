package com.nexora.android.runtime.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSinkConfigTest {

    @Test
    fun `el buffer por defecto es el doble del periodo elegido`() {
        val settings = AudioSettings(
            sampleRate = AudioSampleRate.HZ_48000,
            bufferFrames = AudioBufferFrames.MEDIUM_192,
        )

        val config = AudioSinkConfig.from(settings, minBufferBytes = 0)

        // 192 frames * 4 bytes/frame * 2 (doble búfer) = 1536
        assertEquals(1536, config.bufferBytes)
        assertEquals(384, config.bufferFrames)
        assertEquals(8, config.latencyMs)
        assertTrue(config.lowLatency)
    }

    @Test
    fun `el minimo del sistema manda cuando es mayor`() {
        val settings = AudioSettings(bufferFrames = AudioBufferFrames.SMALL_96)

        val config = AudioSinkConfig.from(settings, minBufferBytes = 4096)

        assertEquals(4096, config.bufferBytes)
        assertEquals(1024, config.bufferFrames)
        assertEquals(21, config.latencyMs)
    }

    @Test
    fun `el buffer se alinea a frames completos`() {
        assertEquals(4, AudioSinkConfig.align(1, 4))
        assertEquals(4, AudioSinkConfig.align(4, 4))
        assertEquals(1004, AudioSinkConfig.align(1001, 4))
        assertEquals(4, AudioSinkConfig.align(0, 4))
    }

    @Test
    fun `solo los buffers pequenos piden modo de baja latencia`() {
        assertTrue(
            AudioSinkConfig.from(
                AudioSettings(bufferFrames = AudioBufferFrames.SMALL_96),
                minBufferBytes = 0,
            ).lowLatency,
        )
        assertFalse(
            AudioSinkConfig.from(
                AudioSettings(bufferFrames = AudioBufferFrames.LARGE_480),
                minBufferBytes = 0,
            ).lowLatency,
        )
    }

    @Test
    fun `la frecuencia elegida se respeta`() {
        val config = AudioSinkConfig.from(
            AudioSettings(sampleRate = AudioSampleRate.HZ_44100),
            minBufferBytes = 0,
        )

        assertEquals(44_100, config.sampleRateHz)
        assertEquals(0x0C, config.channelMask)
    }

    @Test
    fun `el canal mono usa mascara mono`() {
        val config = AudioSinkConfig.from(AudioSettings.DEFAULT, minBufferBytes = 0, channelCount = 1)

        assertEquals(0x04, config.channelMask)
        assertEquals(2, config.bytesPerFrame)
    }
}
