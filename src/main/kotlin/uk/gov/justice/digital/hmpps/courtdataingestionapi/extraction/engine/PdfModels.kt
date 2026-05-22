package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine

data class Chunk(
  val text: String,
  val x: Float,
  val y: Float,
  val width: Float,
  val height: Float,
  val page: Int,
  val font: String?,
  val fontSize: Float,
)

data class Cell(
  val text: String,
  val x: Float,
  val xEnd: Float,
  val font: String?,
  val fontSize: Float,
) {
  val xMid: Float get() = (x + xEnd) / 2f
}

data class Line(
  val page: Int,
  val y: Float,
  val cells: List<Cell>,
)

data class ExtractionResult(
  val fileName: String,
  val pages: Int,
  val lines: List<Line>,
  val durationMs: Long,
  val error: String?,
)

data class LabelCandidate(
  val text: String,
  val xBin: Int,
  val docFreq: Double,
  val occurrences: Int,
  val avgPerDoc: Double,
  val labelLikeCount: Int = 0,
  val valueLikeCount: Int = 0,
) {
  val valueLikeRatio: Double
    get() = if (labelLikeCount + valueLikeCount == 0) {
      0.0
    } else {
      valueLikeCount.toDouble() / (labelLikeCount + valueLikeCount)
    }
}

data class Field(
  val label: String,
  val value: String,
  val page: Int,
  val y: Float,
)

data class DocumentRecord(
  val fileName: String,
  val fields: List<Field>,
  val labelSignature: Set<String>,
)

data class BlockedRecord(
  val fileName: String,
  val headerFields: List<Field>,
  val offenceBlocks: List<List<Field>>,
)

data class IngestionMetadata(
  val source: String,
  val fileName: String,
  val extractedAt: String,
  val pipelineVersion: String,
  val templateClusterId: Int,
  val templateLabelCount: Int,
  val templateFingerprint: String,
  val numOffences: Int,
)
