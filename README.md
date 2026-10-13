# NEXORA v0.7 — Fase 7

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
- **Fase 7 (v0.7, esta versión):** audio de **baja latencia** (backend
  AAudio / OpenSL ES / PulseAudio, frecuencia 44,1 / 48 kHz y buffer en
  frames con cálculo de latencia real), **caché y partidas guardadas**
  (exportación e importación de `Savegames` en ZIP, limpieza de la caché de
  sombreado DXVK y de temporales), **consola de diagnóstico** con visor en
  vivo y exportación del registro a `.txt`, **ajustes globales** (tema,
  animaciones, autoguardado de logs, almacenamiento y reseteo) y
  **animaciones** de transición entre biblioteca, detalle y ejecución.

> **El runtime sigue sin traer un motor real.** No hay Wine, Box64, FEX-Emu,
> DXVK ni drivers Mesa/Turnip empaquetados, y **no se ejecuta ningún binario
> de Windows**: el launcher (`AndroidBinaryResolver`) devuelve siempre `null`
> y el motor falla de forma explícita con `BinaryNotInstalledException`. La
> UI muestra la causa y los pasos para integrar el conector JNI/NDK en una
> fase posterior. La Fase 7 tampoco toca el sistema de audio de Android:
> compone las **variables de entorno** que consumirá el motor cuando exista.
>
> **No avanza a la Fase 8.** El envío real de los `InputEvent` al proceso
> del juego, el empaquetado de los binarios y la capa JNI/NDK son trabajo
> de una versión posterior.

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
├── runtime/                    # orquestador del motor (Fase 6) + audio/caché/diagnóstico (Fase 7)
│   ├── audio/                  # AudioBackend, AudioSampleRate, AudioBufferFrames,
│   │                           # AudioSettings, AudioComposer (función pura)
│   ├── saves/                  # SaveBackupArchive, SaveImporter,
│   │                           # ShaderCacheCleaner, TempFileCleaner
│   ├── diagnostics/            # DiagnosticsEvent, DiagnosticsLogger (ring buffer),
│   │                           # LogExporter (.txt)
│   ├── preferences/            # AppTheme + GlobalPreferences
│   └── repository/             # AudioSettingsRepository, GlobalPreferencesRepository
├── ui/
│   ├── saves/SavesViewModel.kt          # exportar / importar / limpiar
│   ├── diagnostics/DiagnosticsViewModel.kt
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
controles (Fase 5) con un eventual binario de Wine/Box64/DXVK. Hoy no trae
los binarios: está diseñada como un **conjunto de `interface`s + una
implementación honesta** que falla de forma visible cuando el conector JNI/NDK
no resuelve nada.

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
./gradlew testDebugUnitTest  -> 108 tests, 0 failures, 0 errors
```

Reparto de pruebas: 9 del escáner (Fase 2), 8 del mapeo de la entidad + perfil
(Fase 3/4), 9 del AutoOptimizer y 10 del catálogo de perfiles y la detección de
pistas (Fase 4), 11 del motor de gestos y 8 del layout de controles (Fase 5),
26 del entorno y del motor (Fase 6) y **27 de la Fase 7** (13 de audio, 8 de
partidas y caché, 6 de diagnóstico). Todas JVM puras: ni Robolectric ni
instrumentación, porque ninguna de estas clases toca Android.

Los únicos avisos del compilador son cuatro **desusos de
`Icons.Outlined.ArrowBack`**: el BOM 2024.02.00 (`material-icons-extended
1.6.1`) marca el icono como obsoleto apuntando a
`Icons.AutoMirrored.Outlined.ArrowBack`, pero esa variante **no existe** en el
artefacto (solo `ArrowBackIos`), así que se mantiene la versión sin espejo.

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
  siempre devuelve `null`, lo que dispara `BinaryNotInstalledException` en
  el launcher. Es deliberado: en un Android sin root y sin conector NDK, no
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

## Fase 8 (fuera de esta versión)

- Empaquetado de los binarios de **Wine / Box64 (o FEX-Emu) / DXVK / drivers
  Mesa-Turnip** y conector **JNI/NDK** que materialice el
  `BinaryResolver`. La capa actual asume esa pieza como una `fun interface`
  y falla de forma explícita cuando el resolver devuelve `null`.
- **Envío real de los `InputEvent`** al proceso del juego (`wine64` recibe
  punteros sintéticos, DInput/uinput, etc.). Hoy los gestos se reconocen y
  quedan en memoria: no llegan a ningún `.exe`.
- **Pausa real** (SIGSTOP/ptrace) en lugar del marcador de UI actual.
  Android no expone el API desde la JVM y el motor lo deja documentado.
- **Alta manual de juegos** desde un `.exe` arbitrario.
- **Ajustes finos de audio** (DPI de los controles, idioma, límite global de
  FPS) y apertura real de un `AudioTrack`/Oboe: la Fase 7 compone las
  variables, pero no reproduce nada.
- **Cifrado del prefijo** y migración multi-juego.
