#!/usr/bin/env bash
set -euo pipefail
project_root="$(cd "$(dirname "$0")/../.." && pwd)"
exec "${GRADLE:-$project_root/gradlew}" -p "$project_root/Tools/FutoonTests" \
    :HostCompatibility:test "$@"
