package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtHearingEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.HmtcsApiDataEnrichment
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtHearingRepository
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CourtHearingService(
  private val courtHearingRepository: CourtHearingRepository,
) {

  @Transactional
  fun createOrUpdateCourtHearingData(
    courtDocumentEntity: CourtDocumentEntity,
    hmtcsApiDataEnrichment: HmtcsApiDataEnrichment?,
  ) {
    if (hmtcsApiDataEnrichment != null) {
      val hearing = courtHearingRepository.findFirstByHmctsCourtHearingId(courtDocumentEntity.hmctsCourtHearingId!!)
      if (hearing != null) {
        hearing.apply {
          courtId = hmtcsApiDataEnrichment.courtId
          courtName = hmtcsApiDataEnrichment.courtName
          courtCode = hmtcsApiDataEnrichment.courtCode
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
            courtCode = hmtcsApiDataEnrichment.courtCode,
            hearingType = hmtcsApiDataEnrichment.hearingType,
            hearingDate = hmtcsApiDataEnrichment.hearingDate,
            hmctsCourtHearingId = courtDocumentEntity.hmctsCourtHearingId!!,
            courtDocuments = mutableListOf(courtDocumentEntity),
          ),
        )
      }
    }
  }

  fun getCourtHearing(courtHearingId: UUID): CourtHearing {
    val courtHearing = courtHearingRepository.findFirstByHmctsCourtHearingId(courtHearingId) ?: throw EntityNotFoundException("Hearing not found $courtHearingId")
    return courtHearing.toCourtHearing()
  }

  fun getCourtHearingsByPrisoner(prisonerNumber: String): List<CourtHearing> = courtHearingRepository.findByCourtDocumentsPrisonerNumber(prisonerNumber).map { it.toCourtHearing() }
}
