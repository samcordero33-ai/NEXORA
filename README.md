# NEXORA v0.2 — Fase 2

**Windows Gaming Runtime for Android.**

NEXORA es una aplicación Android para ejecutar juegos de PC en móviles.

- **Fase 1 (v0.1):** estructura del proyecto, interfaz gaming oscura y
  detección del dispositivo.
- **Fase 2 (v0.2, esta versión):** escaneo de almacenamiento local y microSD
  para detectar juegos instalados (`.exe`), gestión de permisos y biblioteca en
  memoria con progreso en la UI.

> **No hay Fase 2 de runtime.** No hay Wine, Box64, DXVK ni ningún motor de
> traducción, y **tampoco se ejecuta ningún binario de Windows**: el escáner
> solo lee nombres, rutas y tamaños de archivo.
>
> **No avanza a la Fase 3.** La capa de datos persistente y el lanzamiento de
> juegos quedan fuera de esta versión.

## Requisitos cumplidos

| Requisito | Estado |
|---|---|
| 100 % Kotlin nativo + Jetpack Compose + Android SDK | ✔ |
| Sin librerías de escritorio Java (`javax.swing.*`, `java.awt.*`) | ✔ verificado en fuentes **y en los DEX del APK** |
| Sin Wine / Box64 / DXVK | ✔ |
| No se ejecuta ningún binario de Windows | ✔ el escáner solo lee metadatos |
| Permisos de almacenamiento gestionados de forma limpia | ✔ SAF + `MANAGE_EXTERNAL_STORAGE` (Scoped Storage) |
| Escáner `.exe` que ignora desinstaladores y librerías secundarias | ✔ `ExecutableFilter`, con pruebas unitarias |
| Botones "Escanear carpeta" y "Buscar juegos" | ✔ |
| Barra de progreso e indicador durante el escaneo | ✔ `ScanProgressCard` |
| Arquitectura limpia y modular | ✔ |

## Stack

| Elemento | Versión |
|---|---|
| Kotlin | 1.9.22 |
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.2 (wrapper versionado) |
| Compose Compiler | 1.5.8 |
| Compose BOM | 2024.02.00 |
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
│   ├── screens/HomeScreen.kt   # NexoraHomeRoute (efectos) + NexoraHomeScreen (pura)
│   ├── scanner/GameScannerViewModel.kt
│   ├── components/             # NexoraCard, GameListItem, ScanProgressCard,
│   │                           # StorageAccessCard
│   ├── util/Format.kt          # formateo de tamaños
│   └── theme/{Color,Theme,Type}.kt
├── game/
│   ├── model/Game.kt           # Game + GameSource + GameLibraryState
│   ├── scan/
│   │   ├── GameScanner.kt      # recorrido SAF y por ruta de filesystem
│   │   ├── ExecutableFilter.kt # heurísticas .exe (puras, sin Android)
│   │   ├── ScanModels.kt       # ScanConfig, ScanProgress, ScanOutcome
│   │   └── StorageTargets.kt   # volúmenes y carpetas habituales
│   └── repository/GameLibraryRepository.kt
├── permission/
│   └── StorageAccessController.kt
├── engine/                     # preparado para futuras integraciones
│   ├── RuntimeEngine.kt        # interfaz + EngineStatus + LaunchResult
│   ├── DeviceInfo.kt
│   └── DeviceInfoProvider.kt
└── settings/
    └── AppSettings.kt
```

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

### `ui/`

`NexoraHomeRoute` concentra los efectos que necesitan `Context` o ciclo de vida
(selector de carpetas, petición del permiso clásico, datos del dispositivo) y
`NexoraHomeScreen` es una función pura del estado: previewable y testeable sin
ciclo de vida.

El estado es una única `ScannerUiState` inmutable observada con
`collectAsStateWithLifecycle`.

El acceso amplio se concede fuera de la app, así que se relee en `ON_RESUME`: el
sistema no notifica el cambio.

## Pantalla principal

```
NEXORA
Windows Gaming Runtime for Android

MIS JUEGOS
┌────────────────────────────────────┐
│  Hollow Knight                  🗑 │
│  /storage/emulated/0/Games/Hollow… │
│  48.2 MB · Detectado en el escaneo │
└────────────────────────────────────┘
[ VACIAR BIBLIOTECA ]

[ 📂  ESCANEAR CARPETA ]     <- sin permisos (SAF)
[ 🔍  BUSCAR JUEGOS ]        <- carpetas habituales + microSD

ESCANEANDO ALMACENAMIENTO
┌────────────────────────────────────┐
│ ▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░ │
│ 12483 archivos · 902 carpetas · 6  │
│ /storage/1A2B-3C4D/Games/Doom      │
└────────────────────────────────────┘

ACCESO AL ALMACENAMIENTO
┌────────────────────────────────────┐
│ 🔓 Acceso amplio concedido          │
│ 2 carpetas concedidas · …           │
└────────────────────────────────────┘
```

La barra de progreso es **determinada**: mide la fracción del presupuesto de
entradas consumida, no un porcentaje inventado del árbol (que no se conoce de
antemano). Los contadores y la ruta actual acompañan a la barra porque un
escaneo sobre microSD se percibe mucho más corto si se ve que avanza.

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

## Decisiones de la Fase 2

- **El filtro es una clase pura sin Android.** `ExecutableFilter` no toca
  `Context` ni `Uri`, así que se prueba en JVM sin Robolectric: 9 pruebas
  unitarias cubren desinstaladores, redistribuibles, títulos y puntuación.
- **La biblioteca vive en memoria.** No hay persistencia todavía: el
  identificador canónico de un juego se fija en la Fase 3 junto con la capa de
  datos. La interfaz del repositorio ya es la definitiva, así que sustituirla por
  Room no obliga a tocar la UI.
- **El id del juego se deriva de la ruta.** Volver a escanear la misma carpeta no
  duplica entradas.
- **Los errores de lectura no abortan el escaneo.** Una carpeta sin permiso se
  registra y se sigue: es el comportamiento esperado cuando falta el acceso
  amplio, y abortar perdería los resultados del resto de carpetas.
- **Se omiten enlaces simbólicos** para que uno que apunte a un directorio padre
  no provoque un recorrido infinito.
- **Sin Hilt ni kapt.** Sigue sin haber consumidores de inyección: el ViewModel
  recibe `Application` y compone las dependencias a mano.

## Verificación

```
./gradlew assembleDebug      -> BUILD SUCCESSFUL (sin warnings)
./gradlew assembleRelease    -> BUILD SUCCESSFUL (sin warnings)
./gradlew testDebugUnitTest  -> 9 tests, 0 failures
```

Auditoría de librerías de escritorio, en dos niveles:

```
grep -rIn "javax.swing|javax.awt|java.awt" app/src   ->  0 resultados
strings classes*.dex | grep -iE "javax/swing|java/awt" ->  0 resultados (9 DEX)
```

Los únicos imports del proyecto son `android.*`, `androidx.*`,
`com.nexora.android.*`, `kotlin(x).*` y `java.{io,nio,util}`.

## Fase 3 (fuera de esta versión)

- Persistencia de la biblioteca (Room / DataStore).
- Alta manual de juegos.
- Integración del runtime de ejecución.
