package com.nexora.android.engine.input

import kotlin.math.sqrt

/**
 * Punto o vector en dos dimensiones, en píxeles de la superficie táctil.
 *
 * Es deliberadamente una clase propia y no `Offset` de Compose: el motor de
 * gestos se prueba en JVM, sin Android ni Compose, y la capa de UI traduce sus
 * posiciones al tipo que toque en el borde.
 */
data class Vec2(
    val x: Float,
    val y: Float,
) {

    operator fun plus(other: Vec2): Vec2 = Vec2(x + other.x, y + other.y)

    operator fun minus(other: Vec2): Vec2 = Vec2(x - other.x, y - other.y)

    operator fun times(factor: Float): Vec2 = Vec2(x * factor, y * factor)

    operator fun div(divisor: Float): Vec2 = Vec2(x / divisor, y / divisor)

    /** Distancia al origen: mide cuánto se ha desplazado un dedo. */
    fun length(): Float = sqrt(x * x + y * y)

    companion object {

        val ZERO = Vec2(0f, 0f)
    }
}
