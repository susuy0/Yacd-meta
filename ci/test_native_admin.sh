#!/usr/bin/env bash
set -euo pipefail
package_dir="$(cd "$(dirname "$0")/.." && pwd)"
test_root="$1"
mkdir -p "$test_root"
cp -R "$package_dir/FutoonAdmin" "$test_root/FutoonAdmin"
cat > "$test_root/settings.gradle.kts" <<'GRADLE'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "FutoonPanelBusinessFusionRegression"
include(":FutoonAdmin")
GRADLE
cat > "$test_root/build.gradle.kts" <<'GRADLE'
plugins {
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
GRADLE
printf 'android.useAndroidX=true\n' > "$test_root/gradle.properties"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > "$test_root/local.properties"
timeout --signal=QUIT --kill-after=30s 18m gradle -p "$test_root" :FutoonAdmin:testDebugUnitTest :FutoonAdmin:lintDebug \
    --no-daemon --max-workers=2 -PfutoonUiTesting=true -Pkotlin.compiler.execution.strategy=in-process \
    '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g' --stacktrace
