// ============================================================
// build.gradle.kts (root) — EternaMente
// Solo declara plugins disponibles para submódulos.
// Ningún plugin se aplica aquí directamente (apply false).
// ============================================================
plugins {
    // Android Gradle Plugin — compilación de módulos Android
    alias(libs.plugins.android.application) apply false

    // Kotlin para Android
    alias(libs.plugins.kotlin.android) apply false

    // KSP — procesamiento de anotaciones en tiempo de compilación
    // Reemplaza KAPT: más rápido, incremental, sin stubs Java
    alias(libs.plugins.ksp) apply false

    // Hilt — inyección de dependencias (requiere KSP)
    alias(libs.plugins.hilt) apply false

    // Google Services — integración de Firebase (procesa google-services.json)
    alias(libs.plugins.google.services) apply false
}
