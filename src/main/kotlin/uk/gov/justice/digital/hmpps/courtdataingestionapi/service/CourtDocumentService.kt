package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentViewEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentView
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.optionals.getOrElse

@Service
@Transactional(readOnly = true)
class CourtDocumentService(
  private val courtDocumentRepository: CourtDocumentRepository,
) {

  @Transactional
  fun recordDocumentView(prisonDocumentId: UUID, courtDocumentView: CourtDocumentView) {
    val courtDocument = courtDocumentRepository.findFirstByPrisonDocumentId(prisonDocumentId).getOrElse { throw EntityNotFoundException("Court document not found $prisonDocumentId") }

    courtDocument.courtDocumentViews.add(
      CourtDocumentViewEntity(
        username = courtDocumentView.username,
        courtDocument = courtDocument,
        viewedAt = LocalDateTime.now(),
      ),
    )
  }

  fun getCourtDocumentsByPersonIdAndPrisonDocumentIds(
    personId: String,
    prisonDocumentIds: List<UUID>,
  ): List<CourtDocument> = courtDocumentRepository.findByPrisonerNumberAndPrisonDocumentIdIn(personId, prisonDocumentIds).map { document ->
    CourtDocument(
      prisonDocumentId = document.prisonDocumentId,
      caseReferences = document.courtDocumentCases.map { it.caseReference },
      isUnread = document.courtDocumentViews.isEmpty(),
    )
  }
}
