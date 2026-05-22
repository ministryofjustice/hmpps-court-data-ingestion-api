package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModel
import java.io.InputStream

interface ExtractionPipeline {
  fun extract(input: InputStream, sourceName: String, model: FormatModel): ExtractionOutput
}

data class ExtractionOutput(
  val headerFields: Map<String, String>,
  val offenceBlocks: List<Map<String, String>>,
  val labelSignature: List<String>,
  val pageCount: Int,
  val fieldCount: Int,
)
