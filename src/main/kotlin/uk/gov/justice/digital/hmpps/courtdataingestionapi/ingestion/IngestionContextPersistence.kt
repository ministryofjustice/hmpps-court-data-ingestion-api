package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity

fun CourtDocumentEntity.applyEnrichment(context: IngestionContext): CourtDocumentEntity = apply {
  context.addressedPrison?.let { addressedPrison = it }
  context.downloadedFileSha256?.let { downloadedFileSha256 = it }
  context.extractedTextSha256?.let { extractedTextSha256 = it }
  context.duplicateOf?.let { duplicateOf = it }
  context.destinationType?.let { deliverySource = it }
}
