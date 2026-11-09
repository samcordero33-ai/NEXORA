package com.nexora.android.runtime.overlay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nexora.android.MainActivity
import com.nexora.android.R
import com.nexora.android.runtime.monitor.AndroidRuntimeMonitorSource
import com.nexora.android.runtime.monitor.FrameSnapshot
import com.nexora.android.runtime.monitor.ResourceSnapshot
import com.nexora.android.ui.overlay.FloatingHud
import com.nexora.android.ui.theme.NexoraTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Servicio que dibuja el HUD de rendimiento **encima del juego**.
 *
 * ### Por qué un `Service` con `WindowManager` y no un composable más
 * Un composable solo existe dentro de la jerarquía de la Activity. Cuando el
 * juego corre en pantalla completa, o cuando el usuario cambia de app, esa
 * jerarquía deja de verse. La única forma de mantener el HUD visible es una
 * ventana propia (`TYPE_APPLICATION_OVERLAY`), y una ventana propia necesita
 * un `Context` que no muera con la Activity: un servicio en primer plano.
 *
 * ### Qué mide y qué no
 * - **FPS del motor**: los publica el `RuntimeViewModel` en
 *   [OverlayMetricsBus] cuando `DXVK_HUD` los escribe en stderr. Son los
 *   reales del render.
 * - **FPS del overlay**: si el motor no publica nada, se muestran los frames
 *   que ve el `Choreographer` de este proceso. Se etiqueta como `overlay`
 *   porque no son necesariamente los del juego.
 * - **RAM / CPU**: `ActivityManager` y `Debug.MemoryInfo`. La temperatura es
 *   una **estimación** (Android no expone sensores de CPU sin permisos), y así
 *   se documenta en el monitor de la Fase 6.
 *
 * ### Cierre de emergencia
 * El botón de emergencia publica [OverlayCommand.EmergencyKill] en el bus. El
 * servicio **no** mata el proceso: el `Process` pertenece al `RuntimeViewModel`
 * y solo él puede cerrarlo con su `ProcessHandle`. Si no hay sesión activa, el
 * botón lo dice en vez de no hacer nada.
 */
class OverlayService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val metrics = MutableStateFlow(OverlayMetrics())

    private lateinit var windowManager: WindowManager
    private var root: ComposeView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var monitorSource: AndroidRuntimeMonitorSource? = null
    private var sampleJob: Job? = null

    /** Tamaño actual del HUD. Es estado de Compose: cambiarlo recompone. */
    private var mode by mutableStateOf(HudMode.COMPACT)

    private var sessionStartMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        monitorSource = AndroidRuntimeMonitorSource(applicationContext)
        lifecycleOwner = OverlayLifecycleOwner().apply {
            onCreate()
            onStart()
        }
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground(intent?.getStringExtra(OverlayController.EXTRA_GAME_NAME))

        if (!OverlayController.canDrawOverlays(this)) {
            // Sin permiso no hay ventana posible. Se avisa y se cierra: dejar
            // el servicio vivo sin HUD solo gastaría batería.
            toast(getString(R.string.overlay_permission_missing))
            stopSelf()
            return START_NOT_STICKY
        }

        attachOverlay()
        startSampling()
        return START_STICKY
    }

    override fun onDestroy() {
        sampleJob?.cancel()
        sampleJob = null
        monitorSource?.close()
        monitorSource = null
        removeOverlay()
        lifecycleOwner?.onDestroy()
        lifecycleOwner = null
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------------
    // Ventana
    // ---------------------------------------------------------------------

    private fun attachOverlay() {
        if (root != null) return
        val owner = lifecycleOwner ?: return
        val (screenWidth, screenHeight) = screenSize()
        val width = dpToPx(mode.widthDp)
        val height = dpToPx(mode.heightDp)
        val bounds = OverlayGeometry.initial(
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            width = width,
            height = height,
            topInsetPx = statusBarHeightPx(),
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = bounds.x
            y = bounds.y
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                NexoraTheme {
                    val snapshot by metrics.collectAsState()
                    FloatingHud(
                        metrics = snapshot,
                        mode = mode,
                        onDrag = ::dragBy,
                        onToggleMode = {
                            mode = mode.next()
                            snapIntoPlace()
                        },
                        onKill = ::requestEmergencyKill,
                        onClose = ::dismiss,
                    )
                }
            }
        }

        windowManager.addView(view, params)
        root = view
        layoutParams = params
    }

    private fun removeOverlay() {
        val view = root ?: return
        runCatching { windowManager.removeView(view) }
        root = null
        layoutParams = null
    }

    /** Cierra el HUD y avisa a la app (para que su botón vuelva a "activar"). */
    private fun dismiss() {
        OverlayMetricsBus.send(OverlayCommand.HudDismissed)
        stopSelf()
    }

    /**
     * Mueve la ventana aplicando el delta del gesto.
     *
     * El tamaño real de la vista se lee de `root.width/height` cuando ya está
     * medida; antes del primer layout vale 0 y se usa la estimación en `dp`.
     */
    private fun dragBy(dx: Float, dy: Float) {
        val view = root ?: return
        val params = layoutParams ?: return
        val (screenWidth, screenHeight) = screenSize()
        val width = view.width.takeIf { it > 0 } ?: dpToPx(mode.widthDp)
        val height = view.height.takeIf { it > 0 } ?: dpToPx(mode.heightDp)
        val moved = OverlayGeometry.move(
            current = HudBounds(params.x, params.y, width, height),
            dx = dx,
            dy = dy,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
        )
        params.x = moved.x
        params.y = moved.y
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    /** Vuelve a sujetar la ventana tras cambiar de tamaño. */
    private fun snapIntoPlace() {
        val view = root ?: return
        val params = layoutParams ?: return
        val (screenWidth, screenHeight) = screenSize()
        val width = dpToPx(mode.widthDp)
        val height = dpToPx(mode.heightDp)
        view.post {
            val measuredWidth = view.width.takeIf { it > 0 } ?: width
            val measuredHeight = view.height.takeIf { it > 0 } ?: height
            val placed = OverlayGeometry.snapToEdge(
                bounds = OverlayGeometry.place(
                    desiredX = params.x,
                    desiredY = params.y,
                    width = measuredWidth,
                    height = measuredHeight,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight,
                ),
                screenWidth = screenWidth,
            )
            params.x = placed.x
            params.y = placed.y
            runCatching { windowManager.updateViewLayout(view, params) }
        }
    }

    // ---------------------------------------------------------------------
    // Muestreo
    // ---------------------------------------------------------------------

    private fun startSampling() {
        if (sampleJob != null) return
        sessionStartMs = System.currentTimeMillis()
        monitorSource?.start()
        sampleJob = scope.launch {
            while (isActive) {
                val frame: FrameSnapshot = monitorSource?.sampleFrame(System.nanoTime())
                    ?: FrameSnapshot(fps = 0, frameTimeMs = 0.0)
                val resources: ResourceSnapshot = monitorSource?.sampleResources()
                    ?: ResourceSnapshot(0L, 0L, 0, 0, 0)

                val device = DeviceSample(
                    fps = frame.fps,
                    frameTimeMs = frame.frameTimeMs,
                    ramUsedBytes = resources.usedRamBytes,
                    ramTotalBytes = resources.totalRamBytes,
                    cpuPercent = resources.cpuUsagePercent,
                    cpuTempCelsius = resources.cpuTempCelsius,
                    gpuTempCelsius = resources.gpuTempCelsius,
                )
                metrics.value = OverlayComposer.compose(
                    engine = OverlayMetricsBus.engine.value,
                    device = device,
                    nowMs = System.currentTimeMillis(),
                    elapsedMs = System.currentTimeMillis() - sessionStartMs,
                )
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    /**
     * Pide el cierre del juego.
     *
     * Si no hay sesión publicada, se avisa: es mejor un mensaje que un botón
     * que parece no responder cuando el juego se ha colgado antes de arrancar.
     */
    private fun requestEmergencyKill() {
        if (!OverlayMetricsBus.engine.value.active) {
            toast(getString(R.string.overlay_no_session))
            return
        }
        val delivered = OverlayMetricsBus.send(OverlayCommand.EmergencyKill)
        toast(
            if (delivered) {
                getString(R.string.overlay_kill_requested)
            } else {
                getString(R.string.overlay_no_session)
            },
        )
    }

    // ---------------------------------------------------------------------
    // Notificación / sistema
    // ---------------------------------------------------------------------

    private fun startAsForeground(gameName: String?) {
        val launchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = if (gameName.isNullOrBlank()) {
            getString(R.string.overlay_notification_text)
        } else {
            getString(R.string.overlay_notification_text_game, gameName)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_nexora)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(launchIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.overlay_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun screenSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            return bounds.width() to bounds.height()
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    /** Alto de la barra de estado, leído del recurso del sistema. */
    private fun statusBarHeightPx(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** Intervalo de refresco del HUD. */
        const val SAMPLE_INTERVAL_MS: Long = 500L

        private const val CHANNEL_ID = "nexora_overlay"
        private const val NOTIFICATION_ID = 0x4E58 // "NX"
    }
}

/**
 * Ciclo de vida mínimo para hospedar una composición fuera de una Activity.
 *
 * `ComposeView` exige un `LifecycleOwner` y un `SavedStateRegistryOwner` en el
 * árbol de vistas; sin ellos lanza `IllegalStateException` al componer. Esta
 * clase es la versión mínima que cumple el contrato: `CREATED` al crear,
 * `RESUMED` mientras la ventana está puesta y `DESTROYED` al cerrar.
 */
private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onCreate() {
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onStart() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}
