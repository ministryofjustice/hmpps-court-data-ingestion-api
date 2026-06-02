package uk.gov.justice.digital.hmpps.courtdataingestionapi.corpus

import com.fasterxml.jackson.annotation.JsonInclude
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CorpusSample(
  val filter: CorpusFilter,
  val binSize: Int,
  val requestedSize: Int,
  val returnedCount: Int,
  val seed: Double? = null,
  val documents: List<CorpusDocument>,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CorpusFilter(
  val eventType: String? = null,
  val courtDocumentType: String? = null,
)

enum class CorpusStatus { OK, NON_PDF, DOWNLOAD_ERROR, PARSE_ERROR }

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CorpusDocument(
  val courtDocumentId: UUID,
  val prisonDocumentId: UUID,
  val eventType: String,
  val courtDocumentType: String?,
  val documentGeneratedTimestamp: String?,
  val ingestionAt: String?,
  val status: CorpusStatus,
  val error: String? = null,
  val pages: Int = 0,
  val lineCount: Int = 0,
  /** Distinct xBins observed across the document, sorted. A quick read of the column structure. */
  val observedXBins: List<Int> = emptyList(),
  val lines: List<CorpusLine> = emptyList(),
)

data class CorpusLine(
  val page: Int,
  val y: Float,
  val cells: List<CorpusCell>,
)

data class CorpusCell(
  val text: String,
  val x: Float,
  val xEnd: Float,
  /** (x / binSize), matching FormatModel.xBin, so candidate labels land at the bins the model keys on. */
  val xBin: Int,

  /** PostScript font name. Bold is encoded here, which is the label signal for warrant-style layouts. */
  val font: String?,
  val fontSize: Float,
)
