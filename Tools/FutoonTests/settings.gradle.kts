pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "FutoonBusinessRegression"
include(":FutoonAdmin")
project(":FutoonAdmin").projectDir = file("../../FutoonAdmin")
include(":HostCompatibility")
