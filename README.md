# NEXORA v0.1 — Fase 1

**Windows Gaming Runtime for Android.**

NEXORA es una aplicación Android para ejecutar juegos de PC en móviles. Esta
primera versión es **solo la base**: estructura de proyecto, interfaz y
detección de dispositivo.

> **Fase 1, no Fase 2.** No hay Wine, Box64, DXVK ni ningún motor de traducción.
> Nada de eso está integrado todavía. La arquitectura está preparada para
> recibirlo, pero no se ha escrito una línea de runtime.

## Requisitos cumplidos

| Requisito | Estado |
|---|---|
| 100 % Kotlin nativo + Jetpack Compose + Android SDK | ✔ |
| Sin librerías de escritorio Java (`javax.swing.*`, `java.awt.*`) | ✔ verificado |
| Sin Wine / Box64 / DXVK | ✔ |
| Arquitectura limpia y modular para futuras fases | ✔ |
| Pantalla principal gaming oscura | ✔ |
| Encabezado `NEXORA` + `Windows Gaming Runtime for Android` | ✔ |
| Sección "Mis juegos" con estado vacío | ✔ |
| Botón principal "Añadir juego" | ✔ |
| Sección "Información del dispositivo" | ✔ |

## Stack

| Elemento | Versión |
|---|---|
| Kotlin | 1.9.22 |
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.2 (wrapper versionado) |
| Compose Compiler | 1.5.8 |
| Compose BOM | 2024.02.00 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |
| JDK | 17 |

Versiones centralizadas en `gradle/libs.versions.toml` (version catalog).

## Arquitectura

```
com.nexora.android/
├── NexoraApplication.kt        # android.app.Application
├── MainActivity.kt             # ComponentActivity + setContent
├── ui/                         # pantallas y componentes
│   ├── screens/HomeScreen.kt   # pantalla principal
│   ├── components/NexoraCard.kt# tarjeta base + encabezado de sección
│   └── theme/{Color,Theme,Type}.kt
├── game/                       # modelos de datos
│   └── model/Game.kt           # Game + GameLibraryState
├── engine/                     # preparado para futuras integraciones
│   ├── RuntimeEngine.kt        # interfaz + EngineStatus + LaunchResult
│   ├── DeviceInfo.kt           # modelo de datos del dispositivo
│   └── DeviceInfoProvider.kt   # lee Build + ActivityManager
└── settings/
    └── AppSettings.kt          # configuración con valores por defecto
```

### `ui/`

Presentación en Compose. `HomeScreen` es la única pantalla y se compone de
pequeños composables privados (encabezado, biblioteca vacía, botón, tarjeta de
dispositivo) en lugar de una función gigante.

El tema vive en `ui/theme` y es **solo oscuro**: es un launcher de juegos, y un
fondo claro arruinaría la estética y el consumo en OLED durante sesiones largas.

### `game/`

Modelos de datos puros, sin lógica ni dependencias de Android.
`GameLibraryState` es un tipo sellado que distingue `Loading` / `Empty` /
`Ready`: son tres pantallas distintas y tratarlas como una sola produce
parpadeos en la UI.

### `engine/`

Contrato para las futuras integraciones. `RuntimeEngine` fija la forma que
tendrán los backends (traducción de syscalls, capa gráfica, prefijos) **sin
implementar ninguno**: `EngineStatus.NotIntegrated` es el estado real de esta
versión.

También vive aquí la detección del dispositivo, porque es la información que
decidirá qué backend es viable en cada móvil (arquitectura de 64 bits, memoria,
nivel de API).

### `settings/`

`AppSettings` con sus valores por defecto. Sin persistencia todavía: los ajustes
se leerán de DataStore cuando exista la pantalla que los modifique.

## Pantalla principal

```
NEXORA
Windows Gaming Runtime for Android

MIS JUEGOS
┌────────────────────────────────────┐
│            [icono]                 │
│      No hay juegos agregados       │
│  Añade tu primer juego para verlo  │
└────────────────────────────────────┘

[ +  AÑADIR JUEGO ]

INFORMACIÓN DEL DISPOSITIVO
┌────────────────────────────────────┐
│ Dispositivo          Google Pixel 8│
│ Android                  14 (API 34)│
│ Arquitectura              arm64-v8a│
│ Núcleos de CPU                    8│
│ Memoria total               7.32 GB│
│ Memoria disponible         3.11 GB │
│ ⚠ Runtime de ejecución no integrado │
└────────────────────────────────────┘
```

Los datos del dispositivo son reales: se leen con `android.os.Build` y
`ActivityManager.MemoryInfo` desde `DeviceInfoProvider`.

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

Un solo acento saturado sobre fondo casi negro: es la convención de los
launchers de juegos y la que mejor aprovecha las pantallas OLED.

## Compilar

```bash
./gradlew assembleDebug
```

APK en `app/build/outputs/apk/debug/app-debug.apk`.

En cada push a `main`, GitHub Actions compila y publica el artefacto
`NEXORA-APK`.

## Decisiones de la Fase 1

- **Sin Hilt ni kapt.** No hay repositorios ni fuentes de datos que inyectar;
  añadir DI ahora sería infraestructura sin consumidores.
- **Sin NDK.** No se configura todavía: el runtime llegará con sus propios
  binarios y entonces se fijarán los `abiFilters`.
- **Sin implementaciones falsas.** `RuntimeEngine` es una interfaz sin
  implementar a propósito: mejor un estado explícito "no integrado" que un stub
  que aparenta funcionar.
- **Botón "Añadir juego" sin destino.** El alta requiere explorador de archivos
  y biblioteca persistente: Fase 2.

## Verificación

```
./gradlew assembleDebug    -> BUILD SUCCESSFUL (sin warnings)
./gradlew assembleRelease  -> BUILD SUCCESSFUL (sin warnings)
```

Auditoría de librerías de escritorio:

```
grep -rIn "javax.swing|javax.awt|java.awt" app/src  ->  0 resultados
```

Los únicos imports del proyecto son `android.*`, `androidx.*` y
`com.nexora.android.*`.
