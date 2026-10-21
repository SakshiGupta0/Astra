// Story export as a PDF (PRD F-06): the same text the user chose to share, laid out on A4 pages.
package app.astra.data

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

object StoryPdf {
    /** Writes the export to the app's cache (shared through FileProvider) and returns the file. */
    fun write(context: Context, text: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "my-astra-story.pdf")
        val doc = PdfDocument()
        val pageW = 595; val pageH = 842; val margin = 48
        val body = TextPaint().apply { color = Color.rgb(21, 19, 58); textSize = 11.5f; isAntiAlias = true }
        val head = TextPaint(body).apply { textSize = 18f; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD); color = Color.rgb(96, 45, 137) }
        val foot = TextPaint(body).apply { textSize = 8.5f; color = Color.rgb(87, 91, 117) }
        val lines = text.lines()
        val title = lines.firstOrNull() ?: "My details"
        val rest = lines.drop(1).joinToString("\n").trim()
        val width = pageW - 2 * margin
        val layout = StaticLayout.Builder.obtain(rest, 0, rest.length, body, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(3f, 1f).build()
        var line = 0; var pageNo = 1
        while (line < layout.lineCount || pageNo == 1) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            val c = page.canvas
            var y = margin.toFloat()
            if (pageNo == 1) {
                c.drawText(title, margin.toFloat(), y + 18f, head); y += 40f
            }
            val bottom = pageH - margin - 24
            c.save(); c.translate(margin.toFloat(), y)
            val startTop = if (line < layout.lineCount) layout.getLineTop(line) else 0
            var drawn = line
            while (drawn < layout.lineCount && layout.getLineBottom(drawn) - startTop <= bottom - y) drawn++
            if (drawn > line) {
                c.clipRect(0f, 0f, width.toFloat(), (layout.getLineTop(drawn) - startTop).toFloat())
                c.translate(0f, -startTop.toFloat())
                layout.draw(c)
            }
            c.restore()
            c.drawText("Shared from the ASTRA app · calculated with Lahiri ayanamsha, whole-sign houses · page $pageNo", margin.toFloat(), (pageH - margin).toFloat(), foot)
            doc.finishPage(page)
            if (drawn == line) break
            line = drawn; pageNo++
        }
        out.outputStream().use { doc.writeTo(it) }
        doc.close()
        return out
    }
}
