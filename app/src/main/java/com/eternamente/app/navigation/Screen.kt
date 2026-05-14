package com.eternamente.app.navigation

/**
 * Jerarquía sellada de destinos de navegación para EternaMente.
 *
 * **Contrato de tipos:**
 * - Los `object` subclasses: [route] es el destino definitivo (sin argumentos).
 *   Se usa igual para registrar el composable y para navegar.
 * - Los `data class` subclasses: [route] es la **plantilla URI** con marcadores
 *   `{arg}` para el registro en [NavHost]. Para navegar se llama a [navRoute],
 *   que sustituye los parámetros reales.
 *
 * **Uso correcto:**
 * ```kotlin
 * // Registrar en NavHost → usar route (plantilla)
 * composable(route = Screen.GamePlay.ROUTE) { ... }
 *
 * // Navegar → usar navRoute() (valores reales)
 * navController.navigate(Screen.GamePlay("stroop", "session-42").navRoute())
 * ```
 *
 * **Nunca** pasar strings literales de ruta fuera de esta clase.
 */
sealed class Screen(val route: String) {

    /**
     * Ruta de navegación resuelta con los valores reales de los argumentos.
     * Para `object` subclasses equivale a [route]; los `data class` la sobreescriben.
     */
    open fun navRoute(): String = route

    // ── Auth flow ─────────────────────────────────────────────────────────────

    /** Pantalla de bienvenida animada. Punto de entrada cuando el usuario no está autenticado. */
    object Splash : Screen("splash")

    /**
     * Carrusel de onboarding de N pasos.
     *
     * @property step Índice del paso actual (0-based).
     */
    data class Onboarding(val step: Int) : Screen("onboarding/{step}") {
        override fun navRoute(): String = "onboarding/$step"
        companion object { const val ROUTE = "onboarding/{step}" }
    }

    /** Formulario de creación de cuenta. */
    object Register : Screen("register")

    /** Formulario de inicio de sesión. */
    object Login : Screen("login")

    /** Pantalla de consentimiento informado, requerida antes de cualquier evaluación. */
    object Consent : Screen("consent")

    // ── Main flow ─────────────────────────────────────────────────────────────

    /** Panel principal con resumen cognitivo y acceso rápido a juegos. */
    object Dashboard : Screen("dashboard")

    /** Catálogo de juegos cognitivos disponibles para la sesión. */
    object GameCatalog : Screen("game_catalog")

    /**
     * Instrucciones y configuración previas al juego.
     *
     * @property gameId Identificador estable del mini-juego (ej. `"stroop"`).
     */
    data class GameInstructions(val gameId: String) : Screen("game_instructions/{gameId}") {
        override fun navRoute(): String = "game_instructions/$gameId"
        companion object { const val ROUTE = "game_instructions/{gameId}" }
    }

    /**
     * Pantalla de juego activo.
     *
     * @property gameId  Identificador del mini-juego.
     * @property sessionId UUID de la [com.eternamente.app.domain.model.CognitiveSession] activa.
     */
    data class GamePlay(val gameId: String, val sessionId: String)
        : Screen("game_play/{gameId}/{sessionId}") {
        override fun navRoute(): String = "game_play/$gameId/$sessionId"
        companion object { const val ROUTE = "game_play/{gameId}/{sessionId}" }
    }

    /**
     * Pantalla de resultado al finalizar un juego.
     *
     * @property gameId Identificador del mini-juego completado.
     * @property score  Puntuación normalizada (0–100).
     */
    data class GameResult(val gameId: String, val score: Float)
        : Screen("game_result/{gameId}/{score}") {
        override fun navRoute(): String = "game_result/$gameId/$score"
        companion object { const val ROUTE = "game_result/{gameId}/{score}" }
    }

    // ── Profile flow ──────────────────────────────────────────────────────────

    /** Vista del perfil demográfico y estadísticas del usuario. */
    object Profile : Screen("profile")

    /** Colección de medallas y logros desbloqueados. */
    object Achievements : Screen("achievements")

    /** Ajustes generales de la aplicación. */
    object Settings : Screen("settings")

    /** Configuración de accesibilidad (tamaño de texto, contraste, haptics). */
    object AccessibilitySettings : Screen("accessibility_settings")

    // ── Reports flow ──────────────────────────────────────────────────────────

    /** Reporte de evolución cognitiva de la última semana. */
    object WeeklyReport : Screen("weekly_report")

    /** Reporte mensual con comparativa de baseline. */
    object MonthlyReport : Screen("monthly_report")

    /** Exportación del reporte cognitivo a PDF. */
    object PdfExport : Screen("pdf_export")

    /**
     * Detalle ampliado de una alerta cognitiva específica.
     *
     * @property alertId UUID de la [com.eternamente.app.domain.model.MlPrediction] que generó la alerta.
     */
    data class AlertDetail(val alertId: String) : Screen("alert_detail/{alertId}") {
        override fun navRoute(): String = "alert_detail/$alertId"
        companion object { const val ROUTE = "alert_detail/{alertId}" }
    }
}
