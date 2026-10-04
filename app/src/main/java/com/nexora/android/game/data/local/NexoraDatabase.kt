package com.nexora.android.game.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Base de datos local de NEXORA.
 *
 * Versión 2: la tabla `games` gana la columna `profileId`, donde se guarda el
 * perfil de rendimiento que el usuario ha elegido para cada juego (Fase 4).
 *
 * La migración es **manual** y aditiva: `ALTER TABLE ... ADD COLUMN` sobre una
 * columna nullable no toca las filas existentes, así que un juego ya guardado
 * sigue en automático (`NULL`) en vez de heredar un perfil que nadie eligió.
 *
 * Es intencionadamente pequeña: aquí no se guarda nada del runtime de
 * ejecución, solo la biblioteca que el usuario ha detectado, así que la capa de
 * datos es **independiente del motor de emulación** y sigue compilando y
 * funcionando sin él.
 */
@Database(
    entities = [GameEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    companion object {

        private const val DATABASE_NAME = "nexora-library.db"

        /** Fase 4: alta de la columna `profileId` (perfil por juego). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN profileId TEXT")
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
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { created -> instance = created }
            }
    }
}
