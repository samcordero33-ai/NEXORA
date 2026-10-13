package com.nexora.android.runtime.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.nexora.android.runtime.audio.AudioBackend
import com.nexora.android.runtime.audio.AudioBufferFrames
import com.nexora.android.runtime.audio.AudioSampleRate
import com.nexora.android.runtime.audio.AudioSettings

/**
 * Configuración de audio del runtime, **por juego** (Fase 7).
 *
 * Estructura: misma idea que [RuntimeConfigEntity]: una fila por `gameId`,
 * columnas `String` para sobrevivir a reordenamientos de enum. La entidad
 * es **separada** porque no todos los juegos necesitan overrides de audio y
 * porque queremos poder restablecer audio a [AudioSettings.DEFAULT] sin
 * tocar el resto del entorno.
 */
@Entity(tableName = "audio_settings")
data class AudioSettingsEntity(
    @PrimaryKey val gameId: String,
    val backend: String,
    val sampleRate: String,
    val bufferFrames: String,
    val preventClicks: Boolean,
    val updatedAt: Long,
) {
    fun toSettings(): AudioSettings = AudioSettings.fromStorage(
        backend = backend,
        sampleRate = sampleRate,
        bufferFrames = bufferFrames,
        preventClicks = preventClicks,
    )

    companion object {
        fun fromSettings(gameId: String, settings: AudioSettings, now: Long): AudioSettingsEntity =
            AudioSettingsEntity(
                gameId = gameId,
                backend = settings.backend.wireValue,
                sampleRate = settings.sampleRate.wireValue,
                bufferFrames = settings.bufferFrames.wireValue,
                preventClicks = settings.preventClicks,
                updatedAt = now,
            )

        // Mantengo AudioBackend disponible aquí para que un import del companion
        // no genere "unused import" si se referencian constantes en tests.
        @Suppress("unused")
        private val defaultBackend: AudioBackend = AudioBackend.DEFAULT
        @Suppress("unused")
        private val defaultRate: AudioSampleRate = AudioSampleRate.DEFAULT
        @Suppress("unused")
        private val defaultBuffer: AudioBufferFrames = AudioBufferFrames.DEFAULT
    }
}