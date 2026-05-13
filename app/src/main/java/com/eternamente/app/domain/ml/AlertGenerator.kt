package com.eternamente.app.domain.ml

import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain

/**
 * Generates human-readable, lay-language explanations in Spanish for cognitive analysis results.
 *
 * **Prohibited vocabulary:** demencia, Alzheimer, deterioro, anormal, peligro.
 * All messages use encouraging, non-alarmist language appropriate for adults 60+.
 */
object AlertGenerator {

    fun generate(alertLevel: AlertLevel, flaggedDomains: List<CognitiveDomain>): String =
        when (alertLevel) {
            AlertLevel.NORMAL -> NORMAL_MESSAGE
            AlertLevel.WATCH  -> buildWatchMessage(flaggedDomains)
            AlertLevel.ALERT  -> ALERT_MESSAGE
        }

    // ── Message templates ──────────────────────────────────────────────────────

    private const val NORMAL_MESSAGE =
        "Tu rendimiento cognitivo se mantiene estable esta semana. ¡Sigue así!"

    private const val ALERT_MESSAGE =
        "Tu rendimiento en los últimos días muestra cambios que podrían merecer atención. " +
        "Te recomendamos conversar con tu médico y mostrarle tu reporte."

    private fun buildWatchMessage(domains: List<CognitiveDomain>): String {
        if (domains.isEmpty()) {
            return "Hemos notado algunos cambios en tu rendimiento esta semana. " +
                "No es motivo de alarma, pero te recomendamos seguir practicando con regularidad."
        }
        val domainStr = when (domains.size) {
            1    -> domainName(domains[0])
            2    -> "${domainName(domains[0])} y ${domainName(domains[1])}"
            else -> domains.dropLast(1).joinToString(", ") { domainName(it) } +
                    " y ${domainName(domains.last())}"
        }
        return "Hemos notado que esta semana tardaste un poco más en los juegos de $domainStr. " +
            "No es motivo de alarma, pero te recomendamos seguir practicando."
    }

    // ── Domain name localisation ───────────────────────────────────────────────

    private fun domainName(domain: CognitiveDomain): String = when (domain) {
        CognitiveDomain.MEMORY           -> "memoria"
        CognitiveDomain.ATTENTION        -> "atención"
        CognitiveDomain.EXECUTIVE        -> "función ejecutiva"
        CognitiveDomain.LANGUAGE         -> "lenguaje"
        CognitiveDomain.ORIENTATION      -> "orientación"
        CognitiveDomain.PROCESSING_SPEED -> "velocidad de procesamiento"
    }
}
