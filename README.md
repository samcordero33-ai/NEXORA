# NEXORA v0.5 — Fase 5

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
- **Fase 5 (v0.5, esta versión):** controles táctiles virtuales y gestos en
  pantalla (**HUD Overlay**) optimizados para RTS, con ratón virtual, recuadro
  de selección, zoom por pellizco y perfiles de control por juego en Room.

> **No hay runtime de ejecución.** No hay Wine, Box64, DXVK ni ningún motor de
> traducción, y **tampoco se ejecuta ningún binario de Windows**: el escáner y
> el inspector de perfiles solo leen nombres, rutas y tamaños de archivo, y el
> botón "Jugar" informa de que el lanzamiento llega en una fase posterior.
>
> **No avanza a la Fase 6.** Los gestos se reconocen y se registran, pero no se
> inyectan en ningún juego: no hay motor al que enviarlos.

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
│   │   └── NexoraDatabase.kt   # RoomDatabase v3 + migraciones 1→2→3 (singleton)
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
./gradlew assembleDebug      -> BUILD SUCCESSFUL (sin warnings)
./gradlew assembleRelease    -> BUILD SUCCESSFUL (sin warnings)
./gradlew testDebugUnitTest  -> 55 tests, 0 failures
```

Reparto de pruebas: 9 del escáner (Fase 2), 8 del mapeo de la entidad + perfil
(Fase 3/4), 9 del AutoOptimizer y 10 del catálogo de perfiles y la detección de
pistas (Fase 4), 11 del motor de gestos y 8 del layout de controles (Fase 5).
Todas JVM puras: ni Robolectric ni instrumentación, porque ninguna de estas
clases toca Android.

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

## Fase 6 (fuera de esta versión)

- Integración del runtime de ejecución (y con ella el lanzamiento real de
  "Jugar", la aplicación efectiva de los perfiles y el envío de los `InputEvent`
  al proceso de Windows).
- Alta manual de juegos.
- Pantalla de ajustes persistidos (DataStore).
