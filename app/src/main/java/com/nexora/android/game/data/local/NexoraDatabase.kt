package com.nexora.android.game.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de datos local de NEXORA.
 *
 * Versión 1, una sola tabla (`games`). Es intencionadamente pequeña: aquí no se
 * guarda nada del runtime de ejecución, solo la biblioteca que el usuario ha
 * detectado, así que la capa de datos es **independiente del motor de
 * emulación** y sigue compilando y funcionando sin él.
 */
@Database(
    entities = [GameEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NexoraDatabase : RoomDatabase() {

    abstract fun gameDao(): GameDao

    companion object {

        private const val DATABASE_NAME = "nexora-library.db"

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
                ).build().also { created -> instance = created }
            }
    }
}
