package com.nexora.android.game.data.local

import com.nexora.android.engine.profile.PerformanceProfile
import com.nexora.android.game.model.Game
import com.nexora.android.game.model.GameIconPreset
import com.nexora.android.game.model.GameSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pruebas del mapeo entre el modelo de dominio y la fila de Room.
 *
 * Son pruebas JVM puras: ni la entidad ni los `enum` tocan Android, así que no
 * hace falta Robolectric ni instrumentación. Cubren justo lo que se puede
 * romper en silencio al persistir: un campo que no viaja, un ordinal de icono
 * inválido o un origen que ya no existe.
 */
class GameEntityTest {

    private val sampleGame = Game(
        id = "scan-1",
        name = "Hollow Knight",
        executablePath = "/storage/emulated/0/Games/Hollow Knight/hollow_knight.exe",
        installDirectory = "/storage/emulated/0/Games/Hollow Knight",
        iconPreset = GameIconPreset.ARCADE,
        iconUri = null,
        addedAtMillis = 1_706_000_000_000L,
        source = GameSource.SCAN,
        sizeBytes = 50_500_000L,
        documentUri = "content://com.android.providers.downloads.documents/tree/123",
    )

    @Test
    fun `un juego sobrevive a la ida y vuelta por la base de datos`() {
        val restored = sampleGame.toEntity().toDomain()

        assertEquals(sampleGame, restored)
    }

    @Test
    fun `la fila guarda los datos que exige la biblioteca`() {
        val entity = sampleGame.toEntity()

        assertEquals("scan-1", entity.id)
        assertEquals("Hollow Knight", entity.title)
        assertEquals(
            "/storage/emulated/0/Games/Hollow Knight/hollow_knight.exe",
            entity.exePath,
        )
        assertEquals(GameIconPreset.ARCADE.ordinal, entity.iconPreset)
        assertEquals(1_706_000_000_000L, entity.addedAt)
    }

    @Test
    fun `un ordinal de icono desconocido cae al predeterminado`() {
        val entity = sampleGame.toEntity().copy(iconPreset = 99)

        assertEquals(GameIconPreset.DEFAULT, entity.toDomain().iconPreset)
    }

    @Test
    fun `un origen desconocido cae a manual`() {
        val entity = sampleGame.toEntity().copy(source = "NO_EXISTE")

        assertEquals(GameSource.MANUAL, entity.toDomain().source)
    }

    @Test
    fun `el perfil elegido por el usuario se guarda por identificador`() {
        val entity = sampleGame.copy(profile = PerformanceProfile.RTS_PERFORMANCE).toEntity()

        assertEquals("rts_performance", entity.profileId)
        assertEquals(PerformanceProfile.RTS_PERFORMANCE, entity.toDomain().profile)
    }

    @Test
    fun `un juego sin perfil guardado queda en automatico`() {
        assertNull(sampleGame.toEntity().profileId)
        assertNull(sampleGame.toEntity().toDomain().profile)
    }

    @Test
    fun `un identificador de perfil desconocido degrada a automatico`() {
        val entity = sampleGame.toEntity().copy(profileId = "perfil_del_futuro")

        assertNull(entity.toDomain().profile)
    }

    @Test
    fun `los iconos predeterminados ciclan sin salirse de la lista`() {
        val all = GameIconPreset.ALL

        assertEquals(3, all.size)
        assertEquals(GameIconPreset.JOYPAD, GameIconPreset.next(GameIconPreset.DICE))
        assertEquals(GameIconPreset.ARCADE, GameIconPreset.next(GameIconPreset.JOYPAD))
        assertNull(sampleGame.iconUri)
    }
}
