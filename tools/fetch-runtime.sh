#!/usr/bin/env bash
# NEXORA · integración automática del runtime (Wine + Box64)
#
# Descarga el APK oficial de Winlator, verifica su huella, extrae los binarios
# ARM64 de Box64 y el árbol de Wine, y los empaqueta en
# app/src/main/assets/runtime/ con un manifiesto que la app sabe leer.
#
# Por qué así:
#   - Los binarios NO se versionan (pesan cientos de MB y GitHub rechaza
#     archivos de más de 100 MB). Este script los reconstruye de forma
#     reproducible a partir de una URL y una huella fijas.
#   - Se empaqueta en .tar.gz y no en archivos sueltos: un árbol de Wine tiene
#     ~1.700 archivos y el manifiesto crecería hasta ser inmanejable.
#   - El sello sha256 de cada archivo queda en manifest.txt, así que la app
#     verifica la integridad antes de extraer.
#
# Además, parchea el intérprete ELF de Box64. Tal como viene en el APK de
# Winlator, box64 pide /data/data/com.winlator/files/rootfs/lib/ld-linux-aarch64.so.1,
# una ruta que bajo com.nexora.android no existe y hace que execve falle con
# ENOENT. El script lo reapunta a la ruta de instalación de NEXORA y mete el
# cargador y las bibliotecas glibc aarch64 dentro del propio paquete.
#
# Uso:
#   tools/fetch-runtime.sh            # descarga, empaqueta y escribe manifiesto
#   tools/fetch-runtime.sh --clean    # borra los artefactos generados
#
# Requisitos: curl, unzip, zstd, tar, sha256sum.

set -euo pipefail

# --- Origen fijado -----------------------------------------------------------
WINLATOR_VERSION="11.2"
WINLATOR_URL="https://github.com/brunodev85/winlator/releases/download/v11.2.0/Winlator_11.2.apk"
WINLATOR_SHA256="ea5c81bf0e5b90a6b9ccb8007121ab6738c8646a5a60b341ce0ac2b33f1d100a"

# Debe coincidir con applicationId de app/build.gradle.kts: la ruta absoluta
# queda grabada dentro del ELF y no se puede corregir en el dispositivo.
APP_ID="com.nexora.android"
INSTALL_ROOT="/data/data/${APP_ID}/files/runtime"
BOX_INSTALL="${INSTALL_ROOT}/box64"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ASSETS="$REPO_ROOT/app/src/main/assets/runtime"
CACHE="$REPO_ROOT/build/runtime-cache"
STAGE="$CACHE/stage"

say() { printf '\033[1m==>\033[0m %s\n' "$*"; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

# --- Limpieza ----------------------------------------------------------------
if [ "${1:-}" = "--clean" ]; then
    say "Borrando artefactos generados"
    rm -rf "$ASSETS/wine" "$ASSETS/box64" "$ASSETS/manifest.txt" "$ASSETS/RUNTIME-SOURCE.txt" "$CACHE"
    say "Listo. Las carpetas quedan vacías (se recrean al volver a ejecutar)."
    exit 0
fi

# --- Herramientas ------------------------------------------------------------
for tool in curl unzip zstd tar sha256sum patchelf; do
    command -v "$tool" >/dev/null 2>&1 || die "falta '$tool' en el PATH"
done

mkdir -p "$CACHE" "$STAGE" "$ASSETS/wine" "$ASSETS/box64"

# --- 1. Descarga verificada --------------------------------------------------
APK="$CACHE/Winlator_${WINLATOR_VERSION}.apk"
if [ -f "$APK" ] && [ "$(sha256sum "$APK" | cut -d' ' -f1)" = "$WINLATOR_SHA256" ]; then
    say "APK en caché y con la huella correcta"
else
    say "Descargando Winlator $WINLATOR_VERSION (~151 MB)"
    curl -fL --retry 3 --progress-bar -o "$APK.part" "$WINLATOR_URL"
    mv "$APK.part" "$APK"
fi

ACTUAL="$(sha256sum "$APK" | cut -d' ' -f1)"
[ "$ACTUAL" = "$WINLATOR_SHA256" ] || die "huella del APK distinta: $ACTUAL"
say "Huella del APK verificada"

GRADLE_APP_ID="$(sed -n 's/^[[:space:]]*applicationId[[:space:]]*=[[:space:]]*"\(.*\)".*/\1/p' \
    "$REPO_ROOT/app/build.gradle.kts" | head -n 1)"
[ "$GRADLE_APP_ID" = "$APP_ID" ] || die "APP_ID ($APP_ID) no coincide con applicationId ($GRADLE_APP_ID): la ruta grabada en box64 apuntaria a otro sitio"

# --- 2. Extracción -----------------------------------------------------------
say "Extrayendo archivos del APK"
rm -rf "$STAGE" && mkdir -p "$STAGE/rootfs" "$STAGE/box64"
unzip -o -q "$APK" 'assets/rootfs.tzst' 'assets/box64/*' -d "$STAGE"

BOX_ARCHIVE="$(find "$STAGE/assets/box64" -name 'box64-*.tzst' | head -n 1)"
[ -n "$BOX_ARCHIVE" ] || die "no se encontró el archivo de Box64 en el APK"
BOX_VERSION="$(basename "$BOX_ARCHIVE" .tzst | sed 's/^box64-//')"

say "Descomprimiendo Box64 $BOX_VERSION"
mkdir -p "$STAGE/box64-raw"
zstd -dc "$BOX_ARCHIVE" | tar -x -C "$STAGE/box64-raw" ./usr/local/bin/box64
mv "$STAGE/box64-raw/usr/local/bin/box64" "$STAGE/box64/box64"
rm -rf "$STAGE/box64-raw"

say "Descomprimiendo el árbol de Wine (~300 MB, tarda un poco)"
zstd -dc "$STAGE/assets/rootfs.tzst" | tar -x -C "$STAGE/rootfs" ./opt/wine

WINE_DIR="$STAGE/rootfs/opt/wine"
[ -x "$WINE_DIR/bin/wine" ] || die "el árbol de Wine extraído no tiene bin/wine"

# Se descartan cabeceras y documentación: no hacen falta para ejecutar y
# suman decenas de megabytes.
rm -rf "$WINE_DIR/include" "$WINE_DIR/share/doc" "$WINE_DIR/share/man"

# --- 2b. Cargador glibc y parcheo del intérprete -----------------------------
# box64 es un ELF aarch64 enlazado contra glibc, no contra Bionic: necesita su
# propio cargador y sus propias libc/libm/libresolv. Se extraen del rootfs de
# Winlator y viajan junto al binario, para que el intérprete grabado en el ELF
# apunte a un archivo que de verdad existe en el dispositivo.
say "Extrayendo el cargador glibc aarch64"
mkdir -p "$STAGE/box64/lib"
for lib in ld-linux-aarch64.so.1 libc.so.6 libm.so.6 libresolv.so.2 libdl.so.2 libpthread.so.0; do
    zstd -dc "$STAGE/assets/rootfs.tzst" | tar -x -C "$STAGE/box64/lib" --strip-components=3 "./usr/lib/$lib"
done

say "Parcheando el intérprete de box64 -> $BOX_INSTALL/lib/ld-linux-aarch64.so.1"
# $ORIGIN/lib se añade como segunda entrada del RUNPATH: si algún día cambia el
# directorio de instalación, la biblioteca se sigue encontrando junto al
# binario. La ruta absoluta va primero porque es la que el kernel usa para el
# intérprete.
patchelf \
    --set-interpreter "$BOX_INSTALL/lib/ld-linux-aarch64.so.1" \
    --set-rpath "$BOX_INSTALL/lib:\$ORIGIN/lib" \
    "$STAGE/box64/box64"

# El parcheo es el objetivo del script: si quedara una sola referencia al
# paquete de Winlator, el binario volvería a fallar con ENOENT al ejecutarse.
if grep -qa "com\.winlator" "$STAGE/box64/box64"; then
    die "box64 sigue apuntando a com.winlator despues del parcheo"
fi
say "box64 parcheado y sin referencias a com.winlator"

# Wine NO se parchea, y es deliberado: es un ELF x86-64 cuyo intérprete
# /lib64/ld-linux-x86-64.so.2 nunca llega a usarse, porque quien lo carga es
# box64, que emula la ABI y resuelve libc.so.6 con sus wrappers nativos. No hay
# cargador x86-64 en el rootfs de Winlator (comprobado: no existe lib64), así
# que reapuntarlo a una ruta inventada solo añadiría una forma nueva de fallar.

# --- 3. Empaquetado determinista --------------------------------------------
# --sort=name + --mtime fijo + owner 0 hacen que dos ejecuciones produzcan el
# mismo sha256, que es lo que permite fijar la huella en el manifiesto.
#
# La extensión es .tgz y no .tar.gz a propósito: aapt2 descomprime los assets
# terminados en .gz al empaquetarlos, así que un .tar.gz llegaría al APK como
# .tar crudo de 318 MB. Con .tgz el archivo viaja intacto.
TAR_OPTS=(--sort=name --mtime='@0' --owner=0 --group=0 --numeric-owner --format=gnu)

say "Empaquetando Box64"
BOX_OUT="$ASSETS/box64/box64-${BOX_VERSION}-arm64.tgz"
tar "${TAR_OPTS[@]}" -czf "$BOX_OUT" -C "$STAGE/box64" box64 lib

say "Empaquetando Wine (esto es lo que más tarda)"
WINE_OUT="$ASSETS/wine/wine-${WINLATOR_VERSION}-arm64.tgz"
tar "${TAR_OPTS[@]}" -czf "$WINE_OUT" -C "$WINE_DIR" bin lib share

# --- 4. Manifiesto -----------------------------------------------------------
sha_of() { sha256sum "$1" | cut -d' ' -f1; }
size_of() { stat -c %s "$1"; }

say "Escribiendo manifest.txt"
cat > "$ASSETS/manifest.txt" <<EOF
# NEXORA · manifiesto del runtime (generado por tools/fetch-runtime.sh)
#
# No editar a mano: se regenera al ejecutar el script. Cada sección declara un
# archivo de assets/ que la app copia o extrae en filesDir/runtime.
#
# Claves:
#   asset      ruta dentro de assets/runtime/
#   target     ruta relativa al directorio de instalación
#   archive    true = el asset es un .tar.gz que se extrae en target
#   binary     (solo archives) ejecutable principal, relativo a target
#   sha256     huella del asset, verificada antes de escribir nada
#   size       tamaño del asset en bytes
#   executable true = se marca el ejecutable principal como ejecutable
#
# Origen: Winlator ${WINLATOR_VERSION} (brunodev85/winlator), GPLv3.
# Wine: LGPL-2.1+. Box64: MIT. Ver RUNTIME-SOURCE.txt.

version=10

[box64]
asset=box64/box64-${BOX_VERSION}-arm64.tgz
target=box64
archive=true
binary=box64
executable=true
sha256=$(sha_of "$BOX_OUT")
size=$(size_of "$BOX_OUT")

[wine]
asset=wine/wine-${WINLATOR_VERSION}-arm64.tgz
target=wine
archive=true
binary=bin/wine
executable=true
sha256=$(sha_of "$WINE_OUT")
size=$(size_of "$WINE_OUT")

[tools]
# wineboot/wineserver viven dentro del árbol de Wine, ya extraído por [wine].
asset=wine/wine-${WINLATOR_VERSION}-arm64.tgz
target=wine
archive=true
binary=bin/wineserver
executable=true
sha256=$(sha_of "$WINE_OUT")
size=$(size_of "$WINE_OUT")
EOF

# --- 5. Procedencia y licencias ---------------------------------------------
cat > "$ASSETS/RUNTIME-SOURCE.txt" <<EOF
NEXORA · procedencia del runtime empaquetado
============================================

Generado por tools/fetch-runtime.sh. No editar a mano.

Componente   Box64
  Versión    ${BOX_VERSION}
  Origen     Winlator ${WINLATOR_VERSION} (https://github.com/brunodev85/winlator)
  Licencia   MIT (Copyright (c) 2020-2025 ptitSeb)
  Archivo    box64/box64-${BOX_VERSION}-arm64.tgz
  SHA-256    $(sha_of "$BOX_OUT")

Componente   Wine
  Versión    la incluida en Winlator ${WINLATOR_VERSION} (/opt/wine)
  Origen     Winlator ${WINLATOR_VERSION} (https://github.com/brunodev85/winlator)
  Licencia   LGPL-2.1-or-later (https://www.winehq.org/license)
  Archivo    wine/wine-${WINLATOR_VERSION}-arm64.tgz
  SHA-256    $(sha_of "$WINE_OUT")

Modificaciones introducidas por NEXORA
--------------------------------------
box64 (MIT permite la modificacion y redistribucion):
  - Interprete ELF reapuntado con patchelf:
      /data/data/com.winlator/files/rootfs/lib/ld-linux-aarch64.so.1
      -> ${BOX_INSTALL}/lib/ld-linux-aarch64.so.1
  - RUNPATH: anadido ${BOX_INSTALL}/lib y \$ORIGIN/lib.
  - Se anaden al paquete el cargador ld-linux-aarch64.so.1 y las bibliotecas
    glibc aarch64 (libc, libm, libresolv, libdl, libpthread) tomadas del mismo
    rootfs de Winlator.
Wine: sin modificar (se empaqueta tal cual).

El APK de origen se distribuye bajo GPLv3. Quien redistribuya este APK debe
mantener las licencias anteriores y ofrecer el codigo fuente correspondiente
(https://github.com/brunodev85/winlator y https://github.com/ptitSeb/box64).
El script que aplica el parche es la descripcion exacta del cambio.
EOF

say "Listo"
printf '  %-46s %8s  %s\n' "archivo" "MB" "sha256"
for f in "$BOX_OUT" "$WINE_OUT"; do
    printf '  %-46s %8.1f  %s\n' "${f#$ASSETS/}" "$(awk -v b="$(size_of "$f")" 'BEGIN{print b/1048576}')" "$(sha_of "$f")"
done
