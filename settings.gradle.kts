// ============================================================
// settings.gradle.kts — EternaMente
// Configuración de resolución de plugins y dependencias
// ============================================================

pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Resuelve automáticamente JDK toolchains desde Foojay Disco API
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    // Fuerza que todos los repositorios se declaren aquí (no en módulos individuales)
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack: necesario para MPAndroidChart (no está en Maven Central)
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "EternaMente"
include(":app")
