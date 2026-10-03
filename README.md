# NEXORA v0.3 — Fase 3

**Windows Gaming Runtime for Android.**

NEXORA es una aplicación Android para ejecutar juegos de PC en móviles.

- **Fase 1 (v0.1):** estructura del proyecto, interfaz gaming oscura y
  detección del dispositivo.
- **Fase 2 (v0.2):** escaneo de almacenamiento local y microSD para detectar
  juegos instalados (`.exe`), gestión de permisos y progreso en la UI.
- **Fase 3 (v0.3, esta versión):** persistencia local de la biblioteca con Room,
  grilla de tarjetas gaming y pantalla de detalle del juego.

> **No hay Fase 3 de runtime.** No hay Wine, Box64, DXVK ni ningún motor de
> traducción, y **tampoco se ejecuta ningún binario de Windows**: el escáner
> solo lee nombres, rutas y tamaños de archivo y el botón "Jugar" informa de que
> el lanzamiento llega en una fase posterior.
>
> **No avanza a la Fase 4.** El runtime de ejecución queda fuera de esta
> versión.

## Requisitos cumplidos

| Requisito | Estado |
|---|---|
| 100 % Kotlin nativo + Jetpack Compose + Android SDK | ✔ |
| Sin librerías de escritorio Java (`javax.swing.*`, `java.awt.*`) | ✔ verificado en fuentes **y en los DEX del APK** |
| Sin Wine / Box64 / DXVK | ✔ |
| No se ejecuta ningún binario de Windows | ✔ el escáner solo lee metadatos |
| Persistencia local (Room) con entidad `Game` (id, título, ruta del exe, ícono predeterminado, fecha de agregado) | ✔ `GameEntity` + `GameDao` + `NexoraDatabase` v1 |
| Los juegos detectados sobreviven al reinicio | ✔ la biblioteca se lee de SQLite, no de memoria |
| Interfaz de biblioteca en grilla de tarjetas gaming | ✔ `LazyVerticalGrid` + `GameCard` |
| Cada tarjeta: nombre, ícono, "Jugar", "Ajustes del juego" | ✔ |
| Estado vacío limpio | ✔ |
| Pantalla de detalle con información del ejecutable y opciones iniciales | ✔ `GameDetailScreen` |
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
│   │                           # StorageAccessCard
│   ├── util/Format.kt          # formateo de tamaños y fechas
│   └── theme/{Color,Theme,Type}.kt
├── game/
│   ├── model/
│   │   ├── Game.kt             # Game + GameSource + GameLibraryState
│   │   └── GameIcon.kt         # GameIconPreset (íconos predeterminados)
│   ├── data/local/
│   │   ├── GameEntity.kt       # entidad Room + mapeo a dominio
│   │   ├── GameDao.kt          # consultas y Flow sobre la tabla
│   │   └── NexoraDatabase.kt   # RoomDatabase v1 (singleton)
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
│   ├── DeviceInfo.kt
│   └── DeviceInfoProvider.kt
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
| `addedAt` | fecha de agregado, en milisegundos de época |
| `installDirectory`, `sizeBytes`, `source`, `documentUri` | metadatos del escáner (Fase 2) que muestra el detalle |

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
de instalación, tamaño, origen, fecha de alta, tipo de acceso y ruta completa) y
sus **opciones iniciales**: jugar, cambiar el ícono (persistido), copiar la ruta
al portapapeles y quitar de la biblioteca — con confirmación, y sin tocar nunca
el archivo original.

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

OPCIONES INICIALES
[ ▶ JUGAR                    ]   <- runtime no integrado todavía
[ 🎨 CAMBIAR ÍCONO           ]
[ ⧉ COPIAR RUTA DEL EJECUTABLE]
   Quitar de la biblioteca       <- con confirmación
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
./gradlew testDebugUnitTest  -> 14 tests, 0 failures
```

Auditoría de librerías de escritorio, en dos niveles:

```
grep -rIn "javax.swing|javax.awt|java.awt" app/src   ->  0 resultados
strings classes*.dex | grep -iE "javax/swing|java/awt" ->  0 resultados
```

Los únicos imports del proyecto son `android.*`, `androidx.*`,
`com.nexora.android.*`, `kotlin(x).*` y `java.{io,nio,util,text}`.

## Fase 4 (fuera de esta versión)

- Integración del runtime de ejecución (y con ella el lanzamiento real de
  "Jugar").
- Alta manual de juegos.
- Pantalla de ajustes persistidos (DataStore).
