package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentViewEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentView
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.optionals.getOrElse

@Service
class CourtDocumentService(
  private val courtDocumentRepository: CourtDocumentRepository,
) {

  @Transactional
  fun recordDocumentView(courtDocumentId: UUID, courtDocumentView: CourtDocumentView) {
    val courtDocument = courtDocumentRepository.findById(courtDocumentId).getOrElse { throw EntityNotFoundException("Court document not found $courtDocumentId") }

    courtDocument.courtDocumentViews.add(
      CourtDocumentViewEntity(
        username = courtDocumentView.username,
        courtDocument = courtDocument,
        viewedAt = LocalDateTime.now(),
      ),
    )
  }
}
