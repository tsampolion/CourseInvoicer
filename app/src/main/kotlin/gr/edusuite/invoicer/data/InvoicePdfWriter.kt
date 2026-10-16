package gr.edusuite.invoicer.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import gr.edusuite.invoicer.core.model.InvoiceDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Renders an [InvoiceDocument] (a statement, R14) to an A4 PDF with the framework PdfDocument API and
 * shares it. The default typeface covers Greek, which the statement heading and disclaimer use.
 */
class InvoicePdfWriter(private val context: Context) {
    /**
     * Writes the PDF to the app cache and returns a content:// Uri that other apps can read. The file is
     * written under a temporary name and then renamed, so an app still reading an earlier copy never sees
     * a half-written file.
     */
    suspend fun write(document: InvoiceDocument): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, DIRECTORY).apply { mkdirs() }
        val file = File(dir, "statement-${document.number.replace(Regex("[^A-Za-z0-9_-]"), "_")}.pdf")
        val partial = File.createTempFile("${file.name}.", ".part", dir)
        val pdf = PdfDocument()
        try {
            InvoiceLayout(pdf).render(document)
            partial.outputStream().use(pdf::writeTo)
            if (!partial.renameTo(file)) throw IOException("Could not save ${file.name}")
        } finally {
            pdf.close()
            partial.delete()
        }
        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private companion object {
        /** Matches the `invoices/` cache path shared through the FileProvider (res/xml/file_paths.xml). */
        const val DIRECTORY = "invoices"
    }
}

/**
 * Lays out one document. A statement (R14) has a wrapped heading across the top, a boxed disclaimer under
 * the header and the disclaimer again in every page footer. Documents issued before statements have no
 * title or disclaimer and keep their original layout, with "INVOICE" on the right.
 */
private class InvoiceLayout(private val pdf: PdfDocument) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 48f
    private val right = pageWidth - margin
    private val bottom = pageHeight - margin
    private val footerBaseline = pageHeight - margin / 2

    private val dateX = margin
    private val descriptionX = margin + 84f
    private val durationX = right - 150f
    private val descriptionWidth = durationX - descriptionX - 12f

    /** The seller block shares the top of the page with the number column on the right. */
    private val sellerWidth = right - margin - 170f
    private val fullWidth = right - margin

    /** The footer disclaimer leaves room for "Page N" on its right. */
    private val footerTextWidth = fullWidth - 64f
    private val footerLineHeight = 9f

    private val ink = Color.rgb(0x1C, 0x1B, 0x1F)
    private val muted = Color.rgb(0x60, 0x5D, 0x66)
    private val accent = Color.rgb(0x5B, 0x3F, 0xA0) // Course Invoicer accent (EduSuite style guide)

    private val title = paint(22f, bold = true, color = accent)
    private val statementTitle = paint(15f, bold = true, color = accent)
    private val heading = paint(10f, bold = true)
    private val body = paint(10f)
    private val label = paint(8f, bold = true, color = muted)
    private val small = paint(8f, color = muted)
    private val lineNote = paint(8.5f, italic = true, color = muted)
    private val disclaimerBox = paint(9f, bold = true)
    private val footer = paint(7f, color = muted)
    private val total = paint(12f, bold = true)
    private val stamp = paint(14f, bold = true, color = Color.rgb(0x3E, 0x6E, 0x47)) // suite "ok" moss
    private val rule = Paint().apply { color = Color.rgb(0xCA, 0xC4, 0xD0); strokeWidth = 0.8f }
    private val band = Paint().apply { color = Color.rgb(0xEE, 0xEC, 0xF2) }
    private val frame = Paint().apply { color = ink; strokeWidth = 1f; style = Paint.Style.STROKE }

    private var pageNumber = 0
    private lateinit var page: PdfDocument.Page
    private lateinit var canvas: Canvas
    private var y = 0f

    /** The footer disclaimer, wrapped once; empty for documents issued before statements. */
    private var footerLines: List<String> = emptyList()

    /** Where content must stop on every page, above the footer. */
    private var contentBottom = bottom

    fun render(doc: InvoiceDocument) {
        footerLines = doc.disclaimer?.let { wrap(it, footer, footerTextWidth) }.orEmpty()
        if (footerLines.isNotEmpty()) {
            contentBottom = minOf(bottom, footerBaseline - footerLines.size * footerLineHeight - 12f)
        }
        startPage()
        drawHeader(doc)
        doc.disclaimer?.let(::drawDisclaimerBox)
        drawTableHeader()
        doc.lines.forEach(::drawLine)
        drawTotal(doc)
        drawNotes(doc.notes)
        finishPage()
    }

    private fun drawHeader(doc: InvoiceDocument) {
        doc.title?.let { text ->
            wrap(text, statementTitle, fullWidth).forEach { line ->
                canvas.drawText(line, margin, y + 15f, statementTitle)
                y += 20f
            }
            y += 10f
        }
        val top = y
        doc.seller.forEachIndexed { index, line ->
            val paint = if (index == 0) heading else body
            wrap(line, paint, sellerWidth).forEach { text ->
                canvas.drawText(text, margin, y + 12f, paint)
                y += 14f
            }
        }
        var rightY = top
        if (doc.title == null) {
            drawRight("INVOICE", rightY + 20f, title)
            rightY += 34f
        }
        drawRight("No. ${doc.number}", rightY + 10f, body)
        rightY += 14f
        drawRight(doc.issueDate, rightY + 10f, body)
        rightY += 14f
        if (doc.paid) {
            drawRight("PAID", rightY + 18f, stamp)
            rightY += 22f
        }
        y = maxOf(y, rightY) + 24f

        canvas.drawText("BILL TO", margin, y, label)
        y += 14f
        doc.billTo.forEachIndexed { index, line ->
            val paint = if (index == 0) heading else body
            wrap(line, paint, fullWidth).forEach { text ->
                canvas.drawText(text, margin, y, paint)
                y += 14f
            }
        }
        y += 16f
    }

    /** The non-fiscal notice (D20), bold in a frame so it cannot be missed. */
    private fun drawDisclaimerBox(text: String) {
        val padding = 8f
        val lines = wrap(text, disclaimerBox, fullWidth - 2 * padding)
        val height = lines.size * 12f + 2 * padding
        val top = y - 6f
        canvas.drawRect(margin, top, right, top + height, frame)
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, margin + padding, top + padding + 9f + index * 12f, disclaimerBox)
        }
        y = top + height + 26f
    }

    private fun drawTableHeader() {
        canvas.drawRect(margin, y - 12f, right, y + 6f, band)
        canvas.drawText("DATE", dateX + 4f, y, label)
        canvas.drawText("DESCRIPTION", descriptionX, y, label)
        canvas.drawText("DURATION", durationX, y, label)
        drawRight("AMOUNT", y, label, inset = 4f)
        y += 22f
    }

    private fun drawLine(line: InvoiceDocument.Line) {
        val wrapped = wrap(line.description, body, descriptionWidth)
        val noteLines = line.note?.let { wrap(it, lineNote, descriptionWidth) }.orEmpty()
        val height = wrapped.size * 13f + noteLines.size * 11f + 8f
        if (y + height > contentBottom - 40f) {
            finishPage()
            startPage()
            drawTableHeader()
        }
        canvas.drawText(line.date, dateX + 4f, y, body)
        wrapped.forEachIndexed { index, text -> canvas.drawText(text, descriptionX, y + index * 13f, body) }
        val notesTop = y + wrapped.size * 13f
        noteLines.forEachIndexed { index, text ->
            canvas.drawText(text, descriptionX, notesTop + index * 11f, lineNote)
        }
        canvas.drawText(line.duration, durationX, y, body)
        drawRight(line.amount, y, body, inset = 4f)
        y += height - 8f
        canvas.drawLine(margin, y - 6f, right, y - 6f, rule)
        y += 8f
    }

    private fun drawTotal(doc: InvoiceDocument) {
        if (y + 40f > contentBottom) {
            finishPage()
            startPage()
        }
        y += 8f
        canvas.drawText("TOTAL", durationX, y, heading)
        drawRight(doc.total, y, total, inset = 4f)
        y += 32f
    }

    private fun drawNotes(notes: List<String>) {
        notes.flatMap { wrap(it, small, fullWidth) + "" }.forEach { text ->
            if (y + 12f > contentBottom) {
                finishPage()
                startPage()
            }
            canvas.drawText(text, margin, y, small)
            y += 12f
        }
    }

    private fun startPage() {
        pageNumber++
        page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = margin
    }

    /** "Page N" on the right and, on statements, the disclaimer beside it on every page. */
    private fun finishPage() {
        drawRight("Page $pageNumber", footerBaseline, small)
        val firstBaseline = footerBaseline - (footerLines.size - 1) * footerLineHeight
        footerLines.forEachIndexed { index, text ->
            canvas.drawText(text, margin, firstBaseline + index * footerLineHeight, footer)
        }
        pdf.finishPage(page)
    }

    private fun drawRight(text: String, baseline: Float, paint: Paint, inset: Float = 0f) {
        canvas.drawText(text, right - inset - paint.measureText(text), baseline, paint)
    }

    /** Greedy word wrap; words longer than a line are split. */
    private fun wrap(text: String, paint: Paint, width: Float): List<String> {
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var current = ""
            paragraph.split(' ').filter { it.isNotEmpty() }.forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= width) {
                    current = candidate
                } else {
                    if (current.isNotEmpty()) lines += current
                    var rest = word
                    while (paint.measureText(rest) > width) {
                        val fit = paint.breakText(rest, true, width, null).coerceAtLeast(1)
                        lines += rest.take(fit)
                        rest = rest.drop(fit)
                    }
                    current = rest
                }
            }
            lines += current
        }
        return lines.ifEmpty { listOf("") }
    }

    private fun paint(size: Float, bold: Boolean = false, italic: Boolean = false, color: Int = ink) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = when {
                bold -> Typeface.DEFAULT_BOLD
                italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                else -> Typeface.DEFAULT
            }
            this.color = color
        }
}
