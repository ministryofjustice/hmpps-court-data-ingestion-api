package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine

import kotlin.math.abs

object PdfLayout {

  private const val Y_TOLERANCE_FRACTION = 0.6f // line grouping: fraction of glyph height
  private const val CELL_GAP_CHAR_WIDTHS = 3.0f // cell split: gap > N char widths starts a new cell
  private const val LABEL_WRAP_X_TOLERANCE = 15f // ± points for label-continuation x-alignment
  private const val LABEL_WRAP_Y_FACTOR = 2.5f // max y-gap as a multiple of font size
  private const val LABEL_WRAP_MAX_CONT_LEN = 30 // continuation text max length (labels are short)
  private const val LABEL_WRAP_MIN_CONT_LEN = 2 // single char not a continuation

  fun toLines(chunks: List<Chunk>): List<Line> {
    if (chunks.isEmpty()) return emptyList()

    val initial = mutableListOf<Line>()

    for ((page, pageChunks) in chunks.groupBy { it.page }.toSortedMap()) {
      val byY = pageChunks.sortedWith(compareBy({ it.y }, { it.x }))
      val lineGroups = groupByY(byY)
      for (group in lineGroups) {
        val xSorted = group.sortedBy { it.x }
        val cells = splitIntoCells(xSorted)
        if (cells.isNotEmpty()) {
          initial += Line(page = page, y = xSorted.first().y, cells = cells)
        }
      }
    }
    return mergeWrappedLabels(initial)
  }

  private fun groupByY(sorted: List<Chunk>): List<List<Chunk>> {
    val groups = mutableListOf<MutableList<Chunk>>()
    var current = mutableListOf<Chunk>()
    var currentY: Float? = null

    for (chunk in sorted) {
      val tol = chunk.height * Y_TOLERANCE_FRACTION
      val cy = currentY
      if (cy == null || abs(chunk.y - cy) <= tol) {
        current += chunk
        currentY = if (cy == null) chunk.y else (cy * (current.size - 1) + chunk.y) / current.size
      } else {
        if (current.isNotEmpty()) groups += current
        current = mutableListOf(chunk)
        currentY = chunk.y
      }
    }
    if (current.isNotEmpty()) groups += current
    return groups
  }

  private fun splitIntoCells(chunks: List<Chunk>): List<Cell> {
    if (chunks.isEmpty()) return emptyList()

    val cells = mutableListOf<Cell>()
    var buf = StringBuilder(chunks.first().text)
    var startX = chunks.first().x
    var endX = chunks.first().x + chunks.first().width
    var font = chunks.first().font
    var fontSize = chunks.first().fontSize

    for (i in 1 until chunks.size) {
      val prev = chunks[i - 1]
      val cur = chunks[i]
      val prevEnd = prev.x + prev.width
      val gap = cur.x - prevEnd
      val charWidth = prev.fontSize * 0.3f
      val isNewCell = gap > charWidth * CELL_GAP_CHAR_WIDTHS

      if (isNewCell) {
        cells += Cell(buf.toString().trim(), startX, endX, font, fontSize)
        buf = StringBuilder(cur.text)
        startX = cur.x
        endX = cur.x + cur.width
        font = cur.font
        fontSize = cur.fontSize
      } else {
        buf.append(' ').append(cur.text)
        endX = cur.x + cur.width
      }
    }
    cells += Cell(buf.toString().trim(), startX, endX, font, fontSize)
    return cells.filter { it.text.isNotBlank() }
  }

  private fun mergeWrappedLabels(lines: List<Line>): List<Line> {
    if (lines.size < 2) return lines

    val result = mutableListOf<Line>()
    for (origLine in lines) {
      var line = origLine
      val prev = result.lastOrNull()
      if (prev != null) {
        val patternA = tryMergeContinuations(prev, line)
        if (patternA != null) {
          val (newPrev, newCurr) = patternA
          result[result.size - 1] = newPrev
          line = newCurr
        } else if (shouldMergePatternB(prev, line)) {
          result.removeAt(result.size - 1)
          line = mergePatternB(prev, line)
        }
      }
      if (line.cells.isNotEmpty()) result += line
    }
    return result
  }

  private fun tryMergeContinuations(prev: Line, curr: Line): Pair<Line, Line>? {
    if (prev.page != curr.page) return null
    if (prev.cells.isEmpty() || curr.cells.isEmpty()) return null

    val yGap = curr.y - prev.y
    if (yGap <= 0) return null

    val prevCells = prev.cells.toMutableList()
    val mergedFrom = mutableSetOf<Int>()

    for ((idx, currCell) in curr.cells.withIndex()) {
      if (!looksLikeContinuation(currCell.text)) continue
      if (yGap > currCell.fontSize * LABEL_WRAP_Y_FACTOR) continue

      val alignedIdx = prevCells.indexOfFirst {
        abs(it.x - currCell.x) <= LABEL_WRAP_X_TOLERANCE
      }
      if (alignedIdx == -1) continue

      val target = prevCells[alignedIdx]
      prevCells[alignedIdx] = target.copy(
        text = "${target.text} ${currCell.text}".trim(),
        xEnd = maxOf(target.xEnd, currCell.xEnd),
      )
      mergedFrom += idx
    }

    if (mergedFrom.isEmpty()) return null

    val newPrev = prev.copy(cells = prevCells)
    val newCurr = curr.copy(cells = curr.cells.filterIndexed { i, _ -> i !in mergedFrom })
    return newPrev to newCurr
  }

  private fun shouldMergePatternB(prev: Line, curr: Line): Boolean {
    if (prev.page != curr.page) return false
    if (prev.cells.isEmpty()) return false
    if (curr.cells.size < 2) return false

    val prevFirst = prev.cells[0]
    val currFirst = curr.cells[0]

    if (abs(prevFirst.x - currFirst.x) > LABEL_WRAP_X_TOLERANCE) return false

    val yGap = curr.y - prev.y
    if (yGap <= 0 || yGap > prevFirst.fontSize * LABEL_WRAP_Y_FACTOR) return false

    return looksLikeContinuation(currFirst.text)
  }

  private fun looksLikeContinuation(text: String): Boolean {
    if (text.length < LABEL_WRAP_MIN_CONT_LEN) return false
    if (text.length > LABEL_WRAP_MAX_CONT_LEN) return false
    if (text.any { it.isDigit() }) return false
    if (text.contains(' ')) return false // continuations are single words
    val first = text.firstOrNull() ?: return false
    if (!first.isLowerCase()) return false // standalone labels start uppercase
    return true
  }

  private fun mergePatternB(prev: Line, curr: Line): Line {
    val prevCell = prev.cells[0]
    val first = curr.cells[0]
    val merged = first.copy(
      text = "${prevCell.text} ${first.text}".trim(),
      x = minOf(prevCell.x, first.x),
      xEnd = maxOf(prevCell.xEnd, first.xEnd),
    )
    return curr.copy(cells = listOf(merged) + curr.cells.drop(1))
  }
}
