package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion

import org.springframework.stereotype.Component

@Component
class IngestionEnrichmentFlow(
  private val enrichers: List<IngestionEnricher>,
) {
  fun run(context: IngestionContext): IngestionContext = enrichers.fold(context) { acc, enricher -> enricher.enrich(acc) }

  fun runForBackfill(context: IngestionContext): IngestionContext = enrichers
    .fold(context) { acc, enricher -> enricher.enrich(acc) }
}
