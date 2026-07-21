package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.CourtRegisterApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourtScheduleApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourthouseApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtHearingEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.HmtcsApiDataEnrichment
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.courtregister.CourtRegister
import java.util.UUID

@Component
@Order(800)
class HmctsStructuredDataApiEnricher(
  private val hmctsCourtScheduleApiClient: HmctsCourtScheduleApiClient,
  private val hmctsCourthouseApiClient: HmctsCourthouseApiClient,
  private val courtRegisterApiClient: CourtRegisterApiClient,
) : IngestionEnricher {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override fun enrich(context: IngestionContext): IngestionContext {
    val hearingId = context.hearingId ?: return context
    val caseReferences = context.caseReferences ?: return context

    val data = lookupStructureData(caseReferences, hearingId)

    return if (data != null) {
      context.copy(
        hmtcsApiDataEnrichment = data,
      )
    } else {
      context
    }
  }

  fun lookupStructureData(caseReferences: List<String>, hearingId: UUID): HmtcsApiDataEnrichment? {
    runCatching {
      val hearings = caseReferences.flatMap { hmctsCourtScheduleApiClient.getCourtSchedule(it).courtSchedule.flatMap { schedule -> schedule.hearings } }
      val hearing = hearings.find { it.hearingId == hearingId }

      if (hearing == null || hearing.courtSittings.isEmpty()) {
        log.error("Could not find hearing with sittings from id: $hearingId")
        return null
      }

      val courtId = hearing.courtSittings[0].courtHouse
      val hearingDate = hearing.courtSittings[0].sittingStart

      val courtRegister = getCourtRegister(courtId)

      return HmtcsApiDataEnrichment(
        courtId = courtId,
        courtCode = courtRegister?.courtId,
        courtName = getCourtName(courtId, courtRegister),
        hearingType = hearing.hearingType,
        hearingDate = hearingDate,
      )
    }.onFailure {
      log.warn("Unable to get structured API data from HMCTS", it)
    }

    return null
  }

  fun lookupCourtRegisterData(hearing: CourtHearingEntity): HmtcsApiDataEnrichment? {
    runCatching {
      val courtRegister = getCourtRegister(hearing.courtId)

      return HmtcsApiDataEnrichment(
        courtId = hearing.courtId,
        courtName = getCourtName(hearing.courtId, courtRegister),
        courtCode = courtRegister?.courtId,
        hearingType = hearing.hearingType,
        hearingDate = hearing.hearingDate,
      )
    }.onFailure {
      log.warn("Unable to get court data from Court Register API", it)
    }

    return null
  }

  private fun getCourtRegister(hmctsCourtId: UUID): CourtRegister? {
    runCatching {
      return courtRegisterApiClient.getCourtRegisterByHmctsId(hmctsCourtId)
    }.onFailure {
      log.warn("Unable to get court register API data from HMCTS courtId", it)
    }
    return null
  }

  private fun getCourtName(hmctsCourtId: UUID, courtRegister: CourtRegister?): String {
    if (courtRegister != null) {
      return courtRegister.courtName
    }

    val courthouse = hmctsCourthouseApiClient.getCourthouse(hmctsCourtId)
    return courthouse.courtHouseName
  }
}
