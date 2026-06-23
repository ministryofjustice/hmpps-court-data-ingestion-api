package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtHearingEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.HmtcsApiDataEnrichment
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearingDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtHearingRepository
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional
class CourtHearingService(
  private val courtHearingRepository: CourtHearingRepository,
) {

  fun createOrUpdateCourtHearingData(
    courtDocumentEntity: CourtDocumentEntity,
    hmtcsApiDataEnrichment: HmtcsApiDataEnrichment?,
  ) {
    if (hmtcsApiDataEnrichment != null) {
      var hearing = courtHearingRepository.findFirstByHmctsCourtHearingId(courtDocumentEntity.hmctsCourtHearingId!!)
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

  @Transactional(readOnly = true)
  fun getCourtHearing(courtHearingId: UUID): CourtHearing {
    val courtHearing = courtHearingRepository.findFirstByHmctsCourtHearingId(courtHearingId) ?: throw EntityNotFoundException("Hearing not found $courtHearingId")

    return CourtHearing(
      hearingId = courtHearing.hmctsCourtHearingId,
      courtName = courtHearing.courtName,
      courtId = courtHearing.courtId,
      hearingDate = courtHearing.hearingDate,
      caseReferences = courtHearing.courtDocuments.flatMap { it.courtDocumentCases.map { case -> case.caseReference } }.distinct(),
      hearingType = courtHearing.hearingType,
      documents = courtHearing.courtDocuments.map {
        CourtHearingDocument(
          it.courtDocumentType,
          it.prisonDocumentId,
          it.ingestionAt,
          null, // TOOD when to load filename?
        )
      },
    )
  }
}
