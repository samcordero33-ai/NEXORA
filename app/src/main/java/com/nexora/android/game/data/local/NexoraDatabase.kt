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
import com.nexora.android.runtime.data.local.AudioSettingsDao
import com.nexora.android.runtime.data.local.AudioSettingsEntity
import com.nexora.android.runtime.data.local.GlobalPreferencesDao
import com.nexora.android.runtime.data.local.GlobalPreferencesEntity
import com.nexora.android.runtime.data.local.RuntimeConfigDao
import com.nexora.android.runtime.data.local.RuntimeConfigEntity

/**
 * Base de datos local de NEXORA.
 *
 * Versión 2: la tabla `games` gana la columna `profileId`, donde se guarda el
 * perfil de rendimiento que el usuario ha elegido para cada juego (Fase 4).
 *
 * Versión 3: se añaden `control_profiles` y `control_buttons`, la disposición
 * del HUD táctil de cada juego (Fase 5).
 *
 * Versión 4: se añade `runtime_configs`, los ajustes del entorno de ejecución
 * (DXVK, Box64, Mesa, WINEPREFIX) por juego (Fase 6).
 *
 * Versión 5: se añaden `audio_settings` (overrides de audio por juego) y
 * `global_preferences` (singleton con tema y flags) — Fase 7.
 *
 * Las migraciones son **manuales** y aditivas: cada fase crea tablas nuevas
 * sin tocar las anteriores. Una base de datos de la versión 3 sigue teniendo
 * su biblioteca, su icono, su perfil y su HUD al actualizarse a 5.
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
        RuntimeConfigEntity::class,
        AudioSettingsEntity::class,
        GlobalPreferencesEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    abstract fun controlLayoutDao(): ControlLayoutDao

    abstract fun runtimeConfigDao(): RuntimeConfigDao

    abstract fun audioSettingsDao(): AudioSettingsDao

    abstract fun globalPreferencesDao(): GlobalPreferencesDao

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

        /** Fase 6: tabla del entorno de ejecución. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `runtime_configs` (" +
                        "`gameId` TEXT NOT NULL, " +
                        "`dxvkHud` TEXT NOT NULL, " +
                        "`box64Log` TEXT NOT NULL, " +
                        "`box64Dynarec` TEXT NOT NULL, " +
                        "`galliumDriver` TEXT NOT NULL, " +
                        "`mesaVkWinsys` TEXT NOT NULL, " +
                        "`wineDebug` TEXT NOT NULL, " +
                        "`winetricksVerbs` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`gameId`)" +
                        ")",
                )
            }
        }

        /** Fase 7: tabla de audio por juego + preferencias globales singleton. */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `audio_settings` (" +
                        "`gameId` TEXT NOT NULL, " +
                        "`backend` TEXT NOT NULL, " +
                        "`sampleRate` TEXT NOT NULL, " +
                        "`bufferFrames` TEXT NOT NULL, " +
                        "`preventClicks` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`gameId`)" +
                        ")",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `global_preferences` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`theme` TEXT NOT NULL, " +
                        "`animationsEnabled` INTEGER NOT NULL, " +
                        "`autoExportLogs` INTEGER NOT NULL, " +
                        "`defaultBackend` TEXT NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`)" +
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { created -> instance = created }
            }
    }
}