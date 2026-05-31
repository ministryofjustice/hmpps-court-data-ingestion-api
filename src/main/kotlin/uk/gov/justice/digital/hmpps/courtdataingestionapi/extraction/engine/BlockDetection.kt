package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine

object BlockDetection {

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

  private fun dedupeFields(fields: List<Field>): List<Field> = fields
    .groupBy { it.label to it.value }
    .map { (_, group) -> group.minBy { it.page * 100000.0 + it.y } }
    .sortedWith(compareBy({ it.page }, { it.y }))

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
