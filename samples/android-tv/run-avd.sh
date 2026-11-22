#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
set -euo pipefail

avd=${1:-animeplayer-tv}
if (( $# > 0 )); then
    shift
fi

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
emulator="$sdk/emulator/emulator"
if [[ ! -x "$emulator" ]]; then
    emulator=$(command -v emulator) || {
        printf '%s\n' 'Android Emulator is required. Set ANDROID_HOME or add emulator to PATH.' >&2
        exit 1
    }
fi

if [[ $(uname -s) == Linux ]] && command -v nvidia-smi >/dev/null && nvidia-smi -L >/dev/null 2>&1; then
    # Request the NVIDIA render-offload provider for GL and prefer it for Vulkan.
    export __NV_PRIME_RENDER_OFFLOAD=1
    export __GLX_VENDOR_LIBRARY_NAME=nvidia
    export __VK_LAYER_NV_optimus=NVIDIA_only
fi

exec "$emulator" -avd "$avd" -gpu host "$@"
