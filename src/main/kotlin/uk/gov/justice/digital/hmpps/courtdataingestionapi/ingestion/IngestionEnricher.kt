package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

fun interface IngestionEnricher {
  fun enrich(context: IngestionContext): IngestionContext
}
