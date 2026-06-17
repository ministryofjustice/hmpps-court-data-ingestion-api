package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ThingsToDo
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ToDoType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchByUuidsRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.document.DocumentSearchService
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ThingsToDoService(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val documentSearchService: DocumentSearchService,
  private val documentNotificationService: PrisonDocumentNotificationService,
) {

  fun getToDoList(prisonerId: String): ThingsToDo {
    val unreadCourtDocuments: List<CourtDocumentEntity> = courtDocumentRepository.findByPrisonerNumber(prisonerId)
      .filter { documentNotificationService.isUnread(it) }

    val unreadDocumentUuids: List<UUID> = unreadCourtDocuments.map { it.prisonDocumentId }
    val unreadDocuments = documentSearchService.search(DocumentSearchByUuidsRequest(unreadDocumentUuids))
    val unreadNonDuplicateDocuments: List<Document> = unreadDocuments.filter { it.duplicateOf == null && unreadDocumentUuids.contains(it.documentUuid) }

    return ThingsToDo(
      prisonerId = prisonerId,
      thingsToDo = unreadNonDuplicateDocuments.map {
        ToDoType.HMCTS_API_DOCUMENT_RECEIVED
      },
    )
  }
}
