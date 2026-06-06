import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.compose") version "1.6.0"
    kotlin("plugin.serialization") version "1.9.22"
}

group = "com.lolauncher"
version = "1.0.0"

repositories {
    mavenCentral()
    google()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}

dependencies {
    // Compose Desktop UI
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Сериализация JSON (Mojang API, настройки)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // HTTP-клиент для загрузки версий и файлов
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Корутины для асинхронных операций
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.0")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.lolauncher.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "LoLauncher"
            packageVersion = "1.0.0"
            description = "LoLauncher - Minecraft Launcher"
            vendor = "Txxicu Team"

            windows {
                menuGroup = "LoLauncher"
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            }
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi"
        )
    }
}

// Синхронизация BuildConfig.VERSION_NAME с version проекта
tasks.register("syncBuildConfig") {
    doLast {
        val versionName = project.version.toString()
        val file = file("src/main/kotlin/com/lolauncher/BuildConfig.kt")
        file.writeText(
            """
            package com.lolauncher

            object BuildConfig {
                const val VERSION_NAME = "$versionName"
                const val APP_NAME = "LoLauncher"
            }
            """.trimIndent() + "\n"
        )
    }
}

tasks.named("compileKotlin") { dependsOn("syncBuildConfig") }
