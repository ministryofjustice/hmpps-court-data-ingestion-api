package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ThingsToDo
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ToDoType
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
    val courtDocuments: List<CourtDocumentEntity> = courtDocumentRepository.findByPrisonerNumber(prisonerId)
      .filter { documentNotificationService.isUnread(it) }

    val unreadDocumentUuids: List<UUID> = courtDocuments.map { it.prisonDocumentId }
    val documents = documentSearchService.search(DocumentSearchByUuidsRequest(unreadDocumentUuids))
    val nonDuplicateDocumentUuids: List<UUID> = documents.filter { it.duplicateOf == null }
      .map { it.documentUuid }

    return ThingsToDo(
      prisonerId = prisonerId,
      thingsToDo = unreadDocumentUuids.filter {
        nonDuplicateDocumentUuids.contains(it)
      }.map {
        ToDoType.HMCTS_API_DOCUMENT_RECEIVED
      },
    )
  }
}
