# ============================================================
# ProGuard / R8 Rules — EternaMente
# Aplicadas solo en release build (isMinifyEnabled = true)
# ============================================================

# ── Stack traces legibles en Crashlytics / Logcat ───────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin ──────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ── Coroutines ───────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
# Los campos `volatile` son usados por las máquinas de estado de corrutinas.
# R8 los eliminaría como "sin efecto de lado", rompiendo la suspensión/reanudación.
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ── Hilt ─────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel
# EntryPoint e InstallIn son interfaces leídas por reflexión en el runtime de Hilt.
-keep @dagger.hilt.EntryPoint interface *
-keep @dagger.hilt.InstallIn interface *
-dontwarn dagger.hilt.**

# ── Hilt WorkManager (CognitiveAnalysisWorker usa @HiltWorker + @AssistedInject) ─
# HiltWorkerFactory construye los workers por reflexión; sus clases no pueden ofuscarse.
-keep class androidx.hilt.work.HiltWorkerFactory { *; }
-keep class * extends androidx.hilt.work.HiltWorkerFactory { *; }
# @AssistedInject genera fábricas cuyo nombre lo resuelve el grafo de Hilt en runtime.
-keepclasseswithmembers class * {
    @dagger.assisted.AssistedInject <init>(...);
}
-keep @dagger.assisted.AssistedFactory interface *
-dontwarn dagger.assisted.**

# ── Room ─────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
# TypeConverters: la clase Converters se invoca por reflexión desde Room en runtime.
# Sin esta regla R8 elimina/ofusca los métodos de conversión.
-keep @androidx.room.TypeConverters class * { *; }
# Los campos de las entidades Room se mapean por nombre a las columnas SQL.
# R8 los ofuscaría rompiendo las queries y el schema export de KSP.
-keepclassmembers @androidx.room.Entity class * {
    <fields>;
}
# Paquetes de entidades del proyecto — GAP CRÍTICO:
# Las reglas anteriores (`data.model.**`, `domain.model.**`) NO cubren
# los paquetes reales donde viven las entidades Room de EternaMente.
-keep class com.eternamente.app.data.local.database.entity.** { *; }
-keep class com.eternamente.app.data.local.db.entity.** { *; }
# Converters concreto del proyecto
-keep class com.eternamente.app.data.local.database.Converters { *; }
-dontwarn androidx.room.**

# ── SQLCipher ────────────────────────────────────────────────
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# ── Firebase ─────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
# EternaMenteMessagingService: aunque el Manifest lo declara como entry point
# (lo que normalmente basta para que R8 lo conserve), la retención explícita
# protege también los métodos onNewToken/onMessageReceived y el companion object.
-keep class com.eternamente.app.core.notifications.EternaMenteMessagingService { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── TensorFlow Lite ──────────────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
# Métodos nativos (JNI): R8 nunca debe renombrarlos porque son resueltos
# por nombre desde la librería nativa libtenserflowlite_jni.so.
-keepclasseswithmembernames class org.tensorflow.** {
    native <methods>;
}
-dontwarn org.tensorflow.**

# ── MPAndroidChart ───────────────────────────────────────────
-keep class com.github.mikephil.charting.** { *; }
-dontwarn com.github.mikephil.charting.**

# ── WorkManager ──────────────────────────────────────────────
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── Coil ─────────────────────────────────────────────────────
-dontwarn coil.**

# ── Lottie ───────────────────────────────────────────────────
-dontwarn com.airbnb.lottie.**

# ── Timber (strip en release) ────────────────────────────────
-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ── android.util.Log (strip en release) ─────────────────────
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ── Modelos de datos (evitar ofuscación de data classes) ─────
# R8 puede romper Room/serialización si ofusca los campos
-keep class com.eternamente.app.data.model.** { *; }
-keep class com.eternamente.app.domain.model.** { *; }

# ── Compose ──────────────────────────────────────────────────
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ── Biometric ────────────────────────────────────────────────
-keep class androidx.biometric.** { *; }
-dontwarn androidx.biometric.**

# ── Security Crypto ──────────────────────────────────────────
-keep class androidx.security.crypto.** { *; }

# ── Enums (R8 puede romper .values() y .valueOf()) ───────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Parcelable ───────────────────────────────────────────────
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
