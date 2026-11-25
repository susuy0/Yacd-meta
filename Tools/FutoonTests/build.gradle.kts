plugins {
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
project(":FutoonAdmin").layout.buildDirectory.set(layout.buildDirectory.dir("FutoonAdmin"))
