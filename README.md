# NEXORA v0.9 — Fase 9

**Windows Gaming Runtime for Android.**

NEXORA es una aplicación Android para ejecutar juegos de PC en móviles.

- **Fase 1 (v0.1):** estructura del proyecto, interfaz gaming oscura y
  detección del dispositivo.
- **Fase 2 (v0.2):** escaneo de almacenamiento local y microSD para detectar
  juegos instalados (`.exe`), gestión de permisos y progreso en la UI.
- **Fase 3 (v0.3):** persistencia local de la biblioteca con Room, grilla de
  tarjetas gaming y pantalla de detalle del juego.
- **Fase 4 (v0.4):** sistema **AutoOptimizer** y gestión de perfiles de
  rendimiento/compatibilidad según el hardware detectado.
- **Fase 5 (v0.5):** controles táctiles virtuales y gestos en pantalla
  (**HUD Overlay**) optimizados para RTS, con ratón virtual, recuadro de
  selección, zoom por pellizco y perfiles de control por juego en Room.
- **Fase 6 (v0.6, esta versión):** capa de **integración y orquestación** del
  motor de traducción. **Gestor de entorno y WINEPREFIX** con variables
  configurables (`DXVK_HUD`, `BOX64_LOG`, `BOX64_DYNAREC`, `GALLIUM_DRIVER`,
  `MESA_VK_WINSYS`, `WINEDEBUG`, `WINETRICKS`), **Engine Runner** con ciclo de
  vida completo (Preparar → Lanzar → Pausar → Reanudar → Kill) y captura de
  stdout/stderr en vivo, y **GameRunnerScreen** con HUD táctil integrado y
  monitor técnico flotante opcional (FPS, RAM, temperatura estimada).
- **Fase 7 (v0.7):** audio de **baja latencia** (backend
  AAudio / OpenSL ES / PulseAudio, frecuencia 44,1 / 48 kHz y buffer en
  frames con cálculo de latencia real), **caché y partidas guardadas**
  (exportación e importación de `Savegames` en ZIP, limpieza de la caché de
  sombreado DXVK y de temporales), **consola de diagnóstico** con visor en
  vivo y exportación del registro a `.txt`, **ajustes globales** (tema,
  animaciones, autoguardado de logs, almacenamiento y reseteo) y
  **animaciones** de transición entre biblioteca, detalle y ejecución.
- **Fase 8 (v0.8):** **motor de ejecución real**: instalación y
  verificación de binarios con permisos de ejecución, orquestador de procesos
  con `LD_LIBRARY_PATH` y `PATH` completos, **puente táctil `evdev`** que
  escribe eventos del kernel (`input_event`) y **salida de audio real** con
  `AudioTrack` en modo baja latencia.
- **Fase 9 (v0.9, esta versión):** **monitor de rendimiento flotante**
  (`OverlayService` + `WindowManager` + Compose, arrastrable, con FPS del motor
  vía `DXVK_HUD`, RAM/CPU del sistema y cierre de emergencia), **gestor
  completo de partidas guardadas** (escaneo de carpetas de guardado del
  prefijo, respaldos ZIP con historial, restauración con copia de seguridad
  previa e importación/exportación por SAF) y **consola de logs en vivo con
  reporte de fallos** (registro de sesión persistido en disco y diagnóstico
  automático de SIGSEGV, DLL ausente, `DEVICE_LOST`, OOM…).

> **El motor es real, los binarios no viajan en el APK.** La Fase 8 sustituye
> los stubs por la maquinaria completa —extracción, verificación SHA-256,
> `chmod`, `ProcessBuilder` con entorno y `LD_LIBRARY_PATH`, puente `evdev` y
> `AudioTrack`—, pero **NEXORA no redistribuye Wine, Box64, DXVK ni Turnip**:
> pesan cientos de megabytes, tienen licencias propias y dependen del SoC. Si
> `filesDir/runtime` está vacío, el motor falla de forma explícita con
> `BinaryNotInstalledException` y la UI dice exactamente qué falta y dónde
> ponerlo (`assets/runtime/manifest.txt`).
>
> **Lo que sí funciona sin binarios:** la app compila, arranca, instala y
> verifica lo que encuentre, abre el `AudioTrack`, intenta abrir un nodo
> `evdev` y lo reporta. Lo que no puede hacer es ejecutar un `.exe` que no
> tiene con qué ejecutar.
>
> **La Fase 9 no cambia eso.** El HUD flotante, el gestor de partidas y el
> analizador de fallos funcionan sobre lo que haya: si no hay binarios, el HUD
> muestra RAM/CPU reales y los FPS del overlay (etiquetados como tales, nunca
> como FPS del juego), el gestor de partidas encuentra cero carpetas y el
> analizador dice "runtime sin binarios".
>
> **No avanza a la Fase 10.** Crear un dispositivo `uinput` propio (requiere
> `ioctl`, es decir JNI), empaquetar los binarios firmados y la pausa real con
> SIGSTOP siguen fuera de alcance.

## Requisitos cumplidos

| Requisito | Estado |
|---|---|
| 100 % Kotlin nativo + Jetpack Compose + Android SDK | ✔ |
| Sin librerías de escritorio Java (`javax.swing.*`, `java.awt.*`) | ✔ verificado en fuentes **y en los DEX del APK** |
| Sin Wine / Box64 / DXVK | ✔ |
| No se ejecuta ningún binario de Windows | ✔ el escáner solo lee metadatos |
| Persistencia local (Room) con entidad `Game` (id, título, ruta del exe, ícono predeterminado, fecha de agregado) | ✔ `GameEntity` + `GameDao` + `NexoraDatabase` v2 |
| Los juegos detectados sobreviven al reinicio | ✔ la biblioteca se lee de SQLite, no de memoria |
| Interfaz de biblioteca en grilla de tarjetas gaming | ✔ `LazyVerticalGrid` + `GameCard` |
| Cada tarjeta: nombre, ícono, "Jugar", "Ajustes del juego" | ✔ |
| Estado vacío limpio | ✔ |
| Pantalla de detalle con información del ejecutable y opciones iniciales | ✔ `GameDetailScreen` |
| 10 perfiles de rendimiento con parámetros técnicos (resolución, FPS, backend DirectX/Vulkan, prioridad de CPU…) | ✔ `PerformanceProfile` |
| Nombres técnicos, nunca comerciales | ✔ "4. Rendimiento RTS / DirectX 9", "8. CPU Focus / Box64"… |
| `AutoOptimizer` que recomienda el perfil según hardware (GPU Mali/Adreno, RAM, CPU) y juego | ✔ `AutoOptimizer.recommend()` |
| Detalle del juego: perfil recomendado + cambio manual persistido en Room | ✔ `PerformanceProfileCard` + columna `profileId` |
| Emulación de ratón virtual: un toque / arrastre = mover cursor + clic izquierdo | ✔ `TouchGestureInterpreter` |
| Dos dedos o pulsación larga = clic derecho | ✔ `InputEvent.SecondaryClick` |
| Recuadro de selección ("drag-box") arrastrando | ✔ `InputEvent.SelectionRect` + dibujo en vivo |
| Pellizcar = zoom, dos dedos en paralelo = paneo de cámara | ✔ `CameraZoom` / `CameraPan` |
| Capa flotante con transparencia ajustable | ✔ `TouchControlsOverlay` + `overlayOpacity` |
| Acceso rápido RTS: `Ctrl`+`1/2/3`, `Shift`, `A` (ataque), `Espacio`, D-Pad y joystick opcional | ✔ `ControlAction` |
| Entidad Room con posición, tamaño y configuración de los botones por juego | ✔ `control_profiles` + `control_buttons` (v3) |
| Editor gráfico para mover y redimensionar los botones | ✔ `ControlEditorScreen` |
| Compilación limpia e independiente del motor de emulación | ✔ `./gradlew assembleDebug` sin avisos |
| Arquitectura limpia y modular | ✔ |
| **Gestor de entorno y `WINEPREFIX` por juego (Fase 6)** | ✔ `WinePrefix` + `RuntimeConfigEntity`/`runtime_configs` |
| Variables configurables: `DXVK_HUD`, `BOX64_LOG`, `BOX64_DYNAREC`, `GALLIUM_DRIVER`, `MESA_VK_WINSYS`, `WINEDEBUG`, `WINETRICKS` | ✔ `EnvironmentSettings` + `EnvironmentComposer.compose()` (función pura) |
| **Engine Runner** con ciclo de vida Preparar → Lanzar → Pausar → Reanudar → Kill | ✔ `RuntimeEngine` (8 pruebas) |
| Captura de `stdout`/`stderr` en vivo (canal acotado, auto-scroll inteligente) | ✔ `LogStreamer` + `RuntimeLogPanel` |
| **GameRunnerScreen** que prepara el entorno antes del render | ✔ `GameRunnerScreen` + `RuntimeViewModel` |
| Integración del HUD táctil de la Fase 5 sobre la vista de ejecución | ✔ `TouchControlsOverlay` en `GameRunnerScreen` |
| Monitor flotante técnico (FPS, RAM, temperatura estimada CPU/GPU) | ✔ `RuntimeMonitorCard` + `AndroidRuntimeMonitorSource` (`Choreographer` real, temperatura **estimada**) |
| Lanzador honesto: falla con `BinaryNotInstalledException` cuando no hay motor | ✔ `AndroidBinaryResolver` siempre devuelve `null` |
| Migración Room aditiva 3 → 4 sin alterar datos previos | ✔ `MIGRATION_3_4` (nueva tabla, sin `ALTER`) |
| Navegación por estado (sin Navigation Compose) y `BackHandler` encadenado | ✔ `runningGameId` en `ScannerUiState` |
| **Audio de baja latencia (Fase 7)** | ✔ `AudioBackend` + `AudioSampleRate` + `AudioBufferFrames` + `AudioComposer` (función pura, 13 pruebas) |
| Backend AAudio / OpenSL ES / PulseAudio seleccionable y persistido por juego | ✔ `NEXORA_AUDIO_BACKEND` + tabla `audio_settings` |
| Frecuencia 44,1 / 48 kHz y tamaño de buffer ajustable con latencia mostrada | ✔ `NEXORA_AUDIO_RATE`, `NEXORA_AUDIO_BUFFER`, `AudioComposer.latencyMillis()` |
| Prevención de chasquidos (crackling) | ✔ `preventClicks` → `PULSE_LATENCY_MSEC` + aviso cuando el buffer es demasiado pequeño |
| **Caché y partidas guardadas (Fase 7)** | ✔ `SaveBackupArchive` / `SaveImporter` (ZIP con `java.util.zip`) |
| Backup & Restore de `Savegames` entre el prefijo emulado y la memoria del teléfono | ✔ exportar e importar ZIP desde `SavesManagerScreen` |
| Limpieza de la caché de sombreado DXVK y de temporales | ✔ `ShaderCacheCleaner` (`*.dxvk-cache`, `*_state_cache`) + `TempFileCleaner` |
| **Consola de diagnóstico (Fase 7)** | ✔ `DiagnosticsLogger` (ring buffer) + `DiagnosticsScreen` |
| Visor de logs en tiempo real con filtro por nivel | ✔ `DiagnosticsViewModel` + `Flow<List<DiagnosticsEvent>>` |
| Exportación del registro completo a `.txt` y compartirlo | ✔ `LogExporter` + `FileProvider` (`nexora_file_paths.xml`) |
| **Ajustes globales (Fase 7)** | ✔ `GlobalSettingsScreen` + tabla `global_preferences` |
| Tema, animaciones y reseteo de ajustes/controles persistidos | ✔ `AppTheme`, `animationsEnabled`, `resetAll()` |
| **Transiciones fluidas (Fase 7)** | ✔ `AnimatedContent` (slide + fade) en `HomeScreen` |
| Migración Room aditiva 4 → 5 sin alterar datos previos | ✔ `MIGRATION_4_5` (dos tablas nuevas, sin `ALTER`) |
| **Instalación de binarios (Fase 8)** | ✔ `RuntimeBinaryLoader` + `BinaryInstaller` + `BinarySource` |
| Extracción desde `assets` hacia `filesDir` con verificación SHA-256 | ✔ `Sha256` + escritura a `.part` + `renameTo` |
| Permisos de ejecución aplicados desde Kotlin | ✔ `setExecutable(true, true)` antes de lanzar |
| **Orquestador de procesos (Fase 8)** | ✔ `ProcessExecutionEngine` (implementa `ProcessLauncher`) |
| `ProcessBuilder` con `WINEPREFIX`, `DXVK_HUD`, `BOX64_DYNAREC`… | ✔ `EnvironmentComposer` (Fase 6) + `ExecEnvironment` |
| Rutas de bibliotecas (`LD_LIBRARY_PATH`, `BOX64_LD_LIBRARY_PATH`) | ✔ `ExecEnvironment.build()` (5 pruebas) |
| Captura asíncrona de `stdout`/`stderr` | ✔ `LogStreamer` + `Channel` acotado, ya conectado al visor |
| **Puente táctil real (Fase 8)** | ✔ `InputBridge` + `UinputEvent` + `EvdevInputTransport` |
| Traducción de clics, zoom, arrastre y teclas a `input_event` | ✔ 11 pruebas de secuencia + 5 de mapeo de coordenadas |
| Escalado pantalla → escritorio virtual con recorte | ✔ `ViewportMapping` (`ABS_MAX_VALUE = 32767`) |
| **Audio real (Fase 8)** | ✔ `AudioTrackSink` + `AudioPipeline` + `FifoPcmSource` |
| `AudioTrack` 44,1/48 kHz con búfer calculado y modo baja latencia | ✔ `AudioSinkConfig` (6 pruebas) + `PERFORMANCE_MODE_LOW_LATENCY` |
| **HUD flotante (Fase 9)** | ✔ `OverlayService` (`TYPE_APPLICATION_OVERLAY`) + `FloatingHud` |
| Ventana transparente, arrastrable y sujeta a los bordes | ✔ `OverlayGeometry` (9 pruebas) + `WindowManager.updateViewLayout` |
| FPS del motor desde `DXVK_HUD` | ✔ `DxvkHudParser` (6 pruebas) + `OverlayMetricsBus` |
| RAM y CPU del sistema en vivo | ✔ `AndroidRuntimeMonitorSource` (Fase 6) reutilizado por el servicio |
| Minimizar / maximizar y cierre de emergencia | ✔ `HudMode` + `OverlayCommand.EmergencyKill` → `RuntimeViewModel.kill()` |
| **Gestor de partidas (Fase 9)** | ✔ `SaveLocationScanner` + `SaveBackupStore` |
| Escaneo de `Saved Games`, `My Games`, `Documents`, `AppData` | ✔ 8 pruebas sobre un `drive_c` temporal |
| Exportar / importar ZIP en la memoria del dispositivo | ✔ `SaveBackupArchive` + `SaveImporter` + SAF (`CreateDocument`/`OpenDocument`) |
| Restaurar respaldos con copia de seguridad previa | ✔ `SaveBackupStore.create()` antes de `SaveImporter.extract()` |
| **Consola de logs y reporte de fallos (Fase 9)** | ✔ `SessionLogRecorder` + `LogFileStore` + `CrashLogAnalyzer` |
| Registro de sesión persistido (sobrevive al cierre) | ✔ `filesDir/logs/session-<juego>-<fecha>.log`, `flush()` por línea |
| Diagnóstico automático de Box64 / Wine / DXVK | ✔ 13 pruebas: SIGSEGV, SIGILL, DLL, `DEVICE_LOST`, OOM, permisos… |

## Stack

| Elemento | Versión |
|---|---|
| Kotlin | 1.9.22 |
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.2 (wrapper versionado) |
| Compose Compiler | 1.5.8 |
| Compose BOM | 2024.02.00 |
| Room | 2.6.1 (kapt) |
| `androidx.documentfile` | 1.0.1 |
| `kotlinx-coroutines-android` | 1.7.3 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |
| JDK | 17 |

Versiones centralizadas en `gradle/libs.versions.toml` (version catalog).

## Arquitectura

```
com.nexora.android/
├── NexoraApplication.kt        # android.app.Application
├── MainActivity.kt             # ComponentActivity + setContent
├── ui/
│   ├── screens/
│   │   ├── HomeScreen.kt       # NexoraHomeRoute (efectos) + NexoraHomeScreen (pura)
│   │   └── GameDetailScreen.kt # detalle del juego (pura)
│   ├── scanner/GameScannerViewModel.kt
│   ├── components/             # NexoraCard, GameCard, GameIcon, ScanProgressCard,
│   │                           # StorageAccessCard, PerformanceProfileCard,
│   │                           # TouchControlsOverlay, HudControls
│   ├── util/Format.kt          # formateo de tamaños y fechas
│   └── theme/{Color,Theme,Type}.kt
├── runtime/                    # motor (Fase 6), audio/caché/diagnóstico (Fase 7),
│   │                           # binarios/entrada/ejecución real (Fase 8)
│   ├── binary/                 # RuntimeManifest, BinaryInstaller, BinarySource,
│   │                           # RuntimeBinaryLoader, RuntimeLayout,
│   │                           # FilesystemBinaryResolver, Sha256
│   ├── input/                  # UinputEvent + KeyCodeMapper, ViewportMapping,
│   │                           # InputBridge, EvdevInputTransport
│   ├── audio/                  # AudioBackend, AudioSampleRate, AudioBufferFrames,
│   │                           # AudioSettings, AudioComposer, AudioSinkConfig,
│   │                           # AudioTrackSink, AudioPipeline, FifoPcmSource
│   ├── overlay/                # OverlayGeometry, OverlayMetrics + OverlayComposer,
│   │                           # DxvkHudParser, OverlayMetricsBus,
│   │                           # OverlayController, OverlayService
│   ├── saves/                  # SaveBackupArchive, SaveImporter,
│   │                           # SaveLocationScanner, BackupNaming,
│   │                           # SaveBackupStore, ShaderCacheCleaner, TempFileCleaner
│   ├── diagnostics/            # DiagnosticsEvent, DiagnosticsLogger (ring buffer),
│   │                           # LogExporter (.txt), SessionLogRecorder (.log),
│   │                           # LogFileStore, CrashLogAnalyzer
│   ├── preferences/            # AppTheme + GlobalPreferences
│   └── repository/             # AudioSettingsRepository, GlobalPreferencesRepository
├── ui/
│   ├── overlay/FloatingHud.kt           # contenido del HUD flotante (Compose puro)
│   ├── saves/SavesViewModel.kt          # escanear / respaldar / restaurar
│   ├── diagnostics/DiagnosticsViewModel.kt  # pestañas En vivo y Sesión
│   ├── settings/GlobalSettingsViewModel.kt
│   ├── components/SharedComponents.kt   # MessageCard, EnumRow, RuntimeSectionCard
│   └── screens/{SavesManagerScreen,DiagnosticsScreen,GlobalSettingsScreen}.kt
├── control/                    # HUD táctil (Fase 5)
│   ├── model/
│   │   ├── ControlAction.kt    # Ctrl+1/2/3, Shift, A, Espacio, D-Pad, Joystick
│   │   └── ControlLayout.kt    # botón + disposición + transparencia
│   ├── data/local/
│   │   ├── ControlProfileEntity.kt  # ajustes del HUD por juego + mapeo
│   │   ├── ControlButtonEntity.kt   # posición/tamaño/visibilidad por botón
│   │   └── ControlLayoutDao.kt      # Flows de perfil y de botones
│   └── repository/ControlLayoutRepository.kt
├── game/
│   ├── model/
│   │   ├── Game.kt             # Game + GameSource + GameLibraryState
│   │   └── GameIcon.kt         # GameIconPreset (íconos predeterminados)
│   ├── data/local/
│   │   ├── GameEntity.kt       # entidad Room + mapeo a dominio
│   │   ├── GameDao.kt          # consultas y Flow sobre la tabla
│   │   └── NexoraDatabase.kt   # RoomDatabase v5 + migraciones 1→2→3→4→5 (singleton)
│   ├── scan/
│   │   ├── GameScanner.kt      # recorrido SAF y por ruta de filesystem
│   │   ├── ExecutableFilter.kt # heurísticas .exe (puras, sin Android)
│   │   ├── ScanModels.kt       # ScanConfig, ScanProgress, ScanOutcome
│   │   └── StorageTargets.kt   # volúmenes y carpetas habituales
│   └── repository/GameLibraryRepository.kt  # interfaz de biblioteca sobre Room
├── permission/
│   └── StorageAccessController.kt
├── engine/                     # preparado para futuras integraciones
│   ├── RuntimeEngine.kt        # interfaz + EngineStatus + LaunchResult
│   ├── DeviceInfo.kt           # + GPU, GL ES, low-RAM y clase de hardware
│   ├── DeviceInfoProvider.kt   # lectura desde Build y ActivityManager
│   ├── GpuVendor.kt            # Mali / Adreno / PowerVR / Tegra / Intel
│   ├── DeviceTier.kt           # gama baja / media / alta
│   ├── input/                  # motor de gestos táctiles (Fase 5, Kotlin puro)
│   │   ├── Vec2.kt             # punto/vector en píxeles
│   │   ├── VirtualKey.kt       # códigos de tecla virtual de Win32 + KeyStroke
│   │   ├── InputEvent.kt       # contrato gesto -> runtime
│   │   ├── GestureConfig.kt    # umbrales: slop, pulsación larga, zoom…
│   │   └── TouchGestureInterpreter.kt  # máquina de estados de los gestos
│   └── profile/
│       ├── PerformanceProfile.kt   # 10 perfiles + parámetros
│       ├── GameProfileHints.kt     # pistas del juego (API gráfica, género)
│       ├── GameProfileInspector.kt # inspección de la carpeta (solo nombres)
│       └── AutoOptimizer.kt        # puntuación y recomendación (Kotlin puro)
└── settings/
    └── AppSettings.kt
```

### `game/data/local/` — persistencia (Fase 3)

La biblioteca deja de vivir en memoria. Una tabla, una entidad:

| Columna | Contenido |
|---|---|
| `id` | clave primaria; la deriva el escáner de la ruta del `.exe` |
| `title` | título mostrado en la tarjeta |
| `exePath` | ruta del ejecutable, con **índice único** |
| `iconPreset` | ícono predeterminado del juego (`0` = mando) |
| `iconUri` | ícono personalizado opcional; `null` = usar el preset |
| `profileId` | perfil de rendimiento elegido (Fase 4); `NULL` = automático |
| `addedAt` | fecha de agregado, en milisegundos de época |
| `installDirectory`, `sizeBytes`, `source`, `documentUri` | metadatos del escáner (Fase 2) que muestra el detalle |

La Fase 4 sube el esquema a **v2** con una migración aditiva:

```sql
ALTER TABLE games ADD COLUMN profileId TEXT
```

Columna nullable, así que un juego ya guardado sigue en automático (`NULL`) en
vez de heredar un perfil que nadie eligió, y no se toca ninguna fila existente.
Se guarda el **identificador** del perfil (`rts_performance`), nunca su ordinal:
así se pueden añadir perfiles sin reinterpretar datos guardados, y un `id`
desconocido degrada a automático en vez de romper la pantalla.

La Fase 5 sube el esquema a **v3** con otra migración aditiva, esta vez de dos
tablas nuevas (`control_profiles` y `control_buttons`): la biblioteca, los
iconos y los perfiles de la Fase 4 no se tocan.

### `engine/input/` y `control/` — el HUD táctil (Fase 5)

La pantalla táctil no es un ratón, así que hay que traducirla. El reparto:

- **`TouchGestureInterpreter`** (Kotlin puro) convierte punteros en
  [InputEvent]. Es una máquina de estados: recibe `down`/`move`/`up` con posición
  y marca de tiempo, y devuelve lo que eso significa.

| Gesto | Evento que produce |
|---|---|
| Toque corto con un dedo | `CursorMove` + `PrimaryClick` |
| Arrastre con un dedo | recuadro en vivo; `SelectionRect` al soltar |
| Pulsación larga (un dedo quieto) | `SecondaryClick` |
| Toque con dos dedos | `SecondaryClick` en el centro de los dos |
| Pellizcar (dos dedos se separan) | `CameraZoom` incrementales |
| Dos dedos en paralelo | `CameraPan` |

Detalles que decidí y por qué:

- **El recuadro se dibuja en vivo y se emite al soltar.** El intérprete expone
  `selectionBox` para que la UI lo pinte mientras el dedo se mueve; el evento
  llega cuando el gesto termina, que es cuando una selección tiene sentido.
- **Dos dedos cancelan el recuadro.** Un segundo dedo nunca es "seleccionar más":
  es clic derecho, zoom o paneo.
- **Al levantar uno de dos dedos, el que queda se reancla.** Si no, soltar el
  segundo dispararía un clic izquierdo que nadie pidió.
- **El tercer dedo se ignora.** Dos bastan para todos los gestos y seguir más
  punteros solo añade casos imposibles de depurar.
- **Los umbrales están en `GestureConfig`.** Un panel grande necesita más
  tolerancia al arrastre que uno pequeño, y hay juegos (los que ya traen selección
  táctil) en los que el recuadro estorba: `dragBoxEnabled` lo desactiva y el
  arrastre con un dedo vuelve a ser solo mover el cursor.

- **`ControlAction`** define los botones: `Ctrl + 1/2/3` (grupos de control),
  `Shift` (modificadora, en modo interruptor), `A` (ataque en movimiento),
  `Espacio` (ir al evento), **D-Pad** y **joystick virtual** (opcional, oculto por
  defecto). Cada uno lleva la pulsación que enviará al juego: los códigos son de
  tecla virtual de Win32, así que el día que exista la inyección no hay que
  traducir nada.
- **`HudControl` / `ControlLayout`** guardan posición (centro en fracciones
  0..1), tamaño (fracción del lado menor), visibilidad, transparencia de la capa
  y el ajuste del recuadro. Normalizado: la misma disposición se ve igual en una
  tableta apaisada y en un móvil vertical, y se guarda en SQLite sin densidades
  ni orientación.

Persistencia, dos tablas:

| Tabla | Clave | Contenido |
|---|---|---|
| `control_profiles` | `gameId` | transparencia, recuadro de selección, `updatedAt` |
| `control_buttons` | `gameId` + `actionId` | `centerX`, `centerY`, `size`, `visible` |

Separarlas evita reescribir ocho filas cuando el usuario solo cambia la
transparencia. Un juego sin filas no es un error: el repositorio devuelve la
disposición de fábrica, y una acción desconocida en la tabla se ignora al leer.

Los dedos que caen **sobre un botón** no llegan al intérprete: el overlay
comprueba los rectángulos ocupados antes de entregar el puntero. Pulsar
"Ctrl + 1" no mueve además el cursor ni hace clic en el mapa.

Decisiones que importan:

- **El deduplicado lo hace la base de datos, no el código.** `exePath` es único y
  la inserción es `OnConflictStrategy.IGNORE`: reescanear la misma carpeta no
  duplica ni pisa el juego ya guardado (conserva su ícono elegido y su fecha de
  alta). El repositorio cuenta cuántas filas devolvieron `-1` para saber cuántos
  juegos eran realmente nuevos.
- **Las lecturas son `Flow`.** Room re-emite la consulta ante cualquier cambio de
  la tabla, así que la UI se refresca sola tras escanear, borrar o cambiar un
  ícono: el ViewModel no llama a nada para "recargar".
- **Independiente del motor.** La base de datos solo guarda la biblioteca: nada
  del runtime de ejecución, así que esta capa compila y funciona sin él.
- **El `ordinal` del preset es contrato de datos.** Los íconos se pueden añadir,
  pero no reordenar; y un ordinal desconocido cae al predeterminado.

### `ui/screens/` — biblioteca y detalle

La pantalla principal es una sola **`LazyVerticalGrid`** con celdas adaptativas
(`GridCells.Adaptive(168.dp)`): las tarjetas ocupan el ancho que toque y el resto
de secciones se emiten a span completo. Así la grilla se desplaza con la página
en un único contenedor perezoso (una grilla anidada dentro de una lista no está
permitida en Compose).

Cada `GameCard` muestra ícono, título y subtítulo (tamaño + origen), con:

- **Jugar** — ya cableado. En esta fase informa de que el runtime no está
  integrado; cuando llegue el motor, la tarjeta no cambia.
- **Ajustes del juego** — abre el detalle, donde están las opciones del juego.
- Toda la tarjeta abre el detalle; los dos botones consumen su propia pulsación.

La navegación es **estado** (`ScannerUiState.selectedGameId`), con `BackHandler`
para volver: con dos pantallas, un grafo de Navigation Compose sería más código
que la navegación misma, y el estado sobrevive solo a los cambios de
configuración. Si el juego abierto se borra, la pantalla vuelve sola a la
biblioteca.

La pantalla de detalle muestra **información del ejecutable** (archivo, carpeta
de instalación, tamaño, origen, fecha de alta, tipo de acceso y ruta completa),
el **perfil recomendado por AutoOptimizer** y sus **opciones iniciales**: jugar,
cambiar el ícono (persistido), copiar la ruta al portapapeles y quitar de la
biblioteca — con confirmación, y sin tocar nunca el archivo original.

```
RENDIMIENTO
┌────────────────────────────────────────┐
│ ✨ Perfil recomendado por AutoOptimizer │
│ 4. Rendimiento RTS / DirectX 9   [AUTO]│
│ Estrategia en tiempo real: CPU …       │
│ Confianza de la recomendación: 100 %   │
│ Resolución sugerida    1024 × 768      │
│ Límite de FPS          30 fps          │
│ Renderizado            DirectX 9       │
│ Prioridad de CPU       Alta            │
│ Efectos / Texturas     Baja / Baja     │
│ Hilos de trabajo       6               │
│ Memoria gráfica        384 MB          │
│ Latencia de audio      40 ms           │
│ VSync                  No              │
│ · Estrategia en tiempo real: manda …   │
│ · DirectX 9 es la API habitual de …    │
│ · GPU Mali de gama media: 30 fps …     │
│ [ CAMBIAR PERFIL ]                     │
└────────────────────────────────────────┘
```

Así se ve el caso del enunciado (RTS + DirectX 9 sobre Mali de gama media):

"Cambiar perfil" abre el selector con los diez perfiles más la opción
**Automático (AutoOptimizer)**, marcando cuál es el recomendado. La elección se
escribe en Room (`profileId`) y sobrevive al reinicio; el chip de la tarjeta
distingue `AUTOMÁTICO` de `MANUAL`.

### `runtime/` — orquestador del motor (Fase 6)

La capa de integración es la pieza que conecta los perfiles (Fase 4) y los
controles (Fase 5) con un eventual binario de Wine/Box64/DXVK. Nació como un
**conjunto de `interface`s + una implementación honesta** que fallaba de forma
visible; la Fase 8 la completa con instalación, `ProcessBuilder` real y
`LD_LIBRARY_PATH` (ver [`runtime/binary/`](#runtimebinary--instalación-del-runtime-fase-8)
y [`runtime/engine/`](#runtimeengine--orquestación-real-del-proceso-fase-8)).

- **`runtime/env/`** — `EnvironmentVariables.kt` declara los enums
  (`DxvkHudPreset`, `Box64LogLevel`, `Box64DynarecSize`, `GalliumDriver`,
  `MesaVkWinsys`) con su `wireValue` (el string que va a la variable de
  entorno) y su `label` (lo que ve el usuario). `WinePrefix.kt` es el
  envoltorio del directorio `WINEPREFIX` por juego (idempotente,
  `underFilesDir(filesDir, gameId)`); `EnvironmentComposer.kt` es la
  **función pura** que, dados los parámetros del perfil y los flags del
  usuario, devuelve un `ComposedEnvironment(environment, warnings, command,
  winePrefixPath)`. Es 100 % testeable en JVM: 13 pruebas.
- **`runtime/engine/`** — `ProcessLauncher`/`ProcessHandle` son
  abstracciones. `AndroidProcessLauncher` usa `ProcessBuilder` con un
  `BinaryResolver` inyectable; por defecto
  (`AndroidBinaryResolver`) devuelve `null`, lo que dispara
  `BinaryNotInstalledException`. `LogStreamer` convierte stdout/stderr en
  un `Flow<RuntimeLog>` con `Channel` de 512 elementos; `InMemoryLogSink`
  sirve para tests. `NoopProcessLauncher`/`NoopProcessHandle` simulan un
  proceso con líneas prefijadas, sin tocar el sistema. `RuntimeEngine` es
  la máquina de estados (`Idle → Preparing → Running ⇄ Paused → Stopped /
  Failed`) con `prepare`, `launch`, `pause`, `resume`, `kill`, `logs()`.
- **`runtime/model/`** — `RuntimeStatus` (sealed: `Idle`, `Preparing`,
  `Running`, `Paused`, `Stopped`, `Failed`) y `RuntimeLog` (`LogStream`
  con tres canales: STDOUT, STDERR, SYSTEM).
- **`runtime/data/local/`** — `RuntimeConfigEntity`/`RuntimeConfigDao`/
  `RuntimeConfigRepository` persisten los `EnvironmentSettings` por juego
  en una tabla `runtime_configs` (migración aditiva 3 → 4, sin alterar
  las anteriores).
- **`runtime/monitor/`** — `RuntimeMonitorSource` (interfaz), un
  `FakeRuntimeMonitorSource` para tests/previews y
  `AndroidRuntimeMonitorSource` para producción. FPS se mide con
  `Choreographer.FrameCallback` sobre una ventana de 30; RAM se lee de
  `ActivityManager.MemoryInfo` + `Debug.MemoryInfo`; la temperatura de
  CPU/GPU es una **estimación** marcada como tal en la UI.
- **`runtime/repository/`** — Fachada sobre el DAO con
  `observeSettings(gameId): Flow<EnvironmentSettings>` (devuelve el
  `DEFAULT` cuando no hay fila) y `save`/`reset`.

### `ui/runner/` y `ui/screens/GameRunnerScreen.kt` — pantalla de ejecución (Fase 6)

`RuntimeViewModel` une la biblioteca (Fase 3), los perfiles (Fase 4), los
controles (Fase 5) y el orquestador (Fase 6) en una única `RuntimeUiState`
inmutable. Carga el juego, observa los ajustes del entorno, prepara el
entorno (`prepareEnvironment`), lanza (`launch`), pausa, resume, mata y
muestra los logs (auto-scroll solo cuando el usuario ya estaba abajo;
auto-scroll desactivado cuando se desplaza hacia arriba). El monitor
técnico (`RuntimeMonitorCard`) y el panel de logs (`RuntimeLogPanel` +
`RuntimeLogSummary`) son componentes reutilizables.

`GameRunnerScreen` apila, en orden: barra superior con **chip de estado**,
`BoxWithConstraints` negra como superficie de juego, `TouchControlsOverlay`
(Fase 5) por encima y la `RuntimeMonitorCard` flotante cuando se activa,
chips para HUD/Monitor, botones **Preparar / Lanzar / Pausar / Reanudar /
Forzar cierre** deshabilitados según estado, tarjeta de advertencias,
resumen del entorno, tarjeta de **ajustes editables** con `Save`/`Reset`, y
panel de logs. La navegación se hace con un único campo `runningGameId` en
`ScannerUiState` y `BackHandler` encadenado.

### `runtime/audio/` — audio de baja latencia (Fase 7)

Tres perillas que **sí cambian lo que recibe el motor**, calculadas por una
función pura (`AudioComposer.compose`) que no toca Android ni el sistema de
audio: devuelve un mapa de variables de entorno, avisos y la latencia
resultante en milisegundos.

| Ajuste | Valores | Variable que emite |
|---|---|---|
| Backend | `aaudio`, `opensles`, `pulse` | `NEXORA_AUDIO_BACKEND` |
| Frecuencia | 44 100 Hz, 48 000 Hz | `NEXORA_AUDIO_RATE` |
| Buffer (frames) | 96, 192, 480, 1024 | `NEXORA_AUDIO_BUFFER` |
| Antichasquidos | activado / desactivado | `PULSE_LATENCY_MSEC` |

La latencia no se inventa: `latencyMillis(buffer, rate) = frames * 1000 / hz`
redondeado hacia arriba, así que **96 frames a 48 kHz son 2 ms y 1024 frames a
44,1 kHz son 24 ms**, y eso es lo que ve el usuario bajo el selector. Cuando el
buffer elegido baja de ~4 ms y la prevención de chasquidos está activa, el
compositor emite un aviso: por debajo de ese umbral, un SoC cargado produce
*crackling* y conviene subir un escalón.

Todo se resuelve con `fromWireValue(...)`: un valor guardado desconocido
degrada al predeterminado (`pulse` / 48 kHz / 192 frames) en vez de romper el
arranque. Igual que con los perfiles, se persiste el **identificador**, nunca
el ordinal.

### `runtime/saves/` — caché y partidas guardadas (Fase 7)

El `WINEPREFIX` de cada juego vive en `filesDir/prefixes/<gameId>` (Fase 6), y
ahí es donde un Wine real escribiría `drive_c/users/<usuario>/Documentos/My
Games` o `Saved Games`. Como el motor no corre, la Fase 7 trabaja sobre esa
ruta **emulada**: la empaqueta y la restaura, que es exactamente lo que hará
cuando exista el motor.

| Operación | Clase | Qué hace |
|---|---|---|
| Exportar | `SaveBackupArchive.pack(root, zip, prefix)` | recorre el prefijo y escribe un ZIP con rutas relativas |
| Importar | `SaveImporter.extract(zip, targetDir)` | extrae **rechazando** rutas absolutas y `..` (zip-slip) |
| Limpiar sombreado | `ShaderCacheCleaner.clean(prefix)` | borra `*.dxvk-cache` y `*_state_cache` |
| Limpiar temporales | `TempFileCleaner.clean(dirs, deep)` | borra temporales recientes; `deep` añade subcarpetas |

Dos decisiones que importan: el **import valida cada entrada** antes de
escribirla (una ruta que se escapa del destino se rechaza y se reporta, no se
escribe "por si acaso"), y **la limpieza nunca toca el `WINEPREFIX` fuera de
las carpetas de caché**: borrar la caché de DXVK es reversible (se regenera),
borrar una partida no lo es.

### `runtime/diagnostics/` — consola de diagnóstico (Fase 7)

- **`DiagnosticsEvent`** es inmutable: marca de tiempo, nivel (`DEBUG`, `INFO`,
  `WARN`, `ERROR`), origen y mensaje, con `formatLine()` para el `.txt`.
- **`DiagnosticsLogger`** mantiene un **ring buffer** acotado (por defecto 500
  eventos) protegido con `synchronized`: la memoria no crece sin límite en una
  sesión larga y el último tramo —que es el que importa cuando algo falla— no
  se pierde. Expone `snapshot()`, `clear()` y un `Flow` para la UI.
- **`LogExporter.export(events, output)`** escribe cabecera (versión,
  dispositivo, fecha) + una línea por evento y devuelve el `File`, que se
  comparte con un `Intent ACTION_SEND` a través de **`FileProvider`**
  (`res/xml/nexora_file_paths.xml`). Sin `FileProvider` y sin
  `file://` expuesto: a partir de Android 7 un `Uri` de archivo fuera de la app
  lanza `FileUriExposedException`.

La pantalla (`DiagnosticsScreen`) muestra el visor con **auto-scroll solo si el
usuario ya estaba abajo** —la misma regla que el panel de logs de la Fase 6—,
filtro por nivel mínimo, exportar, compartir y vaciar.

### `ui/settings/` — ajustes globales (Fase 7)

`GlobalPreferences` (tema, animaciones, autoguardado de logs y backend por
defecto) + `AppTheme` se guardan en la tabla `global_preferences` de Room, con
**una sola fila** (`SINGLETON_ID = 1`) y `Flow`. La pantalla ofrece:

- **Tema**: oscuro / claro / seguir al sistema.
- **Animaciones**: desactivarlas hace las transiciones instantáneas en
  dispositivos muy antiguos.
- **Autoguardado de logs**: exporta un `.txt` cuando el motor pasa a `Failed`.
- **Almacenamiento**: cuánto ocupan los prefijos y su caché, con acceso directo
  a la limpieza de la Fase 7.
- **Reseteo**: ajustes globales, controles por juego y biblioteca, siempre con
  confirmación y sin tocar los archivos originales.

### Animaciones y transiciones (Fase 7)

`HomeScreen` envuelve el enrutado en un único **`AnimatedContent`**: la pantalla
que entra lo hace con `slideInHorizontally + fadeIn` en la dirección del
recorrido (biblioteca → detalle → ejecución avanza; volver retrocede), y la que
sale con `fadeOut + slideOutHorizontally`. La dirección se deriva del estado,
no de la pila de navegación, así que no hay que mantener un historial paralelo.
Si el usuario desactiva las animaciones en los ajustes globales, la transición
dura `0 ms` y el contenido cambia en seco.

### `runtime/binary/` — instalación del runtime (Fase 8)

El motor ya no es un stub: instala, verifica y marca como ejecutables los
binarios que encuentre. Lo que **no** hace es traerlos.

`assets/runtime/manifest.txt` describe qué se espera, en texto plano:

```ini
version=8
[wine]
asset=bin/wine64
target=bin/wine64
sha256=9f2c…
executable=true
size=104857600
```

`RuntimeBinaryLoader` lee ese manifiesto, calcula un **sello** (SHA-256 del
manifiesto + versión) y lo guarda en `runtime/.manifest.sha256`. Si el sello
coincide, no vuelve a copiar nada: reconstruye el layout leyendo el disco, que
es barato. Si cambia —o el usuario pulsa *Reinstalar*—, extrae lo que falte.

`BinaryInstaller` aplica cuatro reglas que no se negocian:

1. **Nada se escribe fuera de `filesDir/runtime`.** El destino se resuelve con
   `canonicalFile` y se comprueba el prefijo: un manifiesto manipulado con
   `../../` no puede tocar datos de la app.
2. **Se escribe a `.part` y se renombra.** Si el proceso muere a mitad de
   copia, no queda un `wine64` truncado que luego falle de forma críptica.
3. **La huella se verifica antes de renombrar.** Un binario alterado nunca
   llega a su ruta definitiva; el `.part` se borra y se reporta el motivo.
4. **Los permisos se aplican desde Kotlin**: `setExecutable(true, true)` —
   solo el dueño, nunca `chmod 777`.

Un componente cuyo `asset` no existe **no es un fallo**: se lista en
`InstallReport.missing` y la UI lo muestra como "sin empaquetar". Así el
manifiesto puede declarar los cuatro componentes y la app seguir funcionando
con cero binarios.

`FilesystemBinaryResolver` busca, en orden: ruta absoluta ejecutable →
`filesDir/runtime/bin/<nombre>` → `PATH` del sistema. Un archivo que existe pero
no es ejecutable **no** se devuelve: sería cambiar un error claro
(`BinaryNotInstalledException`) por un `Permission denied` del kernel.

### `runtime/engine/` — orquestación real del proceso (Fase 8)

`ProcessExecutionEngine` implementa `ProcessLauncher`, así que encaja sin tocar
la máquina de estados de la Fase 6. En cada lanzamiento:

| Paso | Pieza | Resultado |
|---|---|---|
| 1 | `RuntimeLayout` | rutas de Wine, Box64 y `wineboot` |
| 2 | `WineCommandBuilder` | `box64 wine64 juego.exe` en ARM; `wine64 juego.exe` en x86_64 |
| 3 | `ExecEnvironment` | `PATH`, `LD_LIBRARY_PATH`, `BOX64_LD_LIBRARY_PATH`, `HOME`, `TMPDIR`, `PULSE_SERVER` |
| 4 | `AndroidProcessLauncher` | `ProcessBuilder` real, `cwd` = carpeta del `.exe` |
| 5 | `LogStreamer` | `stdout`/`stderr` asíncronos al visor |

La decisión del traductor es explícita: en ARM64 los `.exe` son x86_64 y
necesitan Box64 delante; en x86_64 nativo Box64 sobra y solo añade una capa de
traducción. Si el dispositivo es ARM y no hay Box64, se lanza solo Wine **con
un aviso**, porque un binario de la misma arquitectura todavía podría correr.

`LD_LIBRARY_PATH` incluye `lib`, `lib/wine`, `lib/wine/x86_64-unix` y
`lib/wine/i386-unix`: Wine carga `libwine.so` y las DLL de DXVK desde ahí, no
desde el `PATH` del sistema. `HOME` apunta al prefijo para que Wine escriba
`dosdevices` dentro de la app.

### `runtime/input/` — puente táctil real (Fase 8)

Los gestos de la Fase 5 se convierten en estructuras `input_event` del kernel,
que es el mismo camino que usa `sendevent` y el que leen Wine (vía evdev) y un
servidor X de Android.

| Gesto | Secuencia `evdev` |
|---|---|
| `CursorMove` | `ABS_X` + `ABS_Y` + `SYN_REPORT` |
| `PrimaryClick` | mover + `BTN_LEFT 1` + SYN + `BTN_LEFT 0` + SYN |
| `SecondaryClick` | mover + `BTN_RIGHT 1/0` + SYN |
| `SelectionRect` | pulsar, 8 pasos interpolados, soltar |
| `CameraZoom` | `REL_WHEEL` ±1 por muesca (máx. ±3) |
| `CameraPan` | desplazamiento relativo del absoluto anterior |
| `KeyAction` | `EV_KEY` por tecla; al soltar, orden inverso |

La estructura se serializa a mano en **24 bytes** (timeval de 16 + `type` u16 +
`code` u16 + `value` s32, little-endian): no hace falta JNI para escribirla.

**Por qué no se crea el dispositivo `uinput`:** `UI_DEV_SETUP` y
`UI_DEV_CREATE` son `ioctl`, y `ioctl` no está en la API pública de Android sin
JNI. Escribir en un nodo **ya existente** sí se puede desde Kotlin. El puente
busca el primer `/dev/input/event*` escribible y, si no hay, `/dev/uinput`. En
un dispositivo sin root no habrá ninguno: la tarjeta "Motor nativo" lo dice y
el HUD sigue funcionando en pantalla, sin fingir que inyecta.

`ViewportMapping` escala píxeles de pantalla a píxeles del escritorio virtual y
al rango absoluto 0..32767, con recorte en los bordes: un dedo que sale del
área del juego se sujeta al borde en vez de generar un valor que el kernel
rechaza.

### `runtime/audio/` — reproducción real (Fase 8)

La Fase 7 componía variables (`NEXORA_AUDIO_RATE`, `PULSE_LATENCY_MSEC`); la
Fase 8 abre un `AudioTrack` de verdad:

```kotlin
AudioTrack.Builder()
    .setAudioAttributes(USAGE_GAME + CONTENT_TYPE_MUSIC)
    .setAudioFormat(PCM_16BIT, 48_000 Hz, estéreo)
    .setTransferMode(MODE_STREAM)
    .setPerformanceMode(PERFORMANCE_MODE_LOW_LATENCY)   // si el búfer ≤ 192 frames
```

`AudioSinkConfig` calcula el búfer: **el doble del periodo elegido**, alineado a
frames y nunca por debajo del mínimo del sistema (`getMinBufferSize`). Con 192
frames a 48 kHz son 1536 bytes, 384 frames de búfer y **8 ms** de latencia
teórica; con el mínimo de 4096 bytes, 21 ms.

`AudioPipeline` bombea desde un **FIFO** del prefijo
(`nexora-audio.fifo`): el motor escribe PCM y la app lo lee. Si el FIFO aún no
existe queda en `WAITING` y reintenta, porque el juego tarda más en abrir el
audio que la app en estar lista. `WRITE_BLOCKING` hace el control de flujo: si
la app va por delante se bloquea, y los *underruns* quedan contados en la UI.

Un fallo de `AudioTrack` no tumba la sesión: el juego sigue sin sonido y la
tarjeta lo dice. Un juego mudo es mejor que un cierre inesperado.

### `runtime/overlay/` — HUD flotante (Fase 9)

El monitor de la Fase 6 vive **dentro** de la pantalla de ejecución: cuando el
juego pasa a pantalla completa o el usuario cambia de app, desaparece. La Fase 9
lo saca a una ventana propia.

```
OverlayController.start(context, juego)
        │  (comprueba Settings.canDrawOverlays)
        ▼
OverlayService  ── WindowManager.addView(ComposeView, TYPE_APPLICATION_OVERLAY)
        │                │
        │                └── FloatingHud(metrics, mode, onDrag, onKill, onClose)
        │
        └── bucle de muestreo cada 500 ms
                ├── AndroidRuntimeMonitorSource  → RAM, CPU, FPS del overlay
                └── OverlayMetricsBus.engine     → FPS del motor (DXVK_HUD)
```

**Por qué un servicio en primer plano.** Una ventana `TYPE_APPLICATION_OVERLAY`
necesita un `Context` que sobreviva a la Activity, y el sistema mata los
servicios en segundo plano cuando hace falta memoria. El servicio se declara
`foregroundServiceType="specialUse"` (obligatorio en Android 14) y publica una
notificación silenciosa. Sin `POST_NOTIFICATIONS` la notificación no se ve,
pero el HUD sigue funcionando.

**Compose fuera de una Activity.** `ComposeView` exige un `LifecycleOwner` y un
`SavedStateRegistryOwner` en el árbol de vistas. `OverlayLifecycleOwner` es la
implementación mínima (CREATED → RESUMED → DESTROYED) que cumple el contrato sin
arrastrar una Activity entera.

**Geometría sin Android.** `OverlayGeometry` es aritmética de enteros:
`clampX/clampY`, `place`, `move` y `snapToEdge`. El `WindowManager` acepta
posiciones negativas sin protestar, y una ventana arrastrada fuera de pantalla
es irrecuperable para el usuario; por eso todo movimiento pasa por ahí antes de
llegar a `updateViewLayout`. Nueve pruebas cubren los bordes, el truncado de
deltas (para que un arrastre lento no vibre) y el anclaje al borde más cercano.

**De dónde salen los FPS.** Es la parte fácil de mentir, así que se etiqueta:

| Fuente | Cuándo | Etiqueta en el HUD |
|---|---|---|
| `DXVK_HUD` en stderr → `DxvkHudParser` | el motor publica cifras y tienen < 3 s | `motor` |
| `Choreographer` del proceso | el motor no publica nada | `overlay` |
| ninguna | aún no hay medida | `—` |

`OverlayComposer.compose()` hace la mezcla y es una función pura: recibe la
muestra del motor, la del dispositivo y el reloj, y devuelve `OverlayMetrics`.
Los umbrales (50/30 FPS, 75/90 % de RAM, 75/85 °C) están en
`OverlayThresholds` y se traducen a un semáforo de cuatro estados.

**Cierre de emergencia.** El botón rojo **no** mata nada: publica
`OverlayCommand.EmergencyKill` en `OverlayMetricsBus`. El `Process` pertenece al
`RuntimeViewModel`, y solo él puede cerrarlo con su `ProcessHandle`. Si no hay
sesión activa, el HUD lo dice con un `Toast` en vez de un botón que no responde.

### `runtime/saves/` — gestor de partidas (Fase 9)

La Fase 7 ya sabía empaquetar un ZIP; lo que faltaba era saber **qué**
empaquetar y **dónde** dejarlo.

`SaveLocationScanner` busca en las cinco rutas que Windows usa de verdad:
`Saved Games`, `Documents/My Games`, `Documents`, `AppData/Roaming` y
`AppData/Local`, dentro de cada `users/<u>`. Devuelve solo carpetas con
archivos, descarta la carpeta padre cuando ya lista la hija (el usuario quiere
la partida, no el árbol) y marca con `matchesGame` las que contienen alguna
palabra significativa del título —`"The Witcher 3: Wild Hunt"` produce
`["witcher", "wild", "hunt"]`, sin artículos ni «Edition»—.

`BackupNaming` codifica juego y fecha **en el nombre del archivo**:

```
nexora-saves_<gameId>_20250312-180412.zip
```

No hay índice ni base de datos que se pueda desincronizar: copiar el ZIP a otra
carpeta y devolverlo sigue funcionando. Un ZIP que no siga la convención no se
lista (no se sabe de qué juego es) pero tampoco se borra.

**Corrección respecto a la Fase 7:** el respaldo se empaqueta **relativo a
`drive_c`**, sin el prefijo `nexora/<id>/` que usaba la exportación anterior. Con
aquel prefijo, restaurar dejaba los archivos en `drive_c/nexora/<id>/users/...`
en vez de sobreescribir la partida. Ahora exportar e importar son simétricos, y
una prueba lo verifica leyendo las entradas del ZIP.

`SaveBackupStore` crea, lista, borra y poda (`prune(keep)`), y resuelve las
colisiones del mismo segundo avanzando el nombre en vez de sobreescribir.

La restauración hace **primero** un respaldo de seguridad de lo que hay. Si el
ZIP elegido es de otra partida, ese respaldo es la única forma de volver atrás.

### `runtime/diagnostics/` — registro de sesión y análisis de fallos (Fase 9)

La Fase 8 volcaba `stdout`/`stderr` a memoria y al ring buffer. Ambos se van con
el proceso, y un fallo de Box64 se diagnostica **después**.

`SessionLogRecorder` escribe `filesDir/logs/session-<juego>-<fecha>.log` con
`flush()` en cada línea: un buffer sin volcar no contiene las últimas líneas,
que son justo las interesantes. Tope de 8 MB con marca de truncado, para que
`WINEDEBUG=+relay` no llene el teléfono.

`CrashLogAnalyzer` lee la cola del archivo y busca ocho firmas, en orden de
gravedad:

| Firma | Patrón típico | Qué sugiere |
|---|---|---|
| `SEGFAULT` | `Segmentation fault`, `SIGSEGV`, `page fault on read` | dynarec de Box64 o binario de 16 bits |
| `UNHANDLED_EXCEPTION` | `wine: Unhandled exception`, `0xc0000005` | falta `vcrun2019` en el prefijo |
| `ILLEGAL_INSTRUCTION` | `Illegal instruction`, `SIGILL` | instrucción no implementada |
| `DEVICE_LOST` | `VK_ERROR_DEVICE_LOST` | driver Vulkan / resolución |
| `OUT_OF_MEMORY` | `bad_alloc`, `ENOMEM` | texturas o RAM del sistema |
| `MISSING_DLL` | `err:module:import_dll` | instalar con winetricks |
| `PERMISSION_DENIED` | `Permission denied` | permisos de `filesDir/runtime` |
| `MISSING_BINARY` | `BinaryNotInstalledException` | copiar el runtime |

Cuando no reconoce nada lo dice: «no se ha reconocido ningún patrón de fallo
conocido». Un diagnóstico inventado es peor que ninguno.

En la UI, la consola pasa a tener dos pestañas: **En vivo** (ring buffer) y
**Sesión** (archivos en disco, con el análisis arriba y el log debajo, coloreado
por `err:`/`error`/`fault`).

### `engine/profile/` — AutoOptimizer (Fase 4)

El optimizador responde a una pregunta: **qué perfil le conviene a este juego en
este dispositivo**. Funciona por puntuación, no por árbol de decisiones: cada
perfil suma puntos según las señales del hardware y del juego, y cada punto
lleva su motivo, así que la recomendación se explica en la UI.

**Señales del dispositivo** (`DeviceInfo`): memoria total, núcleos,
`isLowRamDevice`, versión de OpenGL ES y fabricante de la GPU. Android no expone
el nombre de la GPU por ninguna API pública, así que `GpuVendor` lo clasifica a
partir de las cadenas que el sistema sí publica (`SOC_MODEL`, `HARDWARE`,
`BOARD`…): `Mali-G57 MC2` o un `mt6789` dan Mali; `Adreno 642L` o un `sm7325`
dan Adreno. Es una pista, no una verdad: si no hay coincidencia, las reglas que
dependen de la GPU simplemente no puntúan.

**Señales del juego** (`GameProfileHints`): la API gráfica se deduce de las
librerías que acompañan al ejecutable (`d3d9.dll` → DirectX 9, `d3d11.dll` →
DirectX 11, `vulkan-1.dll` → Vulkan) y el género, del título. `GameProfileInspector`
enumera la carpeta en `Dispatchers.IO` **sin abrir, leer ni ejecutar nada**: si
no hay acceso, devuelve "no inspeccionado", que el optimizador trata como
ausencia de dato y no como un juego sin API gráfica.

Los diez perfiles:

| # | Perfil | Resolución | FPS | Backend | CPU | Para qué |
|---|---|---|---|---|---|---|
| 1 | Retro / DirectX 9 | 1024 × 768 | 60 | DirectX 9 | Normal | era DX9, backend ligero |
| 2 | DirectX 10/11 | 1280 × 720 | 60 | DirectX 10/11 | Alta | juegos que enlazan d3d10/d3d11 |
| 3 | Vulkan Base | 1280 × 720 | 60 | Vulkan | Alta | menos sobrecarga por llamada |
| 4 | Rendimiento RTS / DirectX 9 | 1024 × 768 | 30 | DirectX 9 | Alta | estrategia: CPU antes que efectos |
| 5 | Máximo Rendimiento | 1920 × 1080 | sin límite | Automático | Máxima | hardware sobrado |
| 6 | Ahorro de Energía | 800 × 600 | 30 | DirectX 9 | Baja | sesiones largas, poca batería |
| 7 | Compatibilidad Media | 1280 × 720 | 30 | Automático | Normal | opción segura sin datos |
| 8 | CPU Focus / Box64 | 1024 × 768 | 30 | DirectX 9 | Máxima | traducción en CPU |
| 9 | Audio Latencia Baja | 1280 × 720 | 60 | Automático | Alta | ritmo, conducción, disparos |
| 10 | Auto Personalizado | derivada | derivada | derivada | derivada | ninguno encaja: se calculan |

Cada perfil lleva además calidad de efectos y texturas, VSync, hilos de trabajo,
latencia de audio y presupuesto de memoria gráfica.

El caso del enunciado: **un RTS con gráficos DX9 en un SoC de gama media con
Mali** (8 núcleos, 6 GB, GL ES 3.2) suma 5 por género RTS, 2 por DirectX 9, 2 por
GPU móvil de gama media y 1 por núcleos, y gana con claridad el perfil
**"4. Rendimiento RTS / DirectX 9"**. Hay una prueba que lo fija
(`la peticion del ejemplo del proyecto se cumple con un juego RTS sobre Mali`).

Dos salidas especiales:

- **Backend inviable**: si el ganador pide Vulkan y la GPU no declara GL ES 3.1,
  se descarta y se deriva un perfil personalizado (es mejor una sesión que
  arranca que una recomendación optimista).
- **Empate sin señal fuerte**: si los dos primeros empatan por debajo de una
  señal concluyente, tampoco hay ganador claro y se deriva el perfil 10.

### `game/scan/` — el escáner

`GameScanner` expone dos recorridos que comparten la misma contabilidad y el
mismo criterio de selección:

| Método | Acceso | Permiso necesario |
|---|---|---|
| `scanTree(Uri)` | Storage Access Framework (`DocumentFile`) | **ninguno** |
| `scanTargets(List<ScanTarget>)` | Rutas del sistema de archivos | acceso amplio al almacenamiento |

El escaneo es una función `suspend` **cancellable**: comprueba la cancelación en
cada entrada visitada, así que abandonar la pantalla lo detiene en vez de
seguir consumiendo CPU.

`ExecutableFilter` decide qué es un juego usando **solo el nombre, la ruta y el
tamaño**. Nunca abre el archivo. Descarta:

- nombres exactos: `unins000.exe`, `setup.exe`, `install.exe`, `uninstall.exe`,
  `unwise.exe`, `vcredist_x64.exe`, `dxsetup.exe`, `oalinst.exe`, `physx.exe`,
  `dotnetfx35.exe`, `crashhandler.exe`…
- prefijos delatores: `unins`, `uninst`, `setup`, `install`, `redist`, `dotnet`…
- fragmentos de utilidad: `config`, `settings`, `patch`, `updater`, `helper`,
  `server`, `benchmark`, `editor`…
- carpetas que no se descienden: `_CommonRedist`, `DirectX`, `crashdumps`,
  `Support`, `Android`…

Y puntúa los candidatos (`ExecutableFilter.score`) para elegir el ejecutable
principal: la señal más fuerte es que el nombre del archivo contenga el de su
carpeta (`Half-Life 2/hl2.exe`), después los marcadores de motor
(`-Win64-Shipping`, convención de Unreal) y el tamaño.

**Un ejecutable por carpeta de instalación.** Un juego convive con su launcher y
su desinstalador; sin ese colapso la biblioteca mostraría tres entradas para un
solo juego. Los descartes se reportan en la UI para que no parezcan archivos no
vistos.

### `permission/` — Scoped Storage

Desde Android 11 (API 30) el almacenamiento con ámbito impide recorrer libremente
el almacenamiento compartido. Hay dos vías y están las dos:

1. **Storage Access Framework.** El usuario elige una carpeta con el selector del
   sistema y la app conserva el permiso de lectura de forma persistente
   (`takePersistableUriPermission`). **No requiere ningún permiso** y funciona
   también en microSD. Es la vía principal.
2. **Acceso amplio** (`MANAGE_EXTERNAL_STORAGE`, API 30+). Es la única forma de
   recorrer por ruta las carpetas habituales y la microSD. No se pide con un
   diálogo de runtime: se solicita llevando al usuario al ajuste del sistema.

`READ_EXTERNAL_STORAGE` se declara con `maxSdkVersion="32"`: por debajo es el
permiso clásico de lectura, a partir de API 33 el sistema lo ignora.

> ⚠️ Google Play restringe `MANAGE_EXTERNAL_STORAGE` a un conjunto cerrado de
> categorías y exige declarar el uso al publicar. En dispositivos o canales donde
> no se conceda, NEXORA **sigue funcionando** con "Escanear carpeta", que no
> necesita permisos. Si vas a distribuir por Play, revisa su política antes de
> depender del acceso amplio.

## Pantalla principal

```
NEXORA
Windows Gaming Runtime for Android

MIS JUEGOS
┌───────────────┐  ┌───────────────┐
│      🎮       │  │      🕹️       │
│ Hollow Knight │  │    DOOM       │
│ 48.2 MB · …   │  │  412 MB · …   │
│ [▶ JUGAR]  ⚙ │  │ [▶ JUGAR]  ⚙ │
└───────────────┘  └───────────────┘
[ VACIAR BIBLIOTECA ]

[ 📂  ESCANEAR CARPETA ]     <- sin permisos (SAF)
[ 🔍  BUSCAR JUEGOS ]        <- carpetas habituales + microSD

ACCESO AL ALMACENAMIENTO
┌────────────────────────────────────┐
│ 🔓 Acceso amplio concedido          │
│ 2 carpetas concedidas · …           │
└────────────────────────────────────┘
```

Al tocar una tarjeta:

```
← Detalle del juego            Hollow Knight

┌────────┐  Hollow Knight
│   🕹️   │  hollow_knight.exe
└────────┘  48.2 MB

EJECUTABLE
Archivo                 hollow_knight.exe
Carpeta de instalación  /storage/…/Hollow Knight
Tamaño                  48.2 MB
Origen                  Detectado en un escaneo
Añadido                 29 ene 2024, 18:26
Acceso al archivo       Documento del Storage Access Framework
Ruta completa
/storage/emulated/0/Games/Hollow Knight/hollow_knight.exe

RENDIMIENTO
┌────────────────────────────────────────┐
│ ✨ Perfil recomendado por AutoOptimizer │
│ 7. Compatibilidad Media          [AUTO] │
│ Opción segura: no asume nada del …     │
│ Resolución sugerida    1280 × 720      │
│ Límite de FPS          30 fps          │
│ …                                      │
│ [ CAMBIAR PERFIL ]                     │
└────────────────────────────────────────┘

CONTROLES TÁCTILES
┌────────────────────────────────────────┐
│ 🎮 Capa de controles (HUD)              │
│ 4 de 8 controles visibles               │
│ [ EDITAR CONTROLES ]                    │
└────────────────────────────────────────┘

OPCIONES INICIALES
[ ▶ JUGAR                    ]   <- runtime no integrado todavía
[ 🎨 CAMBIAR ÍCONO           ]
[ ⧉ COPIAR RUTA DEL EJECUTABLE]
   Quitar de la biblioteca       <- con confirmación
```

Y desde la tarjeta de controles, el editor:

```
← Editor de controles              Hollow Knight

MODO                    [ EDITAR | PROBAR ]
┌──────────────────────────────────────────┐
│  ⌜ selección en vivo ⌟                   │
│                             [Ctrl+2]     │
│                                          │
│            ✛ cursor virtual              │
│                                   [A]    │
│                                  [Esp]   │
│  ╭─────╮                                 │
│  │  ↑  │                                 │
│  │← ↓ →│                                 │
│  ╰─────╯                                 │
└──────────────────────────────────────────┘
Ctrl+1 Ctrl+2 Ctrl+3 Shift A Espacio D-Pad Joystick
        <- tocar una ficha la selecciona

CONTROL SELECCIONADO: Espacio
Posición X        ──────●────  0,82
Posición Y        ───────●───  0,74
Tamaño            ────●──────  0,13
Visible           [ ON  ]
[ OCULTAR CONTROL ]

OPACIDAD DE LA CAPA  ───────●─  78 %
Recuadro de selección [ ON  ]
[ GUARDAR ]  [ RESTABLECER ]

REGISTRO DE GESTOS
Cursor a (412, 233)
Clic izquierdo en (412, 233)
Recuadro (180, 90) → (520, 420)
Clic derecho en (350, 255)
Zoom ×1,60 en (360, 240)
Cámara +18, -6
```

Y desde el detalle del juego, las tres pantallas nuevas de la Fase 7:

```
← Partidas guardadas             Hollow Knight

GUARDADO DEL PREFIJO
Partidas detectadas    12 archivos · 4,3 MB
Prefijo                filesDir/prefixes/hollow-knight

[ ⬆ EXPORTAR A ZIP ]  [ ⬇ IMPORTAR ZIP ]

CACHÉ DE SOMBRADO (DXVK)
2 archivos · 384 KB       [ LIMPIAR CACHÉ ]
TEMPORALES
7 archivos recientes      [ LIMPIAR TEMPORALES ]
```

```
← Diagnóstico                          NEXORA

NIVEL MÍNIMO   [ DEBUG | INFO | WARN | ERROR ]
12:04:11  INFO   runtime   Entorno compuesto: 14 variables
12:04:11  WARN   audio     Buffer de 96 frames: riesgo de chasquidos
12:04:12  ERROR  engine    BinaryNotInstalledException: wine64

[ ⬆ EXPORTAR .TXT ]  [ ↗ COMPARTIR ]  [ 🗑 VACIAR ]
```

```
← Ajustes globales

APARIENCIA
Tema            [ OSCURO | CLARO | SISTEMA ]
Animaciones     [ ON ]

DIAGNÓSTICO
Autoexportar log al fallar  [ OFF ]

ALMACENAMIENTO
Prefijos de Wine        18,4 MB
Caché de sombreado       384 KB   [ LIMPIAR ]

RESETEO
[ RESTABLECER AJUSTES ]  [ BORRAR CONTROLES ]
[ VACIAR BIBLIOTECA ]        <- con confirmación
```

La barra de progreso del escaneo es **determinada**: mide la fracción del
presupuesto de entradas consumida, no un porcentaje inventado del árbol (que no
se conoce de antemano). Los contadores y la ruta actual acompañan a la barra
porque un escaneo sobre microSD se percibe mucho más corto si se ve que avanza.

## Límites del escaneo

| Parámetro | Valor por defecto | Motivo |
|---|---|---|
| `maxDepth` | 8 | profundidad de directorios descendida |
| `entryBudget` | 60 000 | entradas visitadas; al agotarse el resultado se marca como truncado |
| `maxResults` | 200 | juegos devueltos |
| `maxGamesPerFolder` | 1 | colapsa launcher + juego en una entrada |
| `progressInterval` | 40 | entradas entre dos emisiones de progreso |

Sin presupuesto, un escaneo sobre una microSD de 512 GB puede tardar minutos y
dejar la UI congelada. Con presupuesto, el resultado es siempre acotado y la
interfaz avisa explícitamente de que puede haber juegos sin visitar.

## Paleta

| Rol | Nombre | Hex |
|---|---|---|
| Fondo | Casi negro | `#080A0E` |
| Superficie | Gris azulado oscuro | `#12161F` |
| Acento principal | Verde neón | `#00E676` |
| Acento secundario | Violeta | `#7C5CFF` |
| Texto principal | Blanco frío | `#E8ECF4` |
| Texto secundario | Gris | `#8A93A6` |
| Borde | Gris oscuro | `#232A38` |

## Compilar

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

APK en `app/build/outputs/apk/debug/app-debug.apk`.

En cada push a `main`, GitHub Actions compila y publica el artefacto
`NEXORA-APK`.

## Decisiones de la Fase 3

- **Room, no DataStore.** La biblioteca es una lista estructurada que se consulta
  por título, se deduplica por ruta y se actualiza campo a campo: eso es SQL, no
  pares clave-valor. DataStore sigue siendo la opción para los ajustes
  (`AppSettings`), que todavía no se editan desde la UI.
- **Sin Navigation Compose.** Dos pantallas y un `BackHandler`: el grafo pesaría
  más que la navegación. Si en una fase posterior aparecen más destinos, se
  migra sin tocar el ViewModel.
- **El botón "Jugar" existe y es funcional, pero no lanza nada.** Está cableado
  al ViewModel y avisa al usuario: así la tarjeta y la pantalla de detalle no
  cambian cuando se integre el runtime.
- **El ícono es un preset, no una carátula.** Un juego detectado no trae imagen y
  NEXORA no lee los recursos del `.exe` (eso exigiría interpretar un binario de
  Windows). El preset se dibuja con un degradado y se persiste.
- **La grilla es el contenedor de toda la página**, no una lista anidada.
- **El detalle vuelve solo a la biblioteca si el juego se borra**: la navegación
  se deriva de la lista de la base de datos, no de un id guardado a ciegas.

## Verificación

```
./gradlew assembleDebug      -> BUILD SUCCESSFUL
./gradlew assembleRelease    -> BUILD SUCCESSFUL
./gradlew testDebugUnitTest  -> 228 tests, 0 failures, 0 errors
```

Reparto de pruebas: 9 del escáner (Fase 2), 8 del mapeo de la entidad + perfil
(Fase 3/4), 9 del AutoOptimizer y 10 del catálogo de perfiles y la detección de
pistas (Fase 4), 11 del motor de gestos y 8 del layout de controles (Fase 5),
26 del entorno y del motor (Fase 6), **27 de la Fase 7** (13 de audio, 8 de
partidas y caché, 6 de diagnóstico), **51 de la Fase 8** (12 de binarios y
manifiesto, 14 de ejecución y entorno, 19 de entrada táctil y 6 de
configuración de audio) y **69 de la Fase 9** (9 de geometría del HUD, 7 de
métricas y umbrales, 6 de parseo de `DXVK_HUD`, 8 de escaneo de partidas, 6 de
nombres de respaldo, 8 del almacén de respaldos, 6 del registro de sesión, 6 de
lectura de logs y 13 del analizador de fallos). Todas JVM puras: ni Robolectric
ni instrumentación, porque ninguna de estas clases toca Android; las que sí lo
tocan (`AudioTrackSink`, `RuntimeBinaryLoader`, `EvdevInputTransport`,
`OverlayService`) son envoltorios finos sobre APIs del sistema, y su lógica de
decisión vive en clases puras.

La compilación queda **sin avisos**: los cinco usos de `Icons.Outlined.ArrowBack`
se migraron a `Icons.AutoMirrored.Outlined.ArrowBack`, que sí existe en
`material-icons-core` 1.6.1 (en `material-icons-extended` solo está
`ArrowBackIos`, de ahí la confusión de la Fase 7).

Auditoría de librerías de escritorio, en dos niveles:

```
grep -rIn "javax.swing|javax.awt|java.awt" app/src   ->  0 resultados
strings classes*.dex | grep -iE "javax/swing|java/awt" ->  0 resultados
```

Los únicos imports del proyecto son `android.*`, `androidx.*`,
`com.nexora.android.*`, `kotlin(x).*` y `java.{io,nio,util,text}`.

## Decisiones de la Fase 4

- **Kotlin puro en la capa de perfiles.** `PerformanceProfile`, `GameProfileHints`
  y `AutoOptimizer` no importan nada de Android: se prueban en JVM y se pueden
  reutilizar cuando el motor exista sin arrastrar dependencias de UI.
- **Puntuación con motivos, no árbol de decisiones.** Cada regla es
  independiente, así que cambiar el peso de una señal no reescribe las demás; y
  la UI puede explicar por qué se recomienda algo en vez de pedir confianza
  ciega.
- **La GPU se deduce, no se consulta.** Android no expone el modelo de GPU sin
  crear un contexto OpenGL; hacerlo solo para rellenar un dato informativo no
  compensa. La clasificación por cadenas del sistema es suficiente para decidir
  entre rutas gráficas y no cuesta nada.
- **La API del juego se deduce de las librerías, no del binario.** Interpretar un
  ejecutable de Windows está fuera del alcance de NEXORA; enumerar la carpeta
  (lo que ya hacía el escáner) da la misma señal sin riesgo.
- **Identificadores, no ordinales.** El perfil se guarda por `id` y se resuelve
  con `PerformanceProfile.fromId`: añadir o reordenar perfiles no reinterpreta
  bibliotecas guardadas.
- **Migración aditiva, sin `destructiveMigration`.** Una columna nullable nueva
  no justifica borrar la biblioteca del usuario.
- **Los perfiles no se aplican a nada todavía.** Se calculan, se muestran y se
  guardan; el runtime que los consumirá llega en una fase posterior.

## Decisiones de la Fase 5

- **El motor de gestos es una máquina de estados sin Android ni Compose.**
  `TouchGestureInterpreter` recibe `down`/`move`/`up` con posición y marca de
  tiempo y devuelve eventos: por eso se prueba en JVM (11 pruebas) y por eso la
  misma clase sirve en modo juego y en modo edición.
- **El recuadro se dibuja en vivo y se emite al soltar.** La UI lee
  `selectionBox` en cada frame para pintar la caja, pero el
  `InputEvent.SelectionRect` sale una sola vez, con el rectángulo final: el
  runtime recibirá un único "selecciona esto", no cien actualizaciones.
- **Un movimiento de dos dedos se descompone** en la parte que arrastra (los dos
  van a la vez) y la parte que separa (se abren o se cierran). Gana la que antes
  supera su umbral y, si empatan, se interpreta como zoom: así un paneo real no
  se convierte en un pellizco cuando los dedos llegan con unos pocos
  milisegundos de diferencia.
- **Los dedos que empiezan sobre un botón no existen para el motor.** La capa
  calcula los rectángulos de los controles en píxeles y se los pasa al
  intérprete, que ignora los punteros nacidos dentro de uno: sin eso, pulsar
  `Ctrl+1` además movería el cursor o abriría un recuadro.
- **Dos dedos cancelan el recuadro y el último dedo no cierra nada.** Al
  levantar uno de dos, el que queda se reancla y se marca como ya movido; el
  clic izquierdo queda suprimido hasta que la pantalla quede vacía. Es lo que
  evita los clics fantasma al terminar un pellizco.
- **Coordenadas normalizadas.** El centro del botón va de 0 a 1 y su tamaño es
  una fracción del lado corto, así que el layout sobrevive a un cambio de
  resolución, de orientación o de dispositivo sin recalcular nada.
- **Dos tablas, no una.** El perfil (`control_profiles`) guarda la opacidad y el
  interruptor del recuadro; los botones (`control_buttons`) guardan posición,
  tamaño y visibilidad. Cambiar la opacidad no reescribe ocho filas.
- **Los controles se mueven en memoria y se guardan al salir.** Arrastrar un
  botón actualiza el estado, no la base de datos: el editor escribe una sola vez
  al pulsar Guardar o al volver.
- **Todavía no hay a quién enviar los eventos.** Los eventos se describen en el
  registro del editor (`Cursor a (x, y)`, `Clic izquierdo`, `Zoom ×1,60`, …) pero
  no llegan a ningún runtime: el puente real con el proceso de Windows es trabajo
  de la fase que integre la ejecución.

## Decisiones de la Fase 6

- **El orquestador es puro-Kotlin y testeable en JVM.** `RuntimeEngine` no
  importa nada de Android ni de Compose: usa `ProcessLauncher`,
  `LogStreamer` y `BinaryResolver` como `interface`s, y `NoopProcessLauncher`
  + `NoopProcessHandle` permiten reproducir cada transición (idle → preparing
  → running → paused → stopped) sin un `.exe`. El paquete `engine` se
  prueba sin dispositivo (8 pruebas).
- **El resolver de binarios es honesto por defecto.** `AndroidBinaryResolver`
  siempre devolvía `null`, lo que dispara `BinaryNotInstalledException` en
  el launcher (la Fase 8 lo sustituye por `FilesystemBinaryResolver`, que
  devuelve `null` cuando no hay nada ejecutable). Es deliberado: en un Android sin root y sin conector NDK, no
  hay `wine`, `wine64` ni `box64` instalados; mejor fallar visiblemente
  que fingir éxito. La `fun interface BinaryResolver` deja la puerta
  abierta a un módulo NDK/JNI que la provea.
- **Las variables de entorno se calculan con una función pura.**
  `EnvironmentComposer.compose(gameId, exePath, parameters, profileId,
  settings, prefixPathFor)` no toca el disco ni arranca procesos: a partir
  de los parámetros del perfil y de los flags elegidos, devuelve un
  `ComposedEnvironment(environment, warnings, command, winePrefixPath)`
  inmutable. Eso permite probar todas las combinaciones (DXVK solo para
  DX9/10/11, `BOX64_DYNAREC_BIGBLOCK=2` cuando hay ≥6 hilos,
  `WINEDEBUG` solo si el usuario lo activa, …) en 13 pruebas.
- **WINEPREFIX vive dentro de `filesDir`, no en `/storage`.** Cada juego
  tiene su propio prefijo bajo `filesDir/prefixes/<gameId>`. `WinePrefix`
  es idempotente: `createIfMissing()` solo crea los directorios que faltan
  y devuelve los recién creados. Sin permisos extra, sin choques entre
  juegos.
- **`Paused` es un marcador de UI, no un SIGSTOP.** Android no expone la
  señal desde `java.lang.Process`, así que `RuntimeEngine.pause()` cambia
  el `RuntimeStatus` pero **el proceso sigue corriendo**; lo que se
  detiene es el envío de input y la captura activa de logs. La
  documentación de `RuntimeStatus.Paused` lo deja explícito para que no se
  confunda con una suspensión real.
- **El PID se obtiene por reflexión.** `java.lang.Process.pid()` /
  `Process.toHandle().pid()` son Java 9+; el runtime de Android no las
  expone en el stub de la `Process`. `AndroidProcessHandle.pid` las busca
  por reflexión y devuelve `-1L` si ninguna aparece, así que el código no
  se rompe cuando cambia el stub.
- **Logs por canal, no por prioridad.** `LogStreamer` mantiene un
  `Channel` con buffer (`DEFAULT_CAPACITY = 512`) y un `pump` por flujo
  (stdout, stderr). El consumidor hace `take(N).toList()` o `collect` y
  recibe `RuntimeLog(timestampMs, stream, message)` con el sello de canal;
  la UI colorea stdout, stderr y SYSTEM por separado y descarta lo que
  no cabe.
- **El monitor "estima", no lee.** Android no expone sensor térmico de
  CPU/GPU sin permisos extra, así que `AndroidRuntimeMonitorSource` calcula
  un valor entre 40 y 90 °C a partir de la presión de RAM, redondeado a
  múltiplos de 5, y la UI lo marca como `(estimado)`. FPS sí se mide de
  verdad con un `Choreographer.FrameCallback` sobre una ventana móvil de
  30 frames.
- **Navegación por estado, no por Navigation Compose.** La pantalla de
  ejecución se activa con un único campo `runningGameId` en
  `ScannerUiState`; el `BackHandler` se encadena (`runner > editor >
  detalle > biblioteca`). Volver cierra el motor (`RuntimeViewModel.
  shutdown()` cancela los `Job`, mata el proceso si estaba vivo y libera
  el monitor) antes de quitar la pantalla, para que no se quede nada
  colgado.
- **Migración aditiva 3 → 4.** Solo se añade la tabla `runtime_configs`,
  sin alterar las anteriores: los datos de las Fases 3-5 sobreviven
  intactos a la actualización.

## Decisiones de la Fase 7

- **El audio se compone, no se reproduce.** `AudioComposer` es una función
  pura que devuelve variables de entorno, avisos y latencia: se prueba en JVM
  (13 pruebas) y no necesita un `AudioTrack` ni un permiso. Cuando el motor
  exista, esas variables son exactamente las que leerá; hoy describen la
  intención sin fingir que suena algo. La Fase 7 **no** abre ningún
  `AudioTrack`, `Oboe` ni `AAudio`: eso llegaría con el conector JNI/NDK, y
  hacerlo antes sería escribir un reproductor que nadie va a usar.
- **La latencia se calcula, no se promete.** `frames * 1000 / hz` es aritmética
  de buffer: es el dato honesto que se puede dar sin medir el dispositivo. El
  aviso de chasquidos aparece por debajo de ~4 ms porque ahí empieza el
  riesgo real en un SoC cargado, y se muestra **junto** al selector para que
  el usuario entienda el cambio que está haciendo.
- **El ZIP de partidas valida antes de escribir.** `SaveImporter` rechaza
  entradas absolutas y con `..`; un ZIP de origen desconocido es una entrada
  de red más, y el *zip-slip* es el fallo clásico de este patrón. Lo que se
  rechaza se lista en la UI en vez de descartarse en silencio.
- **La caché de sombreado es la única que se borra automáticamente.** DXVK
  regenera su `*.dxvk-cache` en la siguiente sesión; una partida guardada no se
  regenera. El limpiador distingue las dos cosas y nunca toca directorios de
  guardado.
- **El registro se acota en memoria.** Un ring buffer de 500 eventos: una
  sesión de varias horas no crece sin límite y, cuando el motor falla, lo que
  se exporta es justo el tramo final que explica el fallo. El `.txt` se
  comparte por `FileProvider` porque exponer `file://` a otra app lanza
  `FileUriExposedException` desde Android 7.
- **Migración aditiva 4 → 5.** Dos tablas nuevas (`audio_settings` y
  `global_preferences`), sin `ALTER` sobre las existentes: biblioteca, perfiles,
  controles y configuración de entorno de las Fases 3-6 siguen intactos.
- **Las transiciones se derivan del estado, no de una pila.** `AnimatedContent`
  decide la dirección comparando el destino actual con el anterior, así que no
  hay un historial de navegación que mantener sincronizado; y si el usuario
  apaga las animaciones, el cambio es instantáneo y no queda código muerto.

## Decisiones de la Fase 8

- **La maquinaria es real; los binarios no se redistribuyen.** Wine, Box64,
  DXVK y Turnip pesan cientos de megabytes, tienen licencias propias y dependen
  del SoC. Lo honesto es implementar la extracción, la verificación y el
  lanzamiento, y dejar el paquete como algo que el usuario o el fabricante
  aporta. Si no está, el error es explícito y accionable.
- **`.part` + `renameTo`, no escritura directa.** Un binario a medio copiar es
  peor que ninguno: falla en el momento menos oportuno y con un mensaje que no
  explica nada. El renombrado es atómico en el mismo sistema de archivos.
- **SHA-256 antes que permisos.** Marcar como ejecutable un archivo que no es
  el que se esperaba es exactamente cómo se instala un binario ajeno. La huella
  se comprueba sobre el `.part`; si no coincide, se borra.
- **`setExecutable(true, true)`.** `chmod 777` es la tentación y es un error:
  otro proceso del dispositivo no necesita ejecutar los binarios de la app.
- **El traductor se decide por arquitectura, no por preferencia.** Box64 en un
  x86_64 solo añade latencia. En ARM es obligatorio para un `.exe` x86_64, pero
  si falta se avisa y se intenta igual: puede ser un binario ARM.
- **`cwd` = carpeta del `.exe`.** Muchos juegos resuelven rutas relativas a su
  directorio; arrancarlos desde `filesDir` hace que no encuentren sus assets.
- **Se escribe `input_event` a mano, sin JNI.** Son 24 bytes little-endian y
  es lo mismo que hace `sendevent`. Crear el dispositivo sí exige `ioctl`, y
  por eso el puente usa un nodo existente y reporta cuando no hay ninguno.
- **El zoom es rueda, no botón.** Un pellizco en un RTS es zoom de cámara; el
  juego lo entiende como `REL_WHEEL`. Se limitan las muescas a ±3 por gesto
  para no inundar la cola del kernel con un pellizco rápido.
- **`AudioTrack` no bloquea el arranque del juego.** Si el audio falla, la
  sesión sigue: se pierde el sonido, no la partida. El estado se muestra en la
  tarjeta "Motor nativo" con la latencia real y los *underruns*.
- **El FIFO es el puente más simple que funciona.** Un socket o un servicio
  NDK darían más control, pero un FIFO no necesita protocolo, ni framing, ni
  JNI: el motor escribe PCM y la app lo lee. Cuando el motor tenga su propio
  sink, se cambia la implementación de `AudioPcmSource`.

## Decisiones de la Fase 9

- **El HUD es una ventana, no un composable más.** Un overlay dibujado dentro de
  la Activity desaparece en cuanto el juego ocupa la pantalla. `TYPE_APPLICATION_OVERLAY`
  es la única forma de que siga visible, y eso obliga a un servicio en primer
  plano y a un `LifecycleOwner` propio.
- **Los FPS se etiquetan, no se inventan.** `motor` cuando los publica
  `DXVK_HUD`, `overlay` cuando los mide el `Choreographer`. Un 60 fijo en
  pantalla sería la mentira más fácil de esta fase.
- **El cierre de emergencia es una orden, no un `kill` directo.** El servicio
  no tiene el `Process`; el ViewModel sí. Publicar la orden en un bus mantiene
  el servicio sin dependencias del motor y permite decir "no hay sesión" cuando
  no la hay.
- **Arrastrar solo desde el asa.** Si toda la tarjeta arrastrara, pulsar
  "cerrar" con el dedo en movimiento movería la ventana en vez de cerrarla.
- **La geometría se prueba en JVM.** `OverlayGeometry` no conoce Android, así
  que los bordes, el truncado y el anclaje se verifican sin dispositivo. Es la
  parte del HUD que de verdad puede fallar en silencio.
- **El respaldo es relativo a `drive_c`.** El prefijo `nexora/<id>/` de la Fase 7
  hacía que restaurar creara una carpeta nueva en vez de sobrescribir la
  partida. Exportar e importar tienen que ser la misma operación al revés.
- **Antes de restaurar, se respalda.** Restaurar el ZIP equivocado sin red de
  seguridad es perder la partida en un toque.
- **El nombre del archivo es la base de datos.** Sin índice que mantener, un
  respaldo copiado a mano sigue siendo válido. Un ZIP con nombre desconocido no
  se lista, pero tampoco se borra: no es asunto de la app decidir eso.
- **`flush()` por línea en el log de sesión.** Cuesta poco y es la diferencia
  entre tener las últimas líneas de un cuelgue o no tenerlas.
- **El analizador dice "no lo sé".** Ocho firmas cubren lo reconocible; para
  todo lo demás, el log completo está a un toque. Inventar una causa sería peor
  que no dar ninguna.
- **`Settings.canDrawOverlays` es la única verdad del permiso.** No se guarda en
  preferencias: el usuario puede revocarlo en Ajustes sin avisar, y la pantalla
  lo relee en cada `ON_RESUME`.

## Fase 10 (fuera de esta versión)

- **Empaquetar los binarios** de Wine / Box64 (o FEX-Emu) / DXVK / Turnip y
  firmar el manifiesto con una clave de release. La Fase 8 ya los instala,
  verifica y lanza; lo que falta es el paquete.
- **Dispositivo `uinput` propio** (`UI_DEV_SETUP` + `UI_DEV_CREATE`), que exige
  `ioctl` y por tanto un módulo JNI/NDK. Hoy se escribe en un nodo existente.
- **Pausa real** (SIGSTOP/ptrace) en lugar del marcador de UI actual.
  Android no expone el API desde la JVM y el motor lo deja documentado.
- **Alta manual de juegos** desde un `.exe` arbitrario.
- **Ajustes finos de audio** (DPI de los controles, idioma, límite global de
  FPS) y salida Oboe/AAudio nativa en lugar de `AudioTrack`.
- **Métricas del proceso del juego**, no del overlay: hoy la RAM/CPU son del
  sistema y los FPS del motor solo si `DXVK_HUD` los publica. Leer
  `/proc/<pid>/stat` del proceso hijo daría cifras exactas del juego.
- **Sincronización de respaldos** (nube o carpeta compartida) y comparación de
  dos respaldos antes de restaurar.
- **Cifrado del prefijo** y migración multi-juego.
