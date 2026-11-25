plugins { `java-library` }

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val repositoryRoot = rootProject.layout.projectDirectory.dir("../..")
val adminDebugClasses = project(":FutoonAdmin").layout.buildDirectory.dir("tmp/kotlin-classes/debug")

sourceSets {
    main {
        java {
            setSrcDirs(listOf(repositoryRoot.dir("TMessagesProj/src/main/java")))
            include("org/telegram/messenger/futoon/FutoonApi.java")
            include("org/telegram/messenger/futoon/FutoonBusinessApi.java")
            include("org/telegram/messenger/futoon/FutoonQuery.java")
            include("org/telegram/messenger/futoon/FutoonIdentity.java")
            include("org/telegram/messenger/futoon/FutoonPanelLayout.java")
        }
    }
    test {
        java {
            srcDir(repositoryRoot.dir("Tools/futoon"))
            include("com/futoonpay/admin/HostCompatibilityRegressionTest.java")
            include("com/futoonpay/admin/BridgeSessionSnapshotTest.java")
            include("org/telegram/messenger/UserConfig.java", "org/telegram/messenger/futoon/FutoonStore.java")
            include("FutoonCoreTest.java", "FutoonExtendedTest.java", "FutoonModuleTest.java", "FutoonPanelLayoutTest.java")
        }
    }
}

dependencies {
    // Compile against the actual shared production policy, not an Android bootclasspath or stub.
    compileOnly(files(adminDebugClasses))
    testCompileOnly(files(adminDebugClasses))
    testRuntimeOnly(files(adminDebugClasses))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.19.1")
    implementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0")
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(":FutoonAdmin:compileDebugKotlin")
}
tasks.named<JavaCompile>("compileTestJava") {
    // Only Android-owned configuration/storage boundaries are in-memory fixtures.
    // Compile and execute the actual bridge and actual Kotlin session/engine classes.
    source(repositoryRoot.file("TMessagesProj/src/main/java/org/telegram/messenger/futoon/FutoonNativeBridge.java"))
}
tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.encoding = "UTF-8"
}
tasks.test {
    useJUnit()
    maxHeapSize = "512m"
    testLogging { events("passed", "failed"); showStandardStreams = true }
}
