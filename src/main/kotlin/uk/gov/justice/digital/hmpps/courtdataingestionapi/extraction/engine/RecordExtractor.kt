package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine

import java.util.TreeSet
import kotlin.math.abs

object RecordExtractor {

  private class OpenField(
    val label: String,
    val page: Int,
    val y: Float,
    var lastY: Float,
    val valueBuilder: StringBuilder = StringBuilder(),
  ) {
    fun toField(): Field = Field(label, valueBuilder.toString().trim(), page, y)
  }

  fun extract(
    doc: ExtractionResult,
    labelMap: Map<String, Int>,
    params: ExtractionParams = ExtractionParams(),
  ): DocumentRecord {
    val canonicalBins = TreeSet<Int>().apply { addAll(labelMap.values) }
    val fields = mutableListOf<Field>()
    val openFields = mutableMapOf<Int, OpenField>()

    for (line in doc.lines) {
      for (cell in line.cells) {
        val labelText = matchLabel(cell, labelMap, params)

        if (labelText != null) {
          val canonicalBin = labelMap.getValue(labelText)
          openFields.remove(canonicalBin)?.let { fields += it.toField() }
          openFields[canonicalBin] = OpenField(
            label = labelText,
            page = line.page,
            y = line.y,
            lastY = line.y,
          )
        } else {
          val cellBin = (cell.x / params.xBinSize).toInt()
          val region = canonicalBins.floor(cellBin) ?: continue
          val existing = openFields[region] ?: continue
          val sep =
            if (existing.lastY == line.y) {
              params.intraLineSeparator
            } else {
              params.crossLineSeparator
            }
          if (existing.valueBuilder.isNotEmpty()) existing.valueBuilder.append(sep)
          existing.valueBuilder.append(cell.text)
          existing.lastY = line.y
        }
      }
    }

    openFields.values.forEach { fields += it.toField() }
    fields.sortWith(compareBy({ it.page }, { it.y }))

    return DocumentRecord(
      fileName = doc.fileName,
      fields = fields,
      labelSignature = fields.map { it.label }.toSet(),
    )
  }

  fun dedupeByLabelValue(record: DocumentRecord): DocumentRecord {
    val deduped = record.fields
      .groupBy { it.label to it.value }
      .map { (_, group) -> group.minBy { it.page * 100000.0 + it.y } }
      .sortedWith(compareBy({ it.page }, { it.y }))
    return record.copy(
      fields = deduped,
      labelSignature = deduped.map { it.label }.toSet(),
    )
  }

  private fun matchLabel(cell: Cell, labelMap: Map<String, Int>, params: ExtractionParams): String? {
    val norm = cell.text.lowercase().trim().trimEnd(':', ' ', '\t', ',')
      .replace(Regex("\\s+"), " ")
    val canonicalBin = labelMap[norm] ?: return null
    val bin = (cell.x / params.xBinSize).toInt()
    return if (abs(bin - canonicalBin) <= params.binTolerance) norm else null
  }
}

data class ExtractionParams(
  val binTolerance: Int = 1,
  val xBinSize: Int = 10,
  val intraLineSeparator: String = " ",
  val crossLineSeparator: String = "; ",
)
