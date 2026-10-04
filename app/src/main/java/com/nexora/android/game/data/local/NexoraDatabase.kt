package com.nexora.android.game.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.nexora.android.control.data.local.ControlButtonEntity
import com.nexora.android.control.data.local.ControlLayoutDao
import com.nexora.android.control.data.local.ControlProfileEntity

/**
 * Base de datos local de NEXORA.
 *
 * Versión 2: la tabla `games` gana la columna `profileId`, donde se guarda el
 * perfil de rendimiento que el usuario ha elegido para cada juego (Fase 4).
 *
 * Versión 3: se añaden `control_profiles` y `control_buttons`, la disposición
 * del HUD táctil de cada juego (Fase 5).
 *
 * Las migraciones son **manuales** y aditivas. La de la Fase 4 añade una columna
 * nullable (las filas ya guardadas siguen en automático) y la de la Fase 5 crea
 * dos tablas nuevas sin tocar `games`, así que un juego escaneado con la versión
 * anterior conserva su biblioteca, su icono y su perfil.
 *
 * Es intencionadamente pequeña: aquí no se guarda nada del runtime de
 * ejecución, solo la biblioteca que el usuario ha detectado y cómo quiere
 * controlarla, así que la capa de datos es **independiente del motor de
 * emulación** y sigue compilando y funcionando sin él.
 */
@Database(
    entities = [
        GameEntity::class,
        ControlProfileEntity::class,
        ControlButtonEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    abstract fun controlLayoutDao(): ControlLayoutDao

    companion object {

        private const val DATABASE_NAME = "nexora-library.db"

        /** Fase 4: alta de la columna `profileId` (perfil por juego). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN profileId TEXT")
            }
        }

        /** Fase 5: tablas del HUD táctil. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `control_profiles` (" +
                        "`gameId` TEXT NOT NULL, " +
                        "`overlayOpacity` REAL NOT NULL, " +
                        "`dragBoxEnabled` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`gameId`)" +
                        ")",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `control_buttons` (" +
                        "`gameId` TEXT NOT NULL, " +
                        "`actionId` TEXT NOT NULL, " +
                        "`centerX` REAL NOT NULL, " +
                        "`centerY` REAL NOT NULL, " +
                        "`size` REAL NOT NULL, " +
                        "`visible` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`gameId`, `actionId`)" +
                        ")",
                )
            }
        }

        @Volatile
        private var instance: NexoraDatabase? = null

        /**
         * Instancia única de la base de datos.
         *
         * Se construye con el contexto de aplicación para no retener una
         * Activity, y bajo doble bloqueo para que dos corrutinas que arranquen a
         * la vez no creen dos bases de datos.
         */
        fun getInstance(context: Context): NexoraDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexoraDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { created -> instance = created }
            }
    }
}
