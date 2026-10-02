package com.nexora.android

import android.app.Application

/**
 * Punto de arranque del proceso de NEXORA.
 *
 * Fase 1: no inicializa nada. Existe para tener un punto de enganche propio
 * (sin depender de la clase por defecto) cuando en fases posteriores haya que
 * preparar el runtime, la caché o la configuración global antes de que se
 * cree la primera Activity.
 */
class NexoraApplication : Application()
