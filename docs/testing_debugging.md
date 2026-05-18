# Testing y Debugging — EternaMente

---

## 7.3 Detectar Memory Leaks con LeakCanary

### Estado en EternaMente

LeakCanary **ya está instalado y configurado correctamente** en el proyecto:

```kotlin
// app/build.gradle.kts
debugImplementation(libs.leakcanary)   // solo en debug — nunca en release
```

```toml
# gradle/libs.versions.toml
leakcanary = "2.12"
leakcanary = { group = "com.squareup.leakcanary", name = "leakcanary-android", version.ref = "leakcanary" }
```

**No se requiere ninguna inicialización manual.** LeakCanary se activa automáticamente
mediante un `ContentProvider` que se registra al arrancar la app. Añadir código como
`LeakCanary.install(this)` en `Application.onCreate()` es incorrecto y produce un error
en versiones recientes (≥ 2.0).

---

### Cómo funciona LeakCanary

1. **Monitoriza automáticamente** `Activity`, `Fragment`, `ViewModel` y `Service`
   al destruirse para detectar si quedan referencias vivas cuando no deberían.

2. **Analiza el heap** cuando detecta un objeto que debería haber sido recolectado por GC
   pero sigue vivo tras 5 segundos.

3. **Muestra una notificación** en la barra de estado con el texto:
   ```
   LeakCanary: 1 leak detected
   ```

4. **Tocar la notificación** abre la pantalla `Leaks` con el stack trace completo,
   mostrando la cadena de referencias que impide la recolección del objeto.

---

### Cómo leer el informe de LeakCanary

El stack trace de LeakCanary tiene este formato:

```
┬───
│ GC Root: Thread local variable
│
├─ com.eternamente.app.presentation.auth.AuthViewModel instance
│    Leaking: YES (ObjectWatcher was watching this because AuthViewModel received
│             onCleared() and then 5 seconds passed)
│    key = abc123
│    watchDurationMillis = 5137
│    retainedDurationMillis = 137
│    ↓ AuthViewModel.appContext
│                    ~~~~~~~~~~
├─ android.app.Activity instance   ← AQUÍ ESTÁ EL PROBLEMA
│    Leaking: YES (Activity#mDestroyed is true)
```

Lo importante es la línea con `↓` y el campo subrayado: ahí está la referencia que retiene
el objeto. En el ejemplo, el `ViewModel` guarda un `Activity` context en `appContext`
en lugar de `Application` context.

---

### Patrón correcto vs. incorrecto en EternaMente

#### Context en ViewModel

```kotlin
// ✗ INCORRECTO — Activity context → memory leak garantizado
@HiltViewModel
class MyViewModel @Inject constructor(
    private val context: Context   // si Hilt inyecta ActivityContext → leak
) : ViewModel()

// ✓ CORRECTO — Application context → seguro, vive tanto como la app
@HiltViewModel
class MyViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context   // ← Hilt provee Application
) : ViewModel()
```

`AuthViewModel` y `PdfExportViewModel` ya usan `@ApplicationContext` — **están correctos**.

#### NavController en ViewModel

```kotlin
// ✗ INCORRECTO — NavController tiene referencia a Activity → leak
class MyViewModel : ViewModel() {
    var navController: NavController? = null   // NUNCA guardar en ViewModel
}

// ✓ CORRECTO — navegación mediante eventos (patrón usado en EternaMente)
class MyViewModel : ViewModel() {
    private val _navEvent = MutableSharedFlow<Destination>()
    val navEvent: SharedFlow<Destination> = _navEvent.asSharedFlow()
    // La UI colecta el evento y llama a navController.navigate() ella misma
}
```

`GameBaseViewModel` ya usa `GameNavigationEvent` como `SharedFlow` — **correcto**.

#### Activity en ViewModel

```kotlin
// ✗ INCORRECTO — Activity no debe vivir en ViewModel
class MyViewModel : ViewModel() {
    lateinit var activity: FragmentActivity   // leak si la Activity se recrea
}

// ✓ CORRECTO — pasar Activity al método solo cuando se necesita (en Compose: LocalContext)
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    val context = LocalContext.current   // acceso seguro al contexto actual
    Button(onClick = { viewModel.doSomethingWith(context as Activity) }) { ... }
}
```

---

### Análisis de riesgo del proyecto actual

| Clase | Context usado | Riesgo |
|---|---|---|
| `AuthViewModel` | `@ApplicationContext` vía Hilt | ✅ Sin riesgo |
| `PdfExportViewModel` | `@ApplicationContext` vía Hilt | ✅ Sin riesgo |
| `EternaNotificationManager` | `@ApplicationContext` vía Hilt | ✅ Sin riesgo |
| `NotificationScheduler` | `@ApplicationContext` vía Hilt | ✅ Sin riesgo |
| `BadgeNotificationHelper` | `@ApplicationContext` vía Hilt | ✅ Sin riesgo |
| `CognitiveAlertNotificationHelper` | `@ApplicationContext` vía Hilt | ✅ Sin riesgo |
| `GameBaseViewModel` | Ningún Context — navegación por SharedFlow | ✅ Sin riesgo |
| Companion objects de ViewModels | Solo `const val` (constantes primitivas) | ✅ Sin riesgo |

**No se detectaron riesgos de memory leak en el proyecto.**

---

### Casos típicos que LeakCanary detectaría en Android

| Patrón | Por qué leak | Solución |
|---|---|---|
| `Activity` context en `ViewModel` | ViewModel sobrevive a la Activity | `@ApplicationContext` |
| `NavController` en `ViewModel` | NavController referencia Activity | Navegación por eventos |
| `Listener` no desregistrado | Callback retiene la Activity | Desregistrar en `onDestroy` / `onCleared` |
| `Handler` con `Activity` | Messages en cola retienen la Activity | Usar `WeakReference` o cancelar en `onDestroy` |
| `BroadcastReceiver` sin `unregister` | Si se registra dinámicamente | `unregisterReceiver` en `onStop` |
| `Coroutine` lanzada sin scope | Corre indefinidamente | Usar `viewModelScope` o `lifecycleScope` |

En EternaMente, todos los `BroadcastReceiver` son estáticos (declarados en el Manifest),
por lo que el sistema los gestiona sin necesidad de `unregisterReceiver`.

---

### Comandos útiles

```bash
# Ver el heap dump generado por LeakCanary
adb pull /data/data/com.eternamente.app/files/leakcanary/ .

# Forzar análisis de heap desde adb (útil en CI)
adb shell am broadcast -a com.squareup.leakcanary.action.DUMP_HEAP \
    -p com.eternamente.app

# Ver logs de LeakCanary en Logcat
adb logcat -s LeakCanary

# Limpiar resultados anteriores de LeakCanary
adb shell pm clear com.eternamente.app
```

---

## 7.4 Medir rendimiento con Android Profiler

### Cómo abrir el Profiler

```
Android Studio → View → Tool Windows → Profiler
```

O con el atajo: **barra inferior** → icono de cronómetro durante una sesión de debug.

Requisitos:
- Dispositivo o emulador conectado en modo debug.
- App en variante `debug` (la release tiene ofuscación y es menos legible en el Profiler).
- En `run configurations`: marcar **"Profile 'app'"** en lugar de **"Run 'app'"**.

---

### CPU Profiler — durante gameplay

**Cuándo usarlo:** al sospechar que un juego congela la UI, genera jank (frames perdidos)
o mantiene el CPU al 100 % sin bajar después de terminar.

**Pasos:**

1. Profiler → seleccionar proceso `com.eternamente.app` → pestaña **CPU**.
2. Pulsar **Record** (tipo: *Callstack Sample* para Kotlin/Coroutines).
3. Jugar una sesión completa del juego cognitivo (ej. MemoryMatch, TrailMaking).
4. Pulsar **Stop**.
5. En el flame chart, buscar métodos con barra ancha → son los que más CPU consumen.

**Criterios esperados en EternaMente:**

| Momento | CPU esperado |
|---|---|
| Juego activo (lógica + UI) | 20–50 % — varía por juego |
| Juego completado / navegando | < 10 % — debe bajar al salir |
| CPU al 100 % sostenido | ⚠ Revisar loop o corrutina sin `delay` |
| Jank (frames > 16 ms) | ⚠ Revisar cálculo en Main thread |

**Señales de alerta en el Profiler:**

- `GameEngine.tick()` o `GameTimer` aparecen en la parte superior del flame chart con
  barras muy anchas → el motor de juego consume demasiado tiempo por frame.
- `MetricsCollector.record()` aparece frecuentemente → considera reducir la frecuencia
  de muestreo de métricas.
- `Recomposer` (Compose) aparece con picos altos → revisar recomposiciones innecesarias
  en las pantallas de juego.

**Cómo identificar recomposiciones excesivas en Compose:**

```
Layout Inspector → Recomposition Counts
```
O en Logcat filtrar:
```
tag:Choreographer  skipped
```
Más de 3 frames perdidos consecutivos indica trabajo excesivo en el Main thread.

---

### Memory Profiler — durante navegación y sesiones largas

**Cuándo usarlo:** al sospechar crecimiento continuo de heap o para confirmar que
LeakCanary no reporta falsos negativos.

**Pasos:**

1. Profiler → pestaña **Memory**.
2. Navegar entre pantallas: Login → Dashboard → Juego → Resultado → Dashboard.
3. Observar la línea de heap en el gráfico — debe subir durante el juego y **bajar** al volver.
4. Si el heap sube y no baja: pulsar **Force GC** (ícono de cubo de basura).
5. Si después del GC el heap sigue alto: hay un posible leak — revisar con LeakCanary.

**Criterios esperados:**

| Situación | Heap esperado |
|---|---|
| App en Dashboard idle | ~ 50–80 MB (varía por dispositivo) |
| Durante un juego con imágenes | +20–40 MB (Coil carga imágenes) |
| Tras terminar el juego y volver | Debe volver al nivel previo tras GC |
| Crecimiento sostenido sesión a sesión | ⚠ Memory leak — revisar con LeakCanary |

**Puntos de atención en EternaMente:**

- `MemoryMatchScreen` y `SpotDiffScreen` cargan imágenes → pico temporal es normal.
- `MetricsCollector` acumula `TrialMetrics` durante el juego → `engine.metrics.reset()` en
  `GameBaseViewModel.onCleared()` libera esa memoria. Verificar que ocurra en el Profiler.
- `IsolationForestModel` mantiene en memoria el historial de vectores de features → el tamaño
  crece con el uso. Es intencional y acotado (máximo ~ 100 vectores × 14 floats × 4 bytes ≈ 5 KB).

---

### Network Profiler — Firebase y FCM

**Cuándo usarlo:** para confirmar el comportamiento offline-first y que Firebase no hace
llamadas de red inesperadas durante el uso normal de los juegos.

**Pasos:**

1. Profiler → pestaña **Network**.
2. Navegar y jugar normalmente.
3. Observar que la actividad de red es mínima o nula durante juegos cognitivos.
4. Hacer login → verificar el spike de Firebase Auth (esperado, ~ 1–2 KB).
5. Activar modo avión → jugar → confirmar que la app sigue funcionando (offline-first).

**Criterios esperados:**

| Evento | Red esperada |
|---|---|
| Login con Firebase Auth | 1–3 KB (handshake + token) |
| Juego cognitivo activo | 0 bytes — todo es local (Room) |
| Token FCM refresh | < 1 KB — ocurre raramente en background |
| Análisis ML semanal | 0 bytes — on-device (WorkManager + Room) |
| Modo avión, cualquier juego | 0 bytes — offline-first confirmado |

Si aparece tráfico durante un juego cognitivo → investigar qué componente lo genera.
Filtrar en Network Profiler por host para identificar el origen.

---

### ML / TFLite — cuando el modelo esté integrado

El modelo TFLite (`eternamente_ml_v1.tflite`) corre en `CognitiveAnalysisWorker` vía
`CognitiveAnalyzer.analyze()` en background (WorkManager + `Dispatchers.Default`).
**Nunca bloquea la UI.**

**Logs ya instrumentados para medir el rendimiento:**

```
# Filtrar en Logcat para ver tiempos de inferencia
tag:TFLiteModelManager

# Ejemplo de salida esperada:
D  TFLiteModelManager: inference OK — score=0.32, elapsed=48ms
W  TFLiteModelManager: inference lenta (612ms > 500ms umbral)

# Pipeline completo
tag:CognitiveAnalyzer

# Ejemplo de salida:
I  CognitiveAnalyzer: done in 237ms — level=NORMAL, anomaly=0.31, model=0.28, domains=0
```

**Criterios de rendimiento ML:**

| Métrica | Objetivo | Acción si se supera |
|---|---|---|
| `TFLiteModelManager` — `elapsed` | < 500 ms | Reducir `numThreads` o simplificar el modelo |
| `CognitiveAnalyzer` pipeline total | < 2 000 ms | Perfil de CPU en el Worker para encontrar el cuello de botella |
| Carga del modelo (`tryLoadInterpreter`) | < 1 000 ms | Cargar en background al iniciar la app, no al primer uso |

**Cómo medir con el Profiler:**

1. Profiler → CPU → Record (System Trace).
2. Disparar el análisis manual desde `SettingsScreen` (si hay opción de debug) o
   esperar al cron semanal de WorkManager.
3. Buscar en el flame chart: `CognitiveAnalyzer.analyze` → `TFLiteModelManager.runInference`.
4. La barra del método indica duración real — compararla con los logs de Timber.

---

### Resumen de criterios de rendimiento

| Métrica | Objetivo | Cómo medirlo |
|---|---|---|
| CPU en juego | < 50 % sostenido | CPU Profiler → Callstack Sample |
| CPU tras terminar el juego | < 10 % | CPU Profiler — observar bajada |
| Heap durante juego | +20–40 MB máx. | Memory Profiler — pico y liberación |
| Heap tras GC | Vuelve al nivel base | Memory Profiler → Force GC |
| Red en modo offline | 0 bytes durante juegos | Network Profiler — modo avión |
| Inferencia TFLite | < 500 ms | Logcat `tag:TFLiteModelManager` |
| Pipeline ML completo | < 2 000 ms | Logcat `tag:CognitiveAnalyzer` |

---

## 7.2 Cómo depurar crashes comunes

### Guía rápida: encontrar el crash en Logcat

Cuando la app cierra inesperadamente, el crash aparece en Logcat con nivel **ERROR**.
Los pasos para localizarlo siempre son los mismos:

**1. Filtrar por FATAL o AndroidRuntime**

En Android Studio → Logcat → campo de búsqueda:

```
FATAL EXCEPTION
```
o por nivel:
```
package:com.eternamente.app  level:error
```

**2. Buscar la línea `Caused by:`**

El stack trace tiene dos partes:
```
E  AndroidRuntime: FATAL EXCEPTION: main
E  AndroidRuntime: Process: com.eternamente.app, PID: 12345
E  AndroidRuntime: java.lang.RuntimeException: Unable to start activity ...
E  AndroidRuntime:     at android.app.ActivityThread...
E  AndroidRuntime: Caused by: <— ESTA ES LA CAUSA REAL
E  AndroidRuntime:     at com.eternamente.app...  <— ESTA ES TU LÍNEA DE CÓDIGO
```

Ignorar las primeras líneas del sistema. La causa real está siempre después de `Caused by:`.

**3. Localizar la línea del proyecto**

Dentro del stack trace, buscar líneas con `com.eternamente.app`. Esas son las del código propio.
Hacer clic en la línea azul en Android Studio para ir directamente al archivo.

**4. Reproducir en debug**

Conectar el dispositivo o emulador en modo debug y reproducir el crash.
Los logs de Timber aparecen antes del crash y muestran el estado de la app en ese momento.

---

### Crashes documentados

---

#### 1. `NetworkOnMainThreadException`

**Causa probable**

Se está haciendo una llamada de red (o acceso a Room sin `suspend`) en el hilo principal (Main Thread).
En EternaMente esto puede ocurrir si se llama a `UserRepository` o `FirebaseAuth` directamente
desde un `@Composable` o desde un `init {}` de ViewModel sin `viewModelScope.launch`.

**Cómo identificarlo en Logcat**

```
Caused by: android.os.NetworkOnMainThreadException
    at com.eternamente.app.data.repository.UserRepositoryImpl.login(...)
```

**Cómo solucionarlo**

Todas las llamadas a Room y Firebase deben estar dentro de una corrutina con `Dispatchers.IO`:

```kotlin
// ✗ INCORRECTO — bloquea el hilo principal
val user = userRepository.getUserById(id)

// ✓ CORRECTO — dentro de viewModelScope o withContext
viewModelScope.launch {
    val user = userRepository.getUserById(id)  // suspend fun → va a IO automáticamente
}
```

En EternaMente, `UserRepositoryImpl` y todos los repositorios ya usan `withContext(Dispatchers.IO)`.
Si aparece este error, revisar si se añadió una llamada sin `launch` o sin `suspend`.

---

#### 2. `IllegalStateException: Cannot access database on the main thread`

**Causa probable**

Se está accediendo a Room directamente desde el hilo principal sin una función `suspend`.
También puede ocurrir después de que la base de datos se cierra (raro, pero puede pasar
si el `Application` se destruye antes de completar una operación en background).

**Cómo identificarlo en Logcat**

```
Caused by: java.lang.IllegalStateException:
    Cannot access database on the main thread since it may potentially lock the UI
    at androidx.room.RoomDatabase.assertNotMainThread(...)
    at com.eternamente.app.data.local.db.dao.UserDao.getById(...)
```

**Cómo solucionarlo**

```kotlin
// ✗ INCORRECTO
val entity = userDao.getById(id)  // llamado desde main thread

// ✓ CORRECTO — Room con corrutinas (ya implementado en el proyecto)
suspend fun getUserById(id: String) = withContext(Dispatchers.IO) {
    userDao.getById(id)
}
```

En EternaMente, los DAOs en `data/local/db/dao/` están anotados para corrutinas y todos los
repositorios usan `withContext(Dispatchers.IO)`. Si aparece este crash, verificar que no se
llamó a un DAO directamente desde un `BroadcastReceiver.onReceive()` sin `runBlocking` —
`DailyReminderReceiver` ya usa `runBlocking` exactamente por esta razón.

---

#### 3. `OutOfMemoryError` en juegos

**Causa probable**

El juego de **Memorama** (`MemoryMatchScreen`) o **Encuentra Diferencias** (`SpotDiffScreen`)
carga imágenes grandes en memoria. Si el dispositivo tiene poca RAM o se juegan muchas sesiones
seguidas sin liberar recursos, la VM agota el heap.

También puede ocurrir si `MetricsCollector` en `GameEngine` acumula miles de `TrialMetrics`
sin hacer `reset()` entre sesiones.

**Cómo identificarlo en Logcat**

```
E  AndroidRuntime: java.lang.OutOfMemoryError: Failed to allocate a X byte allocation
    at com.eternamente.app.presentation.games.memorymatch.MemoryMatchEngine...
```

o:
```
W  Glide: Load failed for [image_url]
   java.lang.OutOfMemoryError
```

**Cómo solucionarlo**

1. **Imágenes**: Coil (ya usado en el proyecto) gestiona el caché automáticamente. Si hay OOM
   con imágenes, usar `rememberAsyncImagePainter` con `size = Size.ORIGINAL` desactivado:

   ```kotlin
   AsyncImage(
       model = ImageRequest.Builder(LocalContext.current)
           .data(imageRes)
           .size(400, 400)   // limitar resolución máxima
           .build(),
       contentDescription = null
   )
   ```

2. **MetricsCollector**: `GameBaseViewModel.onCleared()` ya llama a `engine.metrics.reset()`.
   Si el crash ocurre en medio de un juego, verificar que el juego llama a `reset()` al reiniciar.

3. **Diagnóstico rápido**: Filtrar en Logcat por `GC_` para ver presión de memoria antes del crash:
   ```
   tag:art  GC_
   ```

---

#### 4. `NullPointerException` en ViewModel

**Causa probable**

En EternaMente los ViewModels usan `StateFlow` con valores iniciales, por lo que los NPE
son raros. Las causas más frecuentes son:

- Acceder a `hiltViewModel()` fuera del contexto de composición correcto.
- Llamar a `navController.getBackStackEntry(route)` para una ruta que ya no está en el back stack
  (visible en `PdfExportScreen` que usa `hiltViewModel(weeklyEntry)`).
- `lateinit var` no inicializado en un `BroadcastReceiver` sin `@AndroidEntryPoint`.

**Cómo identificarlo en Logcat**

```
Caused by: java.lang.NullPointerException: Attempt to invoke virtual method '...' on a null object reference
    at com.eternamente.app.presentation.reports.PdfExportScreen$...
```

**Cómo solucionarlo**

```kotlin
// En NavGraph.kt — PdfExportScreen ya tiene protección con runCatching:
val weeklyEntry = remember(navController) {
    runCatching {
        navController.getBackStackEntry(Screen.WeeklyReport.route)
    }.getOrNull()   // null si la ruta no está en el back stack
}
val sharedReportVm: ReportViewModel? = weeklyEntry?.let {
    hiltViewModel(it)
}
// Luego usar el operador Elvis para el estado:
val reportState = sharedReportVm?.state?.collectAsState()?.value ?: ReportState()
```

Para `BroadcastReceiver`: confirmar que tiene `@AndroidEntryPoint` y que el `lateinit var`
inyectado es de tipo no nulable. `DailyReminderReceiver` y `BootReceiver` ya están correctos.

---

#### 5. `CancellationException` en corrutinas

**Causa probable**

`CancellationException` no es un crash en sí — es el mecanismo normal con el que Kotlin
cancela corrutinas cuando un `ViewModel` se destruye (`onCleared()`). El problema real ocurre
cuando se captura con un `catch (e: Exception)` genérico sin re-lanzarla.

En EternaMente, `safeCall {}` en `core/Result.kt` captura todas las `Exception`. Si una
operación larga (análisis ML, guardado Room) se interrumpe por navegación, `safeCall` convertirá
la `CancellationException` en un `Result.Error` en lugar de cancelar correctamente.

**Cómo identificarlo en Logcat**

```
W  EternaFCM: Auth: login Firebase diferido (sin red)
   kotlinx.coroutines.JobCancelledException: Job was cancelled
```

o en modo verbose:
```
D  CoroutineExceptionHandler: CancellationException
```

**Cómo solucionarlo**

Re-lanzar `CancellationException` en cualquier bloque `catch` genérico:

```kotlin
// ✗ INCORRECTO — traga la cancelación
try {
    someHeavyOperation()
} catch (e: Exception) {
    Timber.e(e, "Error")   // CancellationException queda silenciada
}

// ✓ CORRECTO — re-lanzar para que el sistema de corrutinas funcione
try {
    someHeavyOperation()
} catch (e: CancellationException) {
    throw e   // siempre re-lanzar
} catch (e: Exception) {
    Timber.e(e, "Error")
}
```

`safeCall` en el proyecto captura `Exception` genérica. Para operaciones críticas que deben
respetar la cancelación (análisis ML en `CognitiveAnalysisWorker`), se recomienda usar
`runCatching` combinado con `ensureActive()`:

```kotlin
ensureActive()   // lanza CancellationException si el Job fue cancelado
val result = heavyMlOperation()
```

---

#### 6. TFLite: `wrong type at input` / `Cannot copy to a TensorFlowLite tensor`

**Causa probable**

El modelo TFLite de EternaMente (`eternamente_ml_v1.tflite`) espera un tensor de tipo
`FLOAT32` con forma `[1, N_FEATURES]`. Si el número de features cambia (actualmente 14,
definido en `FeatureExtractor`) o si los datos no se normalizan con `FeatureNormalizer`
antes de pasarlos al modelo, TFLite lanza una excepción en tiempo de ejecución.

También ocurre si el archivo `.tflite` en `assets/` es una versión diferente al código.

**Cómo identificarlo en Logcat**

```
E  tflite  : Cannot copy to a TensorFlowLite tensor (input_1): expected type FLOAT32
    at com.eternamente.app.domain.ml.TFLiteModelManager.runInference(...)
```

o:

```
E  tflite  : Tensor input_0 has shape [1,14] but input shape is [1,12]
    at com.eternamente.app.domain.ml.TFLiteModelManager...
```

**Cómo solucionarlo**

1. Verificar que `FeatureExtractor.extract()` devuelve exactamente 14 features
   (constante en `FeatureExtractor.FEATURE_COUNT`).

2. Confirmar que el pipeline siempre pasa por `FeatureNormalizer.normalize()` antes de
   `TFLiteModelManager.runInference()`.

3. Si se actualiza el modelo `.tflite`, actualizar también `ML_MODEL_VERSION` en `build.gradle.kts`
   y verificar la forma del tensor con:

   ```kotlin
   // Diagnóstico — añadir temporalmente en TFLiteModelManager
   val inputShape = interpreter.getInputTensor(0).shape()
   Timber.d("ML: forma del tensor de entrada = ${inputShape.toList()}")
   ```

4. Filtrar en Logcat por `tflite` para ver todos los mensajes del runtime de TFLite.

---

#### 7. Room: `no such table` / `SQLiteException: no such table`

**Causa probable**

La base de datos Room tiene un número de versión (`version`) en `AppDatabase`. Si se añade
una entidad nueva o se modifica el esquema sin incrementar la versión y proporcionar una
`Migration`, Room lanza este error al intentar acceder a la tabla nueva.

También ocurre si se desinstala la app y se reinstala con datos de Room que quedaron en el
almacenamiento del dispositivo de una versión anterior (raro en desarrollo normal).

**Cómo identificarlo en Logcat**

```
E  SQLiteLog: (1) no such table: cognitive_sessions
   Caused by: android.database.sqlite.SQLiteException: no such table: cognitive_sessions (code 1)
    at com.eternamente.app.data.local.db.dao.SessionDao.getAll(...)
```

o si hay un cambio de esquema sin migración:

```
E  Room    : java.lang.IllegalStateException: Room cannot verify the data integrity.
             Looks like you've changed schema but forgot to update the version number.
```

**Cómo solucionarlo**

**En desarrollo** (datos locales no importan):

```kotlin
// En DatabaseModule.kt — añadir temporalmente fallbackToDestructiveMigration()
Room.databaseBuilder(context, AppDatabase::class.java, "eternamente.db")
    .fallbackToDestructiveMigration()   // ← borra y recrea la DB
    .build()
```

⚠️ **Nunca usar `fallbackToDestructiveMigration()` en producción** — borra todos los datos del usuario.

**En producción** (cuando haya usuarios reales):

```kotlin
// Incrementar version en AppDatabase
@Database(entities = [...], version = 2)  // era 1

// Proveer la migración en DatabaseModule.kt
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE users ADD COLUMN firebaseUid TEXT")
    }
}

Room.databaseBuilder(...)
    .addMigrations(MIGRATION_1_2)
    .build()
```

Los esquemas exportados en `app/schemas/` (generados por KSP con `room.schemaLocation`)
sirven para verificar qué cambió entre versiones.

---

### Tabla resumen

| Error | Contexto en EternaMente | Solución rápida |
|---|---|---|
| `NetworkOnMainThreadException` | Firebase/Room sin corrutina | `viewModelScope.launch {}` |
| `IllegalStateException: database` | DAO en main thread | `withContext(Dispatchers.IO)` |
| `OutOfMemoryError` | Juegos con imágenes / MetricsCollector | Limitar resolución Coil, `metrics.reset()` |
| `NullPointerException` en ViewModel | Back stack o `lateinit` no inicializado | `runCatching + getOrNull()` en `getBackStackEntry` |
| `CancellationException` silenciada | `safeCall` o `catch (Exception)` genérico | Re-lanzar siempre la `CancellationException` |
| TFLite `wrong type` | Features count o normalización incorrectos | Verificar `FEATURE_COUNT` y pipeline ML |
| Room `no such table` | Esquema cambiado sin Migration | `fallbackToDestructiveMigration()` en dev, `Migration` en prod |

---

### Comandos útiles de Logcat para EternaMente

```bash
# Ver solo logs de la app
adb logcat --pid=$(adb shell pidof com.eternamente.app)

# Filtrar crashes
adb logcat AndroidRuntime:E *:S

# Filtrar logs de Timber (tag = clase donde se llama)
adb logcat -s "AuthViewModel" "LoginUserUseCase" "CognitiveAnalysisWorker"

# Ver presión de memoria
adb logcat -s art | grep GC_

# Ver logs de TFLite
adb logcat -s tflite

# Ver logs de Room/SQLite
adb logcat -s SQLiteLog

# Ver token FCM al arrancar
adb logcat -s EternaFCM_Token
```
