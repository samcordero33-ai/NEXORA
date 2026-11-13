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
for tool in curl unzip zstd tar sha256sum; do
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
tar "${TAR_OPTS[@]}" -czf "$BOX_OUT" -C "$STAGE/box64" box64

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

version=9

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

El APK de origen se distribuye bajo GPLv3. NEXORA no modifica los binarios:
los empaqueta tal cual y los extrae en el dispositivo. Quien redistribuya este
APK debe mantener las licencias anteriores y ofrecer el código fuente
correspondiente (https://github.com/brunodev85/winlator y https://github.com/ptitSeb/box64).
EOF

say "Listo"
printf '  %-46s %8s  %s\n' "archivo" "MB" "sha256"
for f in "$BOX_OUT" "$WINE_OUT"; do
    printf '  %-46s %8.1f  %s\n' "${f#$ASSETS/}" "$(awk -v b="$(size_of "$f")" 'BEGIN{print b/1048576}')" "$(sha_of "$f")"
done
