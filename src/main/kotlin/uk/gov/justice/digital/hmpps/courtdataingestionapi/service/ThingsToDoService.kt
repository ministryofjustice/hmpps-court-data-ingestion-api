package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ThingsToDo
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ToDoType
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
    val documents: Set<UUID> = documentSearchService.searchByPrisoner(prisonerId)
    val courtDocuments = courtDocumentRepository.findByPrisonerNumber(prisonerId)

    return ThingsToDo(
      prisonerId = prisonerId,
      thingsToDo = courtDocuments.filter {
        documentNotificationService.isUnread(it) && documents.contains(it.prisonDocumentId)
      }.map {
        ToDoType.HMCTS_API_DOCUMENT_RECEIVED
      },
    )
  }
}
