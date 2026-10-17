#!/usr/bin/env bash
set -euo pipefail
package_dir="$(cd "$(dirname "$0")/.." && pwd)"
destination="$1"
mode="$2"
revision='f32ffeec763f0bdbf4bec25b52d654f77bff733d'
if [[ -e "$destination" ]]; then
    printf 'Target already exists: %s\n' "$destination" >&2
    exit 1
fi
git clone --depth 1 --filter=blob:none --no-checkout https://github.com/DrKLO/Telegram.git "$destination"
git -C "$destination" fetch --depth 1 origin "$revision"
if [[ "$mode" == minimal ]]; then
    git -C "$destination" sparse-checkout init --cone
    git -C "$destination" sparse-checkout set TMessagesProj/src/main/java TMessagesProj/src/main/assets TMessagesProj/src/main/res TMessagesProj/config TMessagesProj_App buildSrc Tools
elif [[ "$mode" != full ]]; then
    printf 'Invalid checkout mode\n' >&2
    exit 1
fi
git -C "$destination" checkout --detach "$revision"
git -C "$destination" apply --check "$package_dir/integration.patch"
git -C "$destination" apply "$package_dir/integration.patch"
if [[ "$mode" == full ]]; then
    git -C "$destination" submodule update --init --recursive --depth 1 --jobs 2
fi

