package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine

/**
 * Group extracted fields into header + offence blocks.
 *
 * The signal is which labels appear many times per document. Labels with high
 * avg-per-doc are per-offence labels (offence code, plea, verdict, etc.). Labels
 * with low avg-per-doc are header labels (defendant name, date of birth, etc.).
 *
 * The boundary between them isn't always exactly at any one threshold. For the
 * court register corpus, header labels sit at avg ≈ 2.3 and per-offence labels
 * sit at avg ≈ 5.5+. A threshold of 5.0 separates them cleanly. Adjust if the
 * corpus changes shape.
 *
 * Algorithm:
 *   1. Walk fields in (page, y) order.
 *   2. When a block label is seen:
 *      - if no current block, or this label already exists in the current block,
 *        finish the current block and start a new one with this field;
 *      - otherwise, append to the current block.
 *   3. Header labels always go into the header bucket (which we later dedupe by
 *      (label, value) to collapse section-header-style repeats).
 *   4. After walking, dedupe blocks by full (label, value) signature so that the
 *      same offence reprinted at a section break collapses to one block.
 */
object BlockDetection {

  /** Default threshold: labels with avgPerDoc at or above this are treated as
   *  per-offence (block) labels. Header labels sit below. */
  const val BLOCK_LABEL_AVG_THRESHOLD: Double = 5.0

  fun identifyBlockLabels(
    labels: List<LabelCandidate>,
    threshold: Double = BLOCK_LABEL_AVG_THRESHOLD,
  ): Set<String> = labels.filter { it.avgPerDoc >= threshold }.map { it.text }.toSet()

  fun detectBlocks(record: DocumentRecord, blockLabels: Set<String>): BlockedRecord {
    val sorted = record.fields.sortedWith(compareBy({ it.page }, { it.y }))

    val headerFields = mutableListOf<Field>()
    val blocks = mutableListOf<MutableList<Field>>()
    var currentBlock: MutableList<Field>? = null

    for (field in sorted) {
      if (field.label in blockLabels) {
        val seenInCurrent = currentBlock?.any { it.label == field.label } ?: false
        if (currentBlock == null || seenInCurrent) {
          currentBlock = mutableListOf(field)
          blocks += currentBlock
        } else {
          currentBlock += field
        }
      } else {
        // Header label. Even if a block is already open, header repeats go to
        // the header bucket; they're section-break reassertions of header info,
        // not part of any specific offence.
        headerFields += field
      }
    }

    val dedupedHeader = dedupeFields(headerFields)
    val dedupedBlocks = dedupeBlocks(blocks.map { it.toList() })

    return BlockedRecord(
      fileName = record.fileName,
      headerFields = dedupedHeader,
      offenceBlocks = dedupedBlocks,
    )
  }

  /** Collapse identical (label, value) pairs in the header to a single entry,
   *  keeping the earliest (lowest page, then lowest y). */
  private fun dedupeFields(fields: List<Field>): List<Field> = fields
    .groupBy { it.label to it.value }
    .map { (_, group) -> group.minBy { it.page * 100000.0 + it.y } }
    .sortedWith(compareBy({ it.page }, { it.y }))

  /** Collapse blocks with identical (label, value) signatures so that section-break
   *  reassertions of the same offences don't produce duplicate offence records. */
  private fun dedupeBlocks(blocks: List<List<Field>>): List<List<Field>> {
    val seen = mutableSetOf<Set<Pair<String, String>>>()
    val result = mutableListOf<List<Field>>()
    for (block in blocks) {
      val signature = block.map { it.label to it.value }.toSet()
      if (signature !in seen) {
        seen += signature
        result += block
      }
    }
    return result
  }
}
