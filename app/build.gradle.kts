// ============================================================
// app/build.gradle.kts — EternaMente
// Módulo principal: app Android para detección de deterioro
// cognitivo leve mediante juegos + IA on-device
// ============================================================
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)             // Procesamiento de anotaciones: Room + Hilt
    alias(libs.plugins.hilt)            // DI — genera componentes Hilt
    alias(libs.plugins.google.services) // Firebase — transforma google-services.json
}

android {
    namespace  = "com.eternamente.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.eternamente.app"
        minSdk        = 29   // Android 10: BiometricPrompt nativo, SQLCipher, TFLite GPU
        targetSdk     = 34
        versionCode   = 1
        versionName   = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // On-device ML model version — bumped when eternamente_ml_v1.tflite is updated
        buildConfigField("String", "ML_MODEL_VERSION", "\"v1.0\"")

        // ABIs para librerías nativas de SQLCipher y TFLite
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    // ── Build Types ──────────────────────────────────────────
    buildTypes {
        debug {
            isDebuggable          = true
            isMinifyEnabled       = false
            versionNameSuffix     = "-DEBUG"

            // Accesibles desde código: BuildConfig.ENABLE_LOGGING
            buildConfigField("Boolean", "ENABLE_LOGGING",     "true")
            buildConfigField("Boolean", "ENABLE_STRICT_MODE", "true")
        }
        release {
            isDebuggable    = false
            isMinifyEnabled = true
            isShrinkResources = true   // Elimina recursos no referenciados
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("Boolean", "ENABLE_LOGGING",     "false")
            buildConfigField("Boolean", "ENABLE_STRICT_MODE", "false")
        }
    }

    // ── Opciones de compilación ──────────────────────────────
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            // Opt-in a APIs experimentales usadas en el proyecto
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview"
        )
    }

    // ── Build Features ──────────────────────────────────────
    buildFeatures {
        compose     = true  // Activa el compilador de Compose
        buildConfig = true  // Genera la clase BuildConfig con campos custom
    }

    // ── Compose Compiler ────────────────────────────────────
    // Kotlin 1.9.22 ↔ Compose Compiler 1.5.8 (tie fijo — no cambiar uno sin el otro)
    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompilerExtension.get()
    }

    // ── Packaging ───────────────────────────────────────────
    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/DEPENDENCIES",
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md"
            )
        }
        // SQLCipher incluye libsqlcipher.so para múltiples ABIs — tomar la primera
        jniLibs.pickFirsts += "lib/*/libsqlcipher.so"
    }

}

// ── KSP — configuración para Room ───────────────────────────
// Debe ser bloque top-level, fuera de android {}
ksp {
    arg("room.schemaLocation",   "$projectDir/schemas") // Exporta schema para migraciones
    arg("room.incremental",      "true")                // Compilación incremental
    arg("room.expandProjection", "true")                // Optimiza proyecciones en queries
}

dependencies {
    // ── Compose BOM ─────────────────────────────────────────
    // El BOM alinea automáticamente todas las versiones de Compose
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // ── AndroidX Core ────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Compose UI ──────────────────────────────────────────
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended) // Iconos para juegos cognitivos

    // ── Navegación ──────────────────────────────────────────
    implementation(libs.navigation.compose)

    // ── Accompanist ─────────────────────────────────────────
    implementation(libs.accompanist.permissions) // Permisos en tiempo de ejecución (cámara, etc.)

    // ── Hilt — Inyección de Dependencias ────────────────────
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose) // hiltViewModel() dentro de NavHost

    // ── Room — Base de Datos Cifrada (AES-256 via SQLCipher) ─
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)       // Suspending queries y Flow
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)      // Cifrado de base de datos
    implementation(libs.sqlite.ktx)     // Puente SQLite requerido por SQLCipher + Room

    // ── Kotlin Coroutines ────────────────────────────────────
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android) // Dispatcher.Main

    // ── DataStore ────────────────────────────────────────────
    implementation(libs.datastore.preferences) // Preferencias de usuario cifradas

    // ── TensorFlow Lite — IA on-device ──────────────────────
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.support)  // Pre/post-procesamiento de tensores
    implementation(libs.tensorflow.lite.metadata) // Lectura de metadatos de modelos .tflite

    // ── MPAndroidChart — Evolución cognitiva ─────────────────
    implementation(libs.mpandroidchart)

    // ── Firebase ─────────────────────────────────────────────
    // El BOM de Firebase gestiona versiones de todas sus librerías
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)        // Auth: email + Google Sign-In
    implementation(libs.firebase.messaging.ktx)   // FCM: recordatorios y alertas

    // ── WorkManager — Tareas en background ──────────────────
    implementation(libs.work.runtime.ktx) // Sesiones programadas, sincronización, backups
    implementation(libs.hilt.work)        // @HiltWorker + HiltWorkerFactory
    ksp(libs.hilt.work.compiler)          // KSP: genera fábrica de workers con Hilt

    // ── Seguridad ────────────────────────────────────────────
    implementation(libs.security.crypto) // EncryptedSharedPreferences para tokens/keys
    implementation(libs.biometric)       // Autenticación biométrica (huella/face)

    // ── Animaciones ──────────────────────────────────────────
    implementation(libs.lottie.compose)  // Animaciones feedback en juegos cognitivos

    // ── Imágenes ─────────────────────────────────────────────
    implementation(libs.coil.compose)    // Carga de imágenes (avatares, recursos)

    // ── Logging ──────────────────────────────────────────────
    implementation(libs.timber)          // Logging estructurado; no-op en release

    // ── Unit Testing ────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)                   // Mocks idiomáticos Kotlin
    testImplementation(libs.turbine)                 // Test de Kotlin Flows
    testImplementation(libs.kotlinx.coroutines.test)

    // ── Instrumented Testing ─────────────────────────────────
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)

    // ── Debug only ───────────────────────────────────────────
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
    debugImplementation(libs.leakcanary) // Detección de memory leaks — nunca en release
}
