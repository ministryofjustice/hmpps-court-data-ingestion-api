package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.util.UUID

@Service
@Transactional(readOnly = true)
class UnmatchedCourtDocumentService(private val courtDocumentRepository: CourtDocumentRepository) {

  fun getUnmatchedCourtDocumentsByPrisonDocumentIds(prisonDocumentIds: List<UUID>): List<CourtDocument> = courtDocumentRepository.findByPrisonerNumberIsNullAndPrisonDocumentIdIn(prisonDocumentIds).map { document ->
    CourtDocument(
      prisonDocumentId = document.prisonDocumentId,
      caseReferences = document.courtDocumentCases.map { it.caseReference },
      isUnread = false,
      documentType = document.courtDocumentType,
      courtHearing = document.courtHearing?.let {
        CourtDocumentHearing(
          it.courtName,
          it.hearingType,
          it.hearingDate,
        )
      },
    )
  }
}
