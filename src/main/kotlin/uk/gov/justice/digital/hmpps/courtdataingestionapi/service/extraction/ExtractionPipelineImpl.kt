package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.DocumentExtractor
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModel
import java.io.InputStream

@Component
class ExtractionPipelineImpl : ExtractionPipeline {
  override fun extract(input: InputStream, sourceName: String, model: FormatModel): ExtractionOutput {
    val doc = DocumentExtractor.extract(input, sourceName, model)
    return ExtractionOutput(
      headerFields = doc.headerFields,
      offenceBlocks = doc.offenceBlocks,
      labelSignature = doc.labelSignature,
      pageCount = doc.pageCount,
      fieldCount = doc.fieldCount,
    )
  }

  override fun extractFromText(
    text: String,
    documentId: String,
    model: FormatModel,
  ): ExtractionOutput {
    val stream = text.byteInputStream(Charsets.UTF_8)

    return extract(
      input = stream,
      sourceName = documentId,
      model = model,
    )
  }
}
