package com.nexora.android.permission

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Estado del acceso al almacenamiento compartido.
 *
 * NEXORA necesita leer ejecutables que el usuario ha puesto en su dispositivo.
 * Desde Android 11 (API 30) el almacenamiento con ámbito impide recorrer
 * libremente el almacenamiento compartido, así que hay dos vías y conviene
 * saber cuál está disponible en cada momento:
 *
 * - **Carpetas concedidas** ([grantedTrees]): el usuario elige una carpeta con
 *   el Storage Access Framework y concede acceso persistente solo a esa carpeta.
 *   No requiere ningún permiso y funciona también en microSD. Es la vía
 *   recomendada y la que no tiene restricciones de política de tienda.
 * - **Acceso amplio** ([canReadSharedStorage]): permite recorrer cualquier ruta,
 *   incluidas las carpetas habituales de juegos sin que el usuario tenga que
 *   localizarlas. En Android 11+ es `MANAGE_EXTERNAL_STORAGE`.
 *
 * @property canReadSharedStorage `true` si se puede leer el almacenamiento
 *   compartido por ruta. En API 30+ equivale a `MANAGE_EXTERNAL_STORAGE`; por
 *   debajo, a `READ_EXTERNAL_STORAGE` concedido.
 * @property canRequestAllFilesAccess `true` si el sistema expone el ajuste de
 *   acceso amplio a todos los archivos (API 30+).
 * @property grantedTrees `Uri` en texto de las carpetas con acceso persistente
 *   concedido mediante el Storage Access Framework.
 * @property needsLegacyReadPermission `true` cuando todavía aplica el permiso
 *   clásico de lectura (API 32 y anteriores) y no está concedido.
 */
data class StorageAccessState(
    val canReadSharedStorage: Boolean = false,
    val canRequestAllFilesAccess: Boolean = false,
    val grantedTrees: List<String> = emptyList(),
    val needsLegacyReadPermission: Boolean = false,
)

/**
 * Consulta y solicita el acceso al almacenamiento.
 *
 * Esta clase no muestra diálogos: expone el estado y el `Intent` de ajustes, y
 * la UI decide cuándo lanzarlo. Mantener la decisión de permisos separada de la
 * presentación es lo que permite que el estado sea observable y testeable.
 */
class StorageAccessController(private val context: Context) {

    /** Estado actual del acceso al almacenamiento. */
    fun currentState(): StorageAccessState {
        val legacyGranted = hasLegacyReadPermission()
        return StorageAccessState(
            canReadSharedStorage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                legacyGranted
            },
            canRequestAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            grantedTrees = persistedTreeUris(),
            needsLegacyReadPermission = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2 &&
                !legacyGranted,
        )
    }

    /**
     * `Intent` que abre el ajuste del sistema "Acceso a todos los archivos".
     *
     * @return `null` en Android 10 y anteriores, donde ese ajuste no existe y el
     *   permiso clásico de lectura es suficiente.
     */
    fun allFilesAccessIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Permisos clásicos que la Activity debe pedir en tiempo de ejecución. */
    fun legacyReadPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        } else {
            emptyArray()
        }

    /** `true` si está concedido el permiso clásico de lectura. */
    private fun hasLegacyReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

    /** Árboles con acceso persistente concedido que siguen siendo válidos. */
    private fun persistedTreeUris(): List<String> =
        context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
}
