package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
import java.io.InputStream
import java.nio.file.Path

object PdfExtractor {

  fun extract(pdfPath: Path): ExtractionResult {
    val start = System.nanoTime()
    return try {
      Loader.loadPDF(pdfPath.toFile()).use { doc -> buildResult(doc, pdfPath.fileName.toString(), start) }
    } catch (e: Exception) {
      errorResult(pdfPath.fileName.toString(), start, e)
    }
  }

  fun extract(input: InputStream, fileName: String): ExtractionResult {
    val start = System.nanoTime()
    return try {
      Loader.loadPDF(input.readBytes()).use { doc -> buildResult(doc, fileName, start) }
    } catch (e: Exception) {
      errorResult(fileName, start, e)
    }
  }

  private fun buildResult(doc: PDDocument, fileName: String, startNanos: Long): ExtractionResult {
    val collector = ChunkCollector().apply { sortByPosition = true }
    collector.getText(doc)
    val lines = PdfLayout.toLines(collector.chunks)
    return ExtractionResult(
      fileName = fileName,
      pages = doc.numberOfPages,
      lines = lines,
      durationMs = (System.nanoTime() - startNanos) / 1_000_000,
      error = null,
    )
  }

  private fun errorResult(fileName: String, startNanos: Long, e: Exception): ExtractionResult = ExtractionResult(
    fileName = fileName,
    pages = 0,
    lines = emptyList(),
    durationMs = (System.nanoTime() - startNanos) / 1_000_000,
    error = "${e::class.simpleName}: ${e.message ?: ""}",
  )
}

private class ChunkCollector : PDFTextStripper() {

  val chunks = mutableListOf<Chunk>()

  override fun writeString(text: String, textPositions: List<TextPosition>) {
    if (textPositions.isEmpty()) return

    val words = groupByGaps(textPositions)
    for (word in words) {
      val first = word.first()
      val last = word.last()
      val wordText = word.joinToString("") { it.unicode ?: "" }.trim()
      if (wordText.isEmpty()) continue

      chunks += Chunk(
        text = wordText,
        x = first.xDirAdj,
        y = first.yDirAdj,
        width = (last.xDirAdj + last.widthDirAdj) - first.xDirAdj,
        height = first.heightDir.takeIf { it > 0f } ?: first.fontSizeInPt,
        page = currentPageNo,
        font = first.font?.name,
        fontSize = first.fontSizeInPt,
      )
    }
  }

  private fun groupByGaps(positions: List<TextPosition>): List<List<TextPosition>> {
    val groups = mutableListOf<MutableList<TextPosition>>()
    var current = mutableListOf<TextPosition>()

    for (pos in positions) {
      if (current.isEmpty()) {
        current += pos
        continue
      }
      val prev = current.last()
      val prevEnd = prev.xDirAdj + prev.widthDirAdj
      val gap = pos.xDirAdj - prevEnd
      val charWidth = ((prev.widthDirAdj.takeIf { it > 0f } ?: (prev.fontSizeInPt * 0.3f)))

      if (gap > charWidth * 0.5f) {
        groups += current
        current = mutableListOf(pos)
      } else {
        current += pos
      }
    }
    if (current.isNotEmpty()) groups += current
    return groups
  }
}
