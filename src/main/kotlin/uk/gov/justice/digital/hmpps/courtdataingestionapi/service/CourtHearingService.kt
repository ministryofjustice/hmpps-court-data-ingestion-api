package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtHearingEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.HmtcsApiDataEnrichment
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtHearingRepository
import java.time.LocalDateTime

@Service
class CourtHearingService(
  private val courtHearingRepository: CourtHearingRepository,
) {

  fun createOrUpdateCourtHearingData(
    courtDocumentEntity: CourtDocumentEntity,
    hmtcsApiDataEnrichment: HmtcsApiDataEnrichment?,
  ) {
    if (hmtcsApiDataEnrichment != null) {
      var hearing = courtHearingRepository.findByHmctsCourtHearingId(courtDocumentEntity.hmctsCourtHearingId!!)
      if (hearing != null) {
        hearing.apply {
          courtId = hmtcsApiDataEnrichment.courtId
          courtName = hmtcsApiDataEnrichment.courtName
          hearingType = hmtcsApiDataEnrichment.hearingType
          hearingDate = hmtcsApiDataEnrichment.hearingDate
          updatedAt = LocalDateTime.now()
        }
        hearing.courtDocuments.add(courtDocumentEntity)
        courtDocumentEntity.courtHearing = hearing
      } else {
        courtDocumentEntity.courtHearing = courtHearingRepository.save(
          CourtHearingEntity(
            courtId = hmtcsApiDataEnrichment.courtId,
            courtName = hmtcsApiDataEnrichment.courtName,
            hearingType = hmtcsApiDataEnrichment.hearingType,
            hearingDate = hmtcsApiDataEnrichment.hearingDate,
            hmctsCourtHearingId = courtDocumentEntity.hmctsCourtHearingId!!,
            courtDocuments = mutableListOf(courtDocumentEntity),
          ),
        )
      }
    }
  }
}
