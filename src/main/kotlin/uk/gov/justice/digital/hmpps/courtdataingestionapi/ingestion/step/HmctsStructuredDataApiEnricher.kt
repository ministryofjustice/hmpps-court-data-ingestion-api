package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourtScheduleApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourthouseApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.HmtcsApiDataEnrichment
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher

@Component
@Order(800)
class HmctsStructuredDataApiEnricher(
  private val hmctsCourtScheduleApiClient: HmctsCourtScheduleApiClient,
  private val hmctsCourthouseApiClient: HmctsCourthouseApiClient,
) : IngestionEnricher {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun enrich(context: IngestionContext): IngestionContext {
    val hearingId = context.hearingId ?: return context
    val caseReferences = context.caseReferences ?: return context

    runCatching {
      val hearings = caseReferences.flatMap { hmctsCourtScheduleApiClient.getCourtSchedule(it).courtSchedule.flatMap { schedule -> schedule.hearings } }
      val hearing = hearings.find { it.hearingId == hearingId }

      if (hearing == null || hearing.courtSittings.isEmpty()) {
        log.error("Could not find hearing with sittings from id: $hearingId")
        return context
      }

      val courtId = hearing.courtSittings[0].courtHouse
      val hearingDate = hearing.courtSittings[0].sittingStart
      val courthouse = hmctsCourthouseApiClient.getCourthouse(courtId)

      return context.copy(
        hmtcsApiDataEnrichment = HmtcsApiDataEnrichment(
          courtId = courtId,
          courtName = courthouse.courtHouseName,
          hearingType = hearing.hearingType,
          hearingDate = hearingDate,

        ),
      )
    }.onFailure {
      log.warn("Unable to get structured API data from HMCTS", it)
    }

    return context
  }
}
