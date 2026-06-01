package uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format

import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine.BlockDetection
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine.ExtractionParams
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine.Field
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine.PdfExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine.RecordExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.engine.StatusDisambiguation
import java.io.InputStream

object DocumentExtractor {

  fun extract(input: InputStream, sourceName: String, model: FormatModel): ExtractedDocument {
    val parsed = PdfExtractor.extract(input, sourceName)
    parsed.error?.let { throw IllegalStateException("PDF parse failed for $sourceName: $it") }

    val disambiguated = parsed.copy(
      lines = StatusDisambiguation.apply(parsed.lines, model.contextualLabels),
    )

    val params = ExtractionParams(
      binTolerance = model.binTolerance,
      xBinSize = model.xBinSize,
      intraLineSeparator = model.intraLineSeparator,
      crossLineSeparator = model.crossLineSeparator,
    )
    val raw = RecordExtractor.extract(disambiguated, model.labelBinByText, params)

    val blocked = BlockDetection.detectBlocks(raw, model.blockLabels)

    val header = foldToMap(blocked.headerFields, model)
    val offences = blocked.offenceBlocks.map { foldToMap(it, model) }

    return ExtractedDocument(
      headerFields = header,
      offenceBlocks = offences,
      labelSignature = (header.keys + offences.flatMap { it.keys }).distinct(),
      pageCount = parsed.pages,
      fieldCount = raw.fields.size,
    )
  }

  private fun foldToMap(fields: List<Field>, model: FormatModel): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    for (f in fields) out.putIfAbsent(f.label, model.scrub(f.value))
    return out
  }
}

/** Pure result of model-driven extraction, free of any persistence or framework type. */
data class ExtractedDocument(
  val headerFields: Map<String, String>,
  val offenceBlocks: List<Map<String, String>>,
  val labelSignature: List<String>,
  val pageCount: Int,
  val fieldCount: Int,
)
