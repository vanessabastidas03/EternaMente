package com.eternamente.app.core.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.eternamente.app.BuildConfig
import com.eternamente.app.domain.model.AlertLevel
import com.eternamente.app.domain.model.CognitiveDomain
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Generates a 5-page cognitive report PDF using [android.graphics.pdf.PdfDocument].
 * No external dependencies — only Android SDK drawing primitives.
 *
 * Output: `context.filesDir/reports/reporte_YYYY-MM-DD.pdf`
 * Sharing: via FileProvider (configure `${applicationId}.fileprovider` in Manifest).
 */
@Singleton
class PdfReportGenerator @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── Page constants (A4 @ 72 dpi) ─────────────────────────────────────────
    private val W  = 595          // page width
    private val H  = 842          // page height
    private val M  = 48           // horizontal margin
    private val CW = W - 2 * M   // content width = 499

    // ── Brand & palette ───────────────────────────────────────────────────────
    private val COL_PRIMARY  = Color.parseColor("#1976D2")
    private val COL_SURFACE  = Color.parseColor("#F5F5F5")
    private val COL_DIVIDER  = Color.parseColor("#E0E0E0")
    private val COL_RED      = Color.parseColor("#C62828")
    private val COL_GREEN    = Color.parseColor("#1B5E20")
    private val COL_ORANGE   = Color.parseColor("#E65100")
    private val COL_DARK     = Color.parseColor("#212121")
    private val COL_GRAY     = Color.parseColor("#757575")

    private val DOMAIN_ORDER = listOf(
        CognitiveDomain.MEMORY, CognitiveDomain.ATTENTION, CognitiveDomain.EXECUTIVE,
        CognitiveDomain.LANGUAGE, CognitiveDomain.ORIENTATION
    )
    private val DOMAIN_SHORT = listOf("Memoria","Atención","F. Ejecutiva","Lenguaje","Orientación")
    private val DOMAIN_ABBR  = listOf("Mem","Ate","Eje","Len","Ori")
    private val DOMAIN_COLORS = intArrayOf(
        Color.parseColor("#1976D2"), Color.parseColor("#388E3C"),
        Color.parseColor("#F57C00"), Color.parseColor("#7B1FA2"),
        Color.parseColor("#0097A7")
    )

    private val dateFmtLong  = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es"))
    private val dateFmtShort = DateTimeFormatter.ofPattern("d MMM", Locale("es"))

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun generate(data: PdfReportData): File = withContext(Dispatchers.IO) {
        val doc = PdfDocument()
        page1Cover(doc, data)
        page2Summary(doc, data)
        page3Charts(doc, data)
        page4AiMessage(doc, data)
        page5Contact(doc, data)

        val dir = File(context.filesDir, "reports").also { it.mkdirs() }
        purgeOldReports(dir)
        val date = Instant.ofEpochMilli(data.generatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        val file = File(dir, "reporte_${date}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        file
    }

    fun expectedFileName(generatedAt: Long = System.currentTimeMillis()): String {
        val date = Instant.ofEpochMilli(generatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
        return "reporte_${date}.pdf"
    }

    // ── Page 1 — Cover ────────────────────────────────────────────────────────

    private fun page1Cover(doc: PdfDocument, data: PdfReportData) {
        val (page, cv) = newPage(doc, 1)

        // Blue header band
        val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        pFill.color = COL_PRIMARY
        cv.drawRect(0f, 0f, W.toFloat(), 110f, pFill)

        // App name in header
        val pWhite = textPaint(22f, Color.WHITE, bold = true)
        drawCenter(cv, "EternaMente", 65f, pWhite)
        val pWhiteSub = textPaint(11f, Color.WHITE)
        drawCenter(cv, "Evaluación Cognitiva Personalizada", 84f, pWhiteSub)

        // Report title
        val pTitle = textPaint(28f, COL_DARK, bold = true)
        drawCenter(cv, "Reporte Cognitivo", 165f, pTitle)
        val pSub = textPaint(14f, COL_GRAY)
        drawCenter(cv, "Informe de seguimiento — generado automáticamente", 188f, pSub)

        // Divider
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_DIVIDER; strokeWidth = 1f; style = Paint.Style.STROKE
        }
        cv.drawLine(M.toFloat(), 205f, (W - M).toFloat(), 205f, pLine)

        // User info box
        pFill.color = COL_SURFACE
        cv.drawRoundRect(RectF(M.toFloat(), 220f, (W - M).toFloat(), 330f), 8f, 8f, pFill)

        val pLabel = textPaint(11f, COL_GRAY)
        val pValue = textPaint(13f, COL_DARK, bold = true)
        var iy = 245f
        listOf(
            "Paciente"   to data.userName,
            "Edad"       to "${data.userAge} años",
            "Generado el" to dateFmtLong.format(
                Instant.ofEpochMilli(data.generatedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            )
        ).forEach { (label, value) ->
            cv.drawText(label, (M + 16).toFloat(), iy, pLabel)
            cv.drawText(value, (M + 16).toFloat(), iy + 16f, pValue)
            iy += 38f
        }

        // Mandatory disclaimer box (red border)
        val pRedStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = COL_RED; strokeWidth = 2f
        }
        val pRedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL; color = Color.parseColor("#FFEBEE")
        }
        val warnRect = RectF(M.toFloat(), 360f, (W - M).toFloat(), 470f)
        cv.drawRoundRect(warnRect, 6f, 6f, pRedFill)
        cv.drawRoundRect(warnRect, 6f, 6f, pRedStroke)

        val pRedBold = textPaint(11f, COL_RED, bold = true)
        cv.drawText("⚠  AVISO IMPORTANTE", (M + 12).toFloat(), 383f, pRedBold)

        val disclaimerTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_RED; textSize = 11f; isAntiAlias = true
        }
        drawWrapped(cv, "Este reporte es informativo y no constituye diagnóstico médico. " +
            "No reemplaza la consulta con un profesional de la salud. " +
            "Ante cualquier duda, consulte a su médico o especialista.", (M + 12).toFloat(), 398f, CW - 24, disclaimerTp)

        // Footer
        drawFooter(cv, 1)
        doc.finishPage(page)
    }

    // ── Page 2 — Weekly summary ───────────────────────────────────────────────

    private fun page2Summary(doc: PdfDocument, data: PdfReportData) {
        val (page, cv) = newPage(doc, 2)
        drawPageHeader(cv, "Resumen Semanal", 2)

        val pSub = textPaint(12f, COL_GRAY)
        cv.drawText(
            "Semana del ${dateFmtShort.format(data.weekStart)} al ${dateFmtShort.format(data.weekEnd)}",
            M.toFloat(), 90f, pSub
        )

        var y = 115f

        // Table header
        val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        pFill.color = COL_PRIMARY
        cv.drawRect(M.toFloat(), y, (W - M).toFloat(), y + 26f, pFill)

        val pHead = textPaint(11f, Color.WHITE, bold = true)
        val cols = floatArrayOf(M.toFloat(), M + 200f, M + 300f, M + 400f)
        val headers = arrayOf("Dominio", "Esta semana", "Línea base", "Diferencia")
        headers.forEachIndexed { i, h -> cv.drawText(h, cols[i] + 6f, y + 17f, pHead) }
        y += 26f

        // Table rows
        val pRow  = textPaint(11f, COL_DARK)
        val pBold = textPaint(11f, COL_DARK, bold = true)
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = COL_DIVIDER; strokeWidth = 0.5f
        }

        DOMAIN_ORDER.forEachIndexed { i, domain ->
            val score   = data.weeklyDomainScores[domain] ?: 0f
            val baseline= data.baselineScore
            val diff    = score - baseline
            val diffStr = if (diff >= 0f) "+%.0f".format(diff) else "%.0f".format(diff)
            val diffCol = when {
                diff > 5f  -> COL_GREEN
                diff < -5f -> COL_RED
                else       -> COL_GRAY
            }

            pFill.color = if (i % 2 == 0) COL_SURFACE else Color.WHITE
            cv.drawRect(M.toFloat(), y, (W - M).toFloat(), y + 26f, pFill)

            cv.drawText(DOMAIN_SHORT[i],               cols[0] + 6f,  y + 17f, pRow)
            cv.drawText("%.0f pts".format(score),      cols[1] + 6f,  y + 17f, pBold)
            cv.drawText("%.0f pts".format(baseline),   cols[2] + 6f,  y + 17f, pRow)
            val pDiff = textPaint(11f, diffCol, bold = true)
            cv.drawText(diffStr,                       cols[3] + 6f,  y + 17f, pDiff)
            y += 26f
            cv.drawLine(M.toFloat(), y, (W - M).toFloat(), y, pLine)
        }

        y += 24f

        // Adherence row
        pFill.color = COL_SURFACE
        cv.drawRoundRect(RectF(M.toFloat(), y, (W - M).toFloat(), y + 46f), 6f, 6f, pFill)
        val pAd = textPaint(12f, COL_DARK)
        cv.drawText("Adherencia (últimas 4 semanas):", (M + 12).toFloat(), y + 17f, pAd)
        val pAdVal = textPaint(12f, COL_PRIMARY, bold = true)
        cv.drawText("${data.daysCompletedIn4W} días activos", (M + 12).toFloat(), y + 32f, pAdVal)
        y += 62f

        // Estado general
        val (statusText, statusColor) = when (data.latestPrediction?.alertLevel) {
            AlertLevel.NORMAL -> "NORMAL"           to COL_GREEN
            AlertLevel.WATCH  -> "EN OBSERVACIÓN"   to COL_ORANGE
            AlertLevel.ALERT  -> "CONSULTAR MÉDICO" to COL_RED
            null              -> "SIN DATOS"         to COL_GRAY
        }
        pFill.color = statusColor
        val statusRect = RectF(M.toFloat(), y, M + 200f, y + 32f)
        cv.drawRoundRect(statusRect, 16f, 16f, pFill)
        val pStatus = textPaint(12f, Color.WHITE, bold = true)
        val pMet = pStatus.measureText(statusText)
        cv.drawText(statusText, M + (200f - pMet) / 2f, y + 21f, pStatus)
        cv.drawText("Estado general", (M + 210).toFloat(), y + 21f, textPaint(12f, COL_DARK))

        drawFooter(cv, 2)
        doc.finishPage(page)
    }

    // ── Page 3 — Charts ───────────────────────────────────────────────────────

    private fun page3Charts(doc: PdfDocument, data: PdfReportData) {
        val (page, cv) = newPage(doc, 3)
        drawPageHeader(cv, "Gráficas de Rendimiento", 3)

        var y = 85f

        // Bar chart section
        val pSec = textPaint(12f, COL_DARK, bold = true)
        cv.drawText("Puntuación semanal por dominio", M.toFloat(), y, pSec)
        y += 12f
        y = drawBarChart(cv, data, y)
        y += 24f

        // Line chart section
        cv.drawText("Evolución global — últimas 8 semanas", M.toFloat(), y, pSec)
        y += 12f
        drawLineChart(cv, data, y)

        drawFooter(cv, 3)
        doc.finishPage(page)
    }

    private fun drawBarChart(cv: Canvas, data: PdfReportData, startY: Float): Float {
        val chartH   = 180f
        val chartW   = CW.toFloat()
        val maxScore = 100f
        val barW     = chartW / (DOMAIN_ORDER.size * 1.8f)
        val barSpacing = chartW / DOMAIN_ORDER.size
        val baselineY  = startY + chartH - (data.baselineScore / maxScore) * chartH

        val pFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 0.5f; color = COL_DIVIDER
        }
        val pText   = textPaint(9f, COL_DARK)
        pText.textAlign = Paint.Align.CENTER
        val pVal    = textPaint(8f, COL_DARK, bold = true)
        pVal.textAlign = Paint.Align.CENTER

        // Y-axis grid lines at 0, 25, 50, 75, 100
        val pGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = COL_DIVIDER; strokeWidth = 0.5f
        }
        val pGridLabel = textPaint(8f, COL_GRAY)
        for (v in intArrayOf(0, 25, 50, 75, 100)) {
            val lineY = startY + chartH - (v / maxScore) * chartH
            cv.drawLine(M.toFloat(), lineY, (M + chartW), lineY, pGrid)
            cv.drawText("$v", (M - 4).toFloat(), lineY + 3f, pGridLabel)
        }

        DOMAIN_ORDER.forEachIndexed { i, domain ->
            val score   = data.weeklyDomainScores[domain] ?: 0f
            val barH    = (score / maxScore) * chartH
            val barL    = M + i * barSpacing + (barSpacing - barW) / 2f
            val barTop  = startY + chartH - barH

            pFill.color = DOMAIN_COLORS[i]
            cv.drawRoundRect(RectF(barL, barTop, barL + barW, startY + chartH), 3f, 3f, pFill)

            // Value label above bar
            pVal.color = COL_DARK
            cv.drawText("%.0f".format(score), barL + barW / 2f, barTop - 4f, pVal)
            // Domain label below
            cv.drawText(DOMAIN_ABBR[i], barL + barW / 2f, startY + chartH + 14f, pText)
        }

        // Baseline dashed line
        if (data.baselineScore > 0f) {
            val pBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = COL_GRAY; strokeWidth = 1.5f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 4f), 0f)
            }
            cv.drawLine(M.toFloat(), baselineY, (M + chartW), baselineY, pBase)
            val pBaseLabel = textPaint(8f, COL_GRAY)
            cv.drawText("Base: %.0f".format(data.baselineScore),
                (M + chartW + 4).toFloat(), baselineY + 3f, pBaseLabel)
        }

        // Domain color legend
        var lx = M.toFloat()
        val ly = startY + chartH + 26f
        DOMAIN_ORDER.forEachIndexed { i, _ ->
            pFill.color = DOMAIN_COLORS[i]
            cv.drawRect(lx, ly - 7f, lx + 10f, ly + 3f, pFill)
            val pLeg = textPaint(8f, COL_DARK)
            cv.drawText(DOMAIN_SHORT[i], lx + 13f, ly, pLeg)
            lx += pLeg.measureText(DOMAIN_SHORT[i]) + 26f
        }

        return startY + chartH + 40f
    }

    private fun drawLineChart(cv: Canvas, data: PdfReportData, startY: Float): Float {
        val chartH    = 190f
        val chartW    = CW.toFloat()
        val maxScore  = 100f
        val validData = data.weeklyTrend.mapIndexed { i, v -> i to v }.filter { (_, v) -> v > 0f }
        val n         = 8
        val stepX     = chartW / (n - 1).toFloat()

        // Grid
        val pGrid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = COL_DIVIDER; strokeWidth = 0.5f
        }
        val pGridLabel = textPaint(8f, COL_GRAY)
        for (v in intArrayOf(0, 25, 50, 75, 100)) {
            val lineY = startY + chartH - (v / maxScore) * chartH
            cv.drawLine(M.toFloat(), lineY, (M + chartW), lineY, pGrid)
            cv.drawText("$v", (M - 4).toFloat(), lineY + 3f, pGridLabel)
        }

        // X-axis labels
        val pXLabel = textPaint(9f, COL_DARK)
        pXLabel.textAlign = Paint.Align.CENTER
        for (i in 0 until n) {
            val x = M + i * stepX
            cv.drawText("S${i + 1}", x, startY + chartH + 14f, pXLabel)
        }

        if (validData.size >= 2) {
            val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = COL_PRIMARY; strokeWidth = 2f
            }
            val pDot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL; color = COL_PRIMARY
            }
            val pVal = textPaint(8f, COL_PRIMARY, bold = true)
            pVal.textAlign = Paint.Align.CENTER
            val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.parseColor("#1A1976D2")  // 10% alpha blue
            }

            val path = Path()
            val fillPath = Path()
            var first = true
            validData.forEach { (i, v) ->
                val x = M + i * stepX
                val y = startY + chartH - (v / maxScore) * chartH
                if (first) { path.moveTo(x, y); fillPath.moveTo(x, startY + chartH); fillPath.lineTo(x, y); first = false }
                else { path.lineTo(x, y); fillPath.lineTo(x, y) }
            }
            fillPath.lineTo(M + validData.last().first * stepX, startY + chartH)
            fillPath.close()
            cv.drawPath(fillPath, pFill)
            cv.drawPath(path, pLine)

            validData.forEach { (i, v) ->
                val x = M + i * stepX
                val y = startY + chartH - (v / maxScore) * chartH
                cv.drawCircle(x, y, 4f, pDot)
                cv.drawText("%.0f".format(v), x, y - 7f, pVal)
            }
        } else {
            val pNoData = textPaint(11f, COL_GRAY)
            pNoData.textAlign = Paint.Align.CENTER
            cv.drawText("Sin datos suficientes", (M + chartW / 2f), startY + chartH / 2f, pNoData)
        }

        // Baseline
        if (data.baselineScore > 0f) {
            val baselineY = startY + chartH - (data.baselineScore / maxScore) * chartH
            val pBase = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; color = COL_GRAY; strokeWidth = 1f
                pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 4f), 0f)
            }
            cv.drawLine(M.toFloat(), baselineY, (M + chartW), baselineY, pBase)
        }

        return startY + chartH + 30f
    }

    // ── Page 4 — AI message ───────────────────────────────────────────────────

    private fun page4AiMessage(doc: PdfDocument, data: PdfReportData) {
        val (page, cv) = newPage(doc, 4)
        drawPageHeader(cv, "Análisis Inteligente", 4)

        var y = 85f

        // AI message
        val msgTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_DARK; textSize = 13f
        }
        y = drawWrapped(cv, data.aiMessage.ifBlank { "No hay análisis disponible aún." },
            M.toFloat(), y, CW, msgTp) + 24f

        // Alert-level recommendation block
        val level = data.latestPrediction?.alertLevel
        if (level == AlertLevel.WATCH || level == AlertLevel.ALERT) {
            val (boxColor, recText) = when (level) {
                AlertLevel.WATCH  -> Color.parseColor("#FFF8E1") to
                    "Te recomendamos continuar con tus sesiones regulares y compartir este reporte en tu próxima cita médica."
                AlertLevel.ALERT  -> Color.parseColor("#FFEBEE") to
                    "Recomendamos mostrar este reporte a su médico o especialista. Los cambios detectados merecen una evaluación profesional."
                else -> Color.WHITE to ""
            }
            val borderColor = if (level == AlertLevel.ALERT) COL_RED else COL_ORANGE

            val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = boxColor }
            val pBord = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = borderColor; strokeWidth = 2f }
            val boxTop = y
            val boxRect = RectF(M.toFloat(), boxTop, (W - M).toFloat(), boxTop + 70f)
            cv.drawRoundRect(boxRect, 6f, 6f, pFill)
            cv.drawRoundRect(boxRect, 6f, 6f, pBord)

            val pRecTitle = textPaint(11f, borderColor, bold = true)
            cv.drawText("Recomendación", (M + 12).toFloat(), boxTop + 17f, pRecTitle)
            val recTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = borderColor; textSize = 11f }
            drawWrapped(cv, recText, (M + 12).toFloat(), boxTop + 30f, CW - 24, recTp)
            y = boxTop + 80f
        }

        y += 16f

        // Analysis metadata
        val pMeta = textPaint(10f, COL_GRAY)
        val analysisDate = Instant.ofEpochMilli(data.generatedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale("es")))
        cv.drawText("Fecha del análisis: $analysisDate", M.toFloat(), y, pMeta)
        y += 16f
        cv.drawText("Versión del modelo ML: ${BuildConfig.ML_MODEL_VERSION}", M.toFloat(), y, pMeta)

        drawFooter(cv, 4)
        doc.finishPage(page)
    }

    // ── Page 5 — Contact info ─────────────────────────────────────────────────

    private fun page5Contact(doc: PdfDocument, data: PdfReportData) {
        val (page, cv) = newPage(doc, 5)
        drawPageHeader(cv, "Información del Sistema", 5)

        val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = COL_SURFACE }
        cv.drawRect(M.toFloat(), 80f, (W - M).toFloat(), H - 80f, pFill)

        var y = 110f
        val pTitle  = textPaint(14f, COL_PRIMARY, bold = true)
        val pBody   = textPaint(12f, COL_DARK)
        val pSmall  = textPaint(10f, COL_GRAY)

        fun section(title: String, vararg lines: String) {
            cv.drawText(title, (M + 16).toFloat(), y, pTitle)
            y += 18f
            lines.forEach { line ->
                cv.drawText(line, (M + 16).toFloat(), y, pBody)
                y += 16f
            }
            y += 10f
        }

        section("Sobre EternaMente",
            "Esta evaluación fue realizada con EternaMente,",
            "aplicación de seguimiento cognitivo para adultos mayores.")

        section("Desarrollado por",
            "Vanessa Bastidas — Universidad Cooperativa de Colombia",
            "Sede Pasto · Programa de Ingeniería de Sistemas")

        section("Contacto institucional",
            "Universidad Cooperativa de Colombia",
            "Calle 11 #23-85, Pasto, Nariño, Colombia",
            "ucc.edu.co")

        section("Aviso legal",
            "Los datos de esta evaluación son confidenciales.",
            "Se procesan y almacenan localmente en este dispositivo.",
            "No se comparte información con servidores externos.")

        y += 8f
        val pVer = textPaint(9f, COL_GRAY)
        cv.drawText("EternaMente v${BuildConfig.VERSION_NAME}  •  Análisis off-device  •  Privacidad by design",
            (M + 16).toFloat(), y, pVer)

        drawFooter(cv, 5)
        doc.finishPage(page)
    }

    // ── Drawing helpers ───────────────────────────────────────────────────────

    private fun newPage(doc: PdfDocument, num: Int): Pair<PdfDocument.Page, Canvas> {
        val info = PdfDocument.PageInfo.Builder(W, H, num).create()
        val page = doc.startPage(info)
        // White background
        val bg = Paint().apply { color = Color.WHITE; style = Paint.Style.FILL }
        page.canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), bg)
        return page to page.canvas
    }

    private fun drawPageHeader(cv: Canvas, title: String, pageNum: Int) {
        val pBand = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = COL_PRIMARY }
        cv.drawRect(0f, 0f, W.toFloat(), 60f, pBand)
        val pTitle = textPaint(16f, Color.WHITE, bold = true)
        cv.drawText(title, M.toFloat(), 39f, pTitle)
        val pPage = textPaint(10f, Color.WHITE)
        pPage.textAlign = Paint.Align.RIGHT
        cv.drawText("Página $pageNum de 5", (W - M).toFloat(), 39f, pPage)
    }

    private fun drawFooter(cv: Canvas, pageNum: Int) {
        val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = COL_DIVIDER; strokeWidth = 0.5f
        }
        cv.drawLine(M.toFloat(), (H - 32).toFloat(), (W - M).toFloat(), (H - 32).toFloat(), pLine)
        val pFooter = textPaint(8f, COL_GRAY)
        cv.drawText("EternaMente — Informe confidencial — No es un diagnóstico médico",
            M.toFloat(), (H - 16).toFloat(), pFooter)
        pFooter.textAlign = Paint.Align.RIGHT
        cv.drawText("$pageNum / 5", (W - M).toFloat(), (H - 16).toFloat(), pFooter)
    }

    private fun drawCenter(cv: Canvas, text: String, y: Float, paint: Paint) {
        val saved = paint.textAlign
        paint.textAlign = Paint.Align.CENTER
        cv.drawText(text, (W / 2).toFloat(), y, paint)
        paint.textAlign = saved
    }

    private fun drawWrapped(cv: Canvas, text: String, x: Float, y: Float, width: Int, tp: TextPaint): Float {
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, tp, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.3f)
            .setIncludePad(false)
            .build()
        cv.save()
        cv.translate(x, y)
        layout.draw(cv)
        cv.restore()
        return y + layout.height
    }

    private fun textPaint(size: Float, color: Int, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = size
            this.color    = color
            typeface      = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private fun purgeOldReports(dir: File) {
        dir.listFiles()
            ?.filter { it.name.endsWith(".pdf") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }
    }
}
