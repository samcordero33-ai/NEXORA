// Configuracion raiz del proyecto.
// Los plugins se declaran aqui con `apply false` y se aplican en cada modulo.
// Las versiones viven en gradle/libs.versions.toml (version catalog).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
