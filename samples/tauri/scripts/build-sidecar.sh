#!/usr/bin/env bash
# Builds the Kotlin server and installs it as the sidecar of the desktop sample.
#
# Two layouts are produced:
#
#   src-tauri/binaries/animeplayer-server-<triple>  the launcher used in development
#   src-tauri/resources/sidecar/                    the server bundled with the app
#
# The bundled one carries its own runtime image of the JVM, so an installed
# application does not need a JVM on the machine.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app="$(dirname "$here")"
repo="$(cd "$app/../.." && pwd)"

echo "==> Building the animeplayer server"
(cd "$repo" && ./gradlew :samples:server:installDist :samples:server:jreImage)

if ! command -v rustc > /dev/null 2>&1; then
    echo "rustc is not on the PATH, cannot determine the target triple" >&2
    exit 1
fi

triple="$(rustc --print host-tuple)"

# ---------------------------------------------------------------- the dev launcher
binaries="$app/src-tauri/binaries"
mkdir -p "$binaries"
cp "$here/sidecar/animeplayer-server" "$binaries/animeplayer-server-$triple"
chmod +x "$binaries/animeplayer-server-$triple"
echo "==> Sidecar ready: $binaries/animeplayer-server-$triple"

# ------------------------------------------------------------- the bundled server
resources="$app/src-tauri/resources/sidecar"
rm -rf "$resources"
mkdir -p "$resources/lib"

cp -r "$repo/samples/server/build/jre" "$resources/jre"
cp "$repo/samples/server/build/install/server/lib/"*.jar "$resources/lib/"

# The runtime image carries the license texts of the JDK with the mode 444. They have
# to travel with the application, but a read-only file cannot be overwritten when the
# resources are copied again — and the build fails with "Permission denied".
chmod -R u+w "$resources"

# `libjava.so` lives in `lib` and needs `libjvm.so`, which the runtime image keeps in
# `lib/server`. The bundler of the AppImage scans every binary in the bundle and looks
# for the dependencies by name — it does not find `libjvm.so` and refuses to bundle.
# A copy next to `libjava.so` settles it.
cp "$resources/jre/lib/server/libjvm.so" "$resources/jre/lib/libjvm.so"

cat > "$resources/server" <<'LAUNCHER'
#!/bin/sh
# The server of the animeplayer library, bundled with the application.
set -e
here="$(cd "$(dirname "$0")" && pwd)"
exec "$here/jre/bin/java" \
    -XX:+UseSerialGC \
    -XX:TieredStopAtLevel=1 \
    -Xss1m \
    -cp "$here/lib/*" \
    dev.animeplayer.server.ServerKt "$@"
LAUNCHER
chmod +x "$resources/server"

echo "==> Bundled server ready: $resources"
