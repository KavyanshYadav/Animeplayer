#!/usr/bin/env bash
# Builds the Kotlin server and installs it as the sidecar of the desktop sample.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
app="$(dirname "$here")"
repo="$(cd "$app/../.." && pwd)"

echo "==> Building the animeplayer server"
(cd "$repo" && ./gradlew :samples:server:installDist)

if ! command -v rustc > /dev/null 2>&1; then
    echo "rustc is not on the PATH, cannot determine the target triple" >&2
    exit 1
fi

triple="$(rustc --print host-tuple)"
binaries="$app/src-tauri/binaries"
mkdir -p "$binaries"
cp "$here/sidecar/animeplayer-server" "$binaries/animeplayer-server-$triple"
chmod +x "$binaries/animeplayer-server-$triple"

echo "==> Sidecar ready: $binaries/animeplayer-server-$triple"
