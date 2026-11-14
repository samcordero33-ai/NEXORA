package com.nexora.android.ui.runner

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

/**
 * Modo inmersivo real para la pantalla de ejecución.
 *
 * "Real" significa que las barras del sistema **desaparecen**, no que se pinten
 * de negro: `WindowInsetsControllerCompat.hide(systemBars())` retira la barra de
 * estado y la de navegación, y con `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` el
 * usuario puede recuperarlas con un gesto desde el borde, que es el
 * comportamiento que Android espera de una app a pantalla completa.
 *
 * El estado no se guarda: se deriva de si la pantalla está en modo juego. Si el
 * usuario sale a la biblioteca, [ImmersiveEffect] restaura las barras en
 * `onDispose`; no hay forma de dejar la app sin barra de navegación.
 */
object ImmersiveMode {

    /** Oculta las barras y deja que aparezcan con un gesto desde el borde. */
    fun enter(window: Window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Devuelve las barras a su comportamiento normal. */
    fun exit(window: Window) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
    }
}

/**
 * Aplica (y deshace) el modo inmersivo mientras [enabled] sea `true`.
 *
 * Se reaplica en `ON_RESUME` porque el sistema devuelve las barras al volver de
 * segundo plano —una llamada entrante, el selector de archivos— sin avisar a la
 * app.
 */
@Composable
fun ImmersiveEffect(enabled: Boolean) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(window, enabled, lifecycleOwner) {
        if (window == null) {
            return@DisposableEffect onDispose { }
        }
        if (enabled) ImmersiveMode.enter(window) else ImmersiveMode.exit(window)

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (enabled) ImmersiveMode.enter(window) else ImmersiveMode.exit(window)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ImmersiveMode.exit(window)
        }
    }
}

/** Sube por la cadena de `ContextWrapper` hasta la Activity que tiene la ventana. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
