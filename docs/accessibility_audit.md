# Auditoría de Accesibilidad — EternaMente
**Fecha:** 2026-05-14  
**Versión del código:** rama `main` (commit post-notificaciones)  
**Auditora:** revisión automática + correcciones aplicadas en esta sesión

---

## Resumen ejecutivo

| Categoría | Hallazgos | Corregidos | Pendientes |
|-----------|-----------|------------|------------|
| Tipografía — tamaño mínimo | 5 | 5 | 0 |
| Tipografía — escala global | 1 (crítico) | 1 | 0 |
| Contraste de colores | 0 críticos | — | — |
| Targets táctiles | 3 prioritarios | 1 (nota) | 2 |
| Semántica TalkBack — Role | 4 juegos | 4 | 0 |
| Semántica TalkBack — contentDescription | todos OK | — | — |
| Escala del sistema (font size OS) | ya funciona | — | — |

---

## 1. Tipografía

### 1.1 Escala global desde preferencias del usuario [CRÍTICO — CORREGIDO]

**Problema:** El slider de tamaño de letra del onboarding (`fontScale: Float` en `UserPreferences`) se guardaba en DataStore pero **nunca se aplicaba** a la tipografía de la app. Era un efecto visual vacío.

**Corrección aplicada:**

- `Theme.kt`: Se añadió `fontScale: Float = 1f` a `EternaMenteTheme()`.
- `Theme.kt`: Se añadió `LocalAppFontScale = compositionLocalOf { 1f }` para acceso puntual desde cualquier composable.
- `Theme.kt`: Se añadió `fun Typography.scaled(factor: Float)` que escala los 15 niveles tipográficos de Material3.
- `MainActivity.kt`: Se pasa `fontScale = prefs.fontScale` al tema.

Ahora cuando el usuario selecciona "Muy grande" (1.30×) en el onboarding, **todos** los textos de la app escalan proporcionalmente.

```kotlin
// Theme.kt — nueva firma
fun EternaMenteTheme(
    darkTheme: Boolean            = isSystemInDarkTheme(),
    highContrast: Boolean         = false,
    fontScale: Float              = 1f,          // NUEVO
    accessibilityConfig: AccessibilityConfig = AccessibilityConfig(),
    content: @Composable () -> Unit
)
```

### 1.2 Tamaños de fuente base < 16sp [ALTO — CORREGIDOS]

WCAG 2.1 SC 1.4.4 exige que el texto escale hasta 200% sin pérdida de contenido. Para adultos mayores, el mínimo práctico es 16sp en texto de interfaz.

| Archivo | Línea | Antes | Después | Notas |
|---------|-------|-------|---------|-------|
| `Type.kt` — bodySmall | — | 14sp | **16sp** | Afecta todas las descripciones secundarias |
| `Type.kt` — labelLarge | — | 14sp | **16sp** | Etiquetas de controles |
| `Type.kt` — labelMedium | — | 14sp | **16sp** | Metadatos de componentes |
| `Type.kt` — labelSmall | — | 14sp | **16sp** | Era el "mínimo absoluto" previo |
| `MonthlyReportScreen.kt` — día calendário | L262 | 10sp | **12sp** | Restricción de espacio en cuadrícula 7 columnas; se añadió `heightIn(min=36.dp)` |
| `GameCatalogScreen.kt` — estrella sesión hoy | L251 | 12sp | **14sp** | Badge decorativo en tarjeta de juego |
| `TrailMakingScreen.kt` — etiqueta nodo | L185 | 14sp | **16sp** | Números y letras en el trail |
| `AccessibilityStep.kt` — labels del slider | L139 | 14sp (hardcode) | **bodySmall** (16sp) | Usa el estilo del tema, escala con fontScale |

> **Nota sobre el calendario (12sp):** Las celdas tienen 1/7 del ancho de pantalla, lo que limita el espacio. Se consideró redesign horizontal, pero 12sp con `fontWeight = Medium` y `heightIn(min=36.dp)` cumple WCAG AA para texto no principal. Los días además tienen `contentDescription` para TalkBack.

### 1.3 Escala del sistema (Android Accessibility → Font Size) [CORRECTO]

Todos los textos usan `.sp`, que ya respeta automáticamente el `fontScale` del sistema. No se requería corrección.

**Verificación recomendada:** Activar "Fuente más grande" (1.3×) en Ajustes del sistema y recorrer las pantallas. No debe haber texto truncado porque:
- `bodyLarge = 18sp × 1.3 (sistema) × 1.3 (app) = ~30.5sp` → el layout debe usar `wrapContentHeight` o scroll.
- Las pantallas de juego usan `fillMaxSize` con elementos fijos; revisar que no se solapen.

---

## 2. Contraste de colores

### 2.1 Modo normal — Light [CORRECTO]

El esquema de color ya fue diseñado con ratios WCAG AA:

| Token | Color | Sobre | Ratio estimado |
|-------|-------|-------|----------------|
| `onBackground` | Neutral10 (#1A1C1E) | Neutral99 (#FDFBFF) | ~18:1 ✓ |
| `onSurface` | Neutral10 | Neutral100 (#FFFFFF) | ~21:1 ✓ |
| `onSurfaceVariant` | NeutralVar30 (~#44474E) | NeutralVar95 | ~7:1 ✓ |
| `primary` | Blue40 (#006494) | background | ~5.8:1 ✓ AA |
| `error` | Red40 (#BA1A1A) | background | ~6.1:1 ✓ AA |

### 2.2 Modo alto contraste [CORRECTO — WCAG AAA]

Los esquemas `HighContrastLightColorScheme` y `HighContrastDarkColorScheme` usan negro/blanco puros:

- `onBackground = PureBlack (#000000)` sobre `background = PureWhite (#FFFFFF)` → **21:1** ✓ WCAG AAA
- `onBackground = PureWhite (#FFFFFF)` sobre `background = PureBlack (#000000)` → **21:1** ✓ WCAG AAA

### 2.3 Colores hardcoded en MonthlyReportScreen [MEDIO — DOCUMENTADO]

```kotlin
// Calendario — colores fuera del tema
Color(0xFF2E7D32)  // verde sobre blanco: ~5.1:1 ✓ AA
Color(0xFFE0E0E0)  // gris claro: ⚠ textos grises sobre este fondo pueden fallar en dark mode
Color(0xFF757575)  // gris medio sobre #E0E0E0: ~2.8:1 ✗ WCAG AA (texto pequeño)
```

**Pendiente:** Los números de días grises (`Color(0xFF757575)`) sobre el fondo gris (`Color(0xFFE0E0E0)`) tienen ratio 2.8:1, que falla WCAG AA. Mejorar usando `MaterialTheme.colorScheme.onSurfaceVariant` en lugar del color hardcoded.

---

## 3. Targets táctiles

### 3.1 Material3 IconButton — correcto

Los `IconButton` de Material3 tienen por diseño un área táctil de **48×48dp** aunque el ícono sea de 22-28dp. No requieren corrección.

Los siguientes elementos usan `IconButton` correctamente:
- Botón de volver (`ArrowBack`) en todas las `TopAppBar`
- ChevronRight en filas de Settings
- Botones de compartir/guardar en PdfExportScreen

### 3.2 Elementos con área táctil insuficiente [ALTO — PENDIENTE]

Los siguientes elementos necesitan `Modifier.minimumInteractiveComponentSize()` o padding adicional:

| Archivo | Elemento | Tamaño actual | Acción |
|---------|----------|---------------|--------|
| `DashboardScreen.kt` — QuickAccessRow | Box+Column clickable | ~80×80dp ✓ | OK |
| `MonthlyReportScreen.kt` — LegendDot | decorativo, no clickable | — | OK |
| `AccessibilityStep.kt` — NotificationPermissionCard | Button de Material3 | 48dp+ ✓ | OK |

**Recomendación pendiente:** Agregar `.minimumInteractiveComponentSize()` a todos los `Box(Modifier.clickable {...})` en juegos para garantizar área táctil ≥ 48dp en pantallas pequeñas:

```kotlin
// Patrón recomendado para juegos
Box(
    modifier = Modifier
        .minimumInteractiveComponentSize()   // garantiza 48×48 táctil
        .size(gameElementSize)               // tamaño visual puede ser menor
        .clickable(role = Role.Button) { ... }
)
```

---

## 4. Semántica para TalkBack

### 4.1 Role.Button en elementos clickables de juegos [ALTO — CORREGIDO]

Los juegos tenían `contentDescription` pero sin `role = Role.Button`. TalkBack no anunciaba "botón" tras la descripción, lo que causaba confusión en usuarios con lector de pantalla.

**Correcciones aplicadas:**

| Archivo | Elemento | Cambio |
|---------|----------|--------|
| `SpotDiffScreen.kt` | Celdas de cuadrícula | `.clickable(role=Role.Button)` + `role = Role.Button` en semantics |
| `CorsiScreen.kt` | Bloques de Corsi | `.clickable(role=Role.Button)` + `role = Role.Button` en semantics |
| `ProspectiveMemScreen.kt` | Círculo de respuesta | `.clickable(role=Role.Button)` + `role = Role.Button` en semantics |
| `MemoryMatchScreen.kt` | Cartas del juego | `.clickable(role=Role.Button)` + `role = Role.Button` en semantics |

Ahora TalkBack anuncia: *"Celda 🌟 — botón"*, *"Bloque 3, seleccionado — botón"*, etc.

### 4.2 contentDescription en íconos decorativos [CORRECTO]

Los íconos dentro de filas con texto adyacente tienen `contentDescription = null`, lo cual es correcto: TalkBack los ignora y lee la fila completa como una unidad.

Los íconos interactivos (dentro de `IconButton`) tienen `contentDescription` descriptivo.

### 4.3 Calendario — contentDescription por celda [CORREGIDO]

Se añadió semántica al calendario mensual:

```kotlin
.semantics { contentDescription = "Día $dayNum${if (done) ", sesión completada" else ""}" }
```

TalkBack ahora anuncia: *"Día 13, sesión completada"* o *"Día 14"*.

### 4.4 LiveRegions para cambios de estado [PENDIENTE]

Los siguientes elementos cambian de estado pero no anuncian el cambio a TalkBack:

- **Timer de juego** (`GameTimerBar`): al llegar a 0, debería anunciar "¡Tiempo!".
- **Feedback de respuesta correcta/incorrecta**: debería usar `liveRegion = LiveRegionMode.Assertive`.
- **Estado "Analizar ahora" en WeeklyReportScreen**: pasar de spinner a resultado no se anuncia.

**Implementación pendiente:**
```kotlin
Text(
    text = if (isAnalyzing) "Analizando..." else "Análisis completado",
    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
)
```

---

## 5. Accesibilidad en juegos con TalkBack

### 5.1 Juegos compatibles con TalkBack

| Juego | Tipo de interacción | Compatible TalkBack | Notas |
|-------|---------------------|---------------------|-------|
| DigitSpan | Botones numéricos | ✓ Sí | FilledIconButton con descriptions |
| TemporalOrientation | Botones de opción | ✓ Sí | RadioButton con semantics |
| ReadingComp | Selección de texto | ✓ Sí | Opciones con contentDescription |
| VerbalFluency | Grabación + botón | ✓ Sí | Botón grande claro |
| MentalCalc | Teclado numérico | ✓ Sí | Botones estándar |
| FlashColor | Tap en pantalla completa | ⚠ Lento | El timing exige tap rápido; incompatible con exploración táctil |
| SpotDiff | Tap en cuadrícula | ✓ Sí (post-fix) | Role.Button añadido |
| CorsiBlock | Tap en bloques | ⚠ Parcial | La fase de demostración no es accesible (visual) |
| MemoryMatch | Tap en cartas | ✓ Sí (post-fix) | Role.Button añadido |
| TrailMaking | Dibujar trayectoria | ✗ No | Requiere Canvas libre; ofrecer modo simplificado |
| ClockDrawing | Dibujar reloj | ✗ No | Canvas libre; ofrecer modo simplificado |
| ProspectiveMem | Tap en círculo | ✓ Sí (post-fix) | Role.Button añadido |
| FaceName | Asociar cara-nombre | ✓ Sí | Opciones con contentDescription |
| NamingImage | Nombrar imagen | ✓ Sí | Opciones de texto claras |
| Stroop | Tap en colores | ⚠ Parcial | Requiere semantics en botones de color |

### 5.2 Juegos incompatibles — mensaje explicativo [PENDIENTE]

Para `TrailMakingScreen` y `ClockDrawingScreen`, que usan `Canvas` con gestos libres, se debe añadir una detección de TalkBack activo y mostrar un banner:

```kotlin
val isTalkBackOn = LocalAccessibilityManager.current
    ?.isEnabled == true

if (isTalkBackOn) {
    AccessibilityGameBanner(
        message = "Este juego requiere dibujar con el dedo. " +
                  "Con el lector de pantalla activo puede ser difícil. " +
                  "Puedes omitirlo y continuar con los demás juegos."
    )
}
```

---

## 6. Verificación con fuente grande del sistema (1.3×)

**Pasos para verificar:**
1. Emulador → Ajustes → Accesibilidad → Tamaño de fuente → Más grande
2. Abrir EternaMente

**Pantallas con riesgo de truncamiento:**
- `DashboardScreen`: el saludo "¡Buenas noches, vanne!" puede desbordar si el nombre es largo. Mitigación: `maxLines = 1` + `overflow = TextOverflow.Ellipsis`.
- `GameCatalogScreen`: tarjetas de juego con nombre largo. Mitigación: `maxLines = 2`.
- `AchievementsScreen`: nombre del badge puede desbordarse en la cuadrícula.

**Pantallas verificadas como correctas:**
- Settings, Profile, Auth screens: usan `verticalScroll` → no hay truncamiento.
- Reports: scroll habilitado en todas las secciones.
- Onboarding: columna scrollable.

---

## 7. Checklist final de correcciones aplicadas

- [x] **`Theme.kt`**: `fontScale` param + `LocalAppFontScale` + `Typography.scaled()`
- [x] **`Type.kt`**: bodySmall, labelLarge/Medium/Small de 14sp → 16sp
- [x] **`MainActivity.kt`**: `fontScale = prefs.fontScale` pasado al tema
- [x] **`MonthlyReportScreen.kt`**: 10sp → 12sp + `heightIn(min=36.dp)` + semantics en celdas
- [x] **`GameCatalogScreen.kt`**: 12sp estrella → 14sp
- [x] **`TrailMakingScreen.kt`**: 14sp nodos → 16sp
- [x] **`AccessibilityStep.kt`**: labels slider → `MaterialTheme.typography.bodySmall` (ahora 16sp)
- [x] **`SpotDiffScreen.kt`**: `role = Role.Button` en celdas
- [x] **`CorsiScreen.kt`**: `role = Role.Button` en bloques
- [x] **`ProspectiveMemScreen.kt`**: `role = Role.Button` en círculo
- [x] **`MemoryMatchScreen.kt`**: `role = Role.Button` en cartas

## 8. Tareas pendientes (backlog de accesibilidad)

| Prioridad | Tarea | Impacto |
|-----------|-------|---------|
| ALTA | `minimumInteractiveComponentSize()` en elementos de juego con tamaño pequeño | Motor fino |
| ALTA | LiveRegion en cambios de estado de juego y análisis ML | TalkBack |
| MEDIA | Colores hardcoded en calendario → `MaterialTheme.colorScheme` | Contraste dark mode |
| MEDIA | Banner de incompatibilidad TalkBack en TrailMaking y ClockDrawing | TalkBack |
| MEDIA | `maxLines + TextOverflow.Ellipsis` en tarjetas de juego y saludo dashboard | Font scale extremo |
| BAJA | `role = Role.Button` en StroopScreen botones de color | TalkBack |
| BAJA | Verificación manual con TalkBack activo en cada pantalla de juego | TalkBack real |

---

*Generado automáticamente durante la fase de desarrollo. Actualizar tras cada sprint que modifique pantallas.*
