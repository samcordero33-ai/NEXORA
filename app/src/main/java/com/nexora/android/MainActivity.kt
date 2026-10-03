package com.nexora.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.nexora.android.ui.screens.NexoraHomeRoute
import com.nexora.android.ui.theme.NexoraBackground
import com.nexora.android.ui.theme.NexoraTheme

/**
 * Actividad única de NEXORA.
 *
 * Toda la UI se compone en `NexoraHomeRoute`; la Activity solo aporta el
 * contenedor y el tema. Los efectos que dependen del ciclo de vida (selector de
 * carpetas del sistema, petición de permisos) viven en la ruta, no aquí.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NexoraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NexoraBackground,
                ) {
                    NexoraHomeRoute()
                }
            }
        }
    }
}
