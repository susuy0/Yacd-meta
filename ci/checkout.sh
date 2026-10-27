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
patch_args=()
if [[ "$mode" == minimal ]]; then patch_args+=(--exclude="TMessagesProj/jni/*"); fi
git -C "$destination" apply "${patch_args[@]}" --check "$package_dir/integration.patch"
git -C "$destination" apply "${patch_args[@]}" "$package_dir/integration.patch"
# Bring in the original data/business library; all interaction stays in the host panel.
cp -R "$package_dir/FutoonAdmin" "$destination/FutoonAdmin"
# Inter replaces the host's Roboto text face (SIL OFL 1.1; see fonts/SOURCE.txt).
mkdir -p "$destination/TMessagesProj/src/main/assets/fonts" "$destination/TMessagesProj/src/main/res/font"
for face in regular italic medium semibold bold; do
    cp "$package_dir/fonts/inter_$face.ttf" "$destination/TMessagesProj/src/main/assets/fonts/inter_$face.ttf"
    cp "$package_dir/fonts/inter_$face.ttf" "$destination/TMessagesProj/src/main/res/font/inter_$face.ttf"
done
cp "$package_dir/fonts/inter.xml" "$destination/TMessagesProj/src/main/res/font/inter.xml"
if [[ "$mode" == full ]]; then
    git -C "$destination" submodule update --init --recursive --depth 1 --jobs 2
fi


