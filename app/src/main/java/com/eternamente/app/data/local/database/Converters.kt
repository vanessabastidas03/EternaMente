package com.eternamente.app.data.local.database

import androidx.room.TypeConverter
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.Badge
import com.eternamente.app.domain.model.CognitiveDomain
import com.eternamente.app.domain.model.SessionType
import java.time.LocalDate

/**
 * TypeConverters de Room para [EternaDatabase].
 *
 * Convierte entre los tipos Kotlin del dominio y los primitivos almacenables en SQLite.
 *
 * | Tipo Kotlin          | Tipo SQLite  | Estrategia                                        |
 * |----------------------|--------------|---------------------------------------------------|
 * | [LocalDate]          | INTEGER      | epochDay (días desde 1970-01-01)                  |
 * | [List]<[CognitiveDomain]> | TEXT  | nombres separados por coma                        |
 * | [List]<[Badge]>      | TEXT         | nombres separados por coma                        |
 * | [SessionType]        | TEXT         | `.name` (enum → String)                           |
 * | [AlertLevel]         | TEXT         | `.name`                                           |
 *
 * Todos los conversores de listas son robustos ante valores desconocidos:
 * un nombre de enum no reconocido se descarta silenciosamente (runCatching).
 */
class Converters {

    // ── LocalDate ↔ Long (epochDay) ───────────────────────────────────────────

    @TypeConverter
    fun localDateToEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun epochDayToLocalDate(epochDay: Long?): LocalDate? =
        epochDay?.let { LocalDate.ofEpochDay(it) }

    // ── List<CognitiveDomain> ↔ String ────────────────────────────────────────

    @TypeConverter
    fun cognitiveDomainListToString(domains: List<CognitiveDomain>?): String =
        domains?.joinToString(",") { it.name } ?: ""

    @TypeConverter
    fun stringToCognitiveDomainList(value: String?): List<CognitiveDomain> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { CognitiveDomain.valueOf(it.trim()) }.getOrNull() }
    }

    // ── List<Badge> ↔ String ──────────────────────────────────────────────────

    @TypeConverter
    fun badgeListToString(badges: List<Badge>?): String =
        badges?.joinToString(",") { it.name } ?: ""

    @TypeConverter
    fun stringToBadgeList(value: String?): List<Badge> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",")
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { Badge.valueOf(it.trim()) }.getOrNull() }
    }

    // ── SessionType ↔ String ──────────────────────────────────────────────────

    @TypeConverter
    fun sessionTypeToString(type: SessionType?): String? = type?.name

    @TypeConverter
    fun stringToSessionType(value: String?): SessionType? =
        value?.let { runCatching { SessionType.valueOf(it) }.getOrNull() }

    // ── AlertLevel ↔ String ───────────────────────────────────────────────────

    @TypeConverter
    fun alertLevelToString(level: AlertLevel?): String? = level?.name

    @TypeConverter
    fun stringToAlertLevel(value: String?): AlertLevel? =
        value?.let { runCatching { AlertLevel.valueOf(it) }.getOrNull() }
}
