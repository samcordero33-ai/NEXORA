# Reglas de ofuscacion/minificacion de NEXORA.
# En la Fase 1 la build de release no minifica (isMinifyEnabled = false).
# Se conserva el archivo para cuando se habilite R8.

-keep class com.nexora.android.ui.theme.** { *; }
-keep class com.nexora.android.game.model.** { *; }
