package com.nexora.android.runtime.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Punto único de entrada al HUD flotante.
 *
 * Envolver el `startService` aquí tiene dos ventajas: la UI no necesita saber
 * que existe un `Service`, y el chequeo del permiso de superposición se hace
 * en un solo sitio. `Settings.canDrawOverlays` es la única fuente de verdad:
 * si devuelve `false`, el HUD **no** se arranca y el llamante recibe `false`
 * para que muestre el botón de conceder permiso en vez de fingir que flota.
 */
object OverlayController {

    /** Extra con el nombre del juego, solo para la notificación. */
    const val EXTRA_GAME_NAME: String = "com.nexora.android.extra.GAME_NAME"

    /** `true` si la app puede dibujar sobre otras aplicaciones. */
    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * Intent que lleva al ajuste del sistema "Aparecer encima de otras apps".
     *
     * No hay diálogo de runtime para este permiso: Android solo lo concede
     * desde Ajustes, así que se abre la pantalla concreta de la app.
     */
    fun permissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}"),
    )

    /**
     * Arranca el servicio del HUD.
     *
     * @return `false` si falta el permiso (no se arranca nada).
     */
    fun start(context: Context, gameName: String): Boolean {
        if (!canDrawOverlays(context)) return false
        val intent = Intent(context, OverlayService::class.java)
            .putExtra(EXTRA_GAME_NAME, gameName)
        ContextCompat.startForegroundService(context, intent)
        return true
    }

    /** Detiene el servicio y retira la ventana. Idempotente. */
    fun stop(context: Context) {
        context.stopService(Intent(context, OverlayService::class.java))
    }
}
