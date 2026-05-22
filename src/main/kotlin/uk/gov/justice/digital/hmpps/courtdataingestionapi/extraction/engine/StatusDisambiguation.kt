package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine

object StatusDisambiguation {

  val CONTEXTUAL: Set<String> = setOf("status")

  private const val X_BIN_WIDTH: Float = 15f

  fun apply(lines: List<Line>, contextual: Set<String> = CONTEXTUAL): List<Line> {
    if (lines.isEmpty()) return lines

    val lastInColumn = mutableMapOf<Int, String>()

    return lines.map { line ->
      val newCells = line.cells.map { cell ->
        val normalised = normalise(cell.text)
        val xBin = (cell.x / X_BIN_WIDTH).toInt()

        if (normalised in contextual) {
          val parent = lastInColumn[xBin]
          if (parent != null) cell.copy(text = "$parent ${cell.text}") else cell
        } else {
          lastInColumn[xBin] = cell.text
          cell
        }
      }
      line.copy(cells = newCells)
    }
  }

  private fun normalise(text: String): String = text.lowercase().trim().trimEnd(':', ',', ' ', '\t').replace(Regex("\\s+"), " ")
}
