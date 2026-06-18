package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ThingsToDo
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ToDoType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchByUuidsRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.util.UUID

@Service
@Transactional(readOnly = true)
class ThingsToDoService(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val documentNotificationService: PrisonDocumentNotificationService,
  private val documentManagementApiClient: HmppsDocumentManagementApi,
) {

  fun getToDoList(prisonerId: String): ThingsToDo {
    val unreadCourtDocuments: List<CourtDocumentEntity> = courtDocumentRepository.findByPrisonerNumber(prisonerId)
      .filter { documentNotificationService.isUnread(it) }

    val unreadNonDuplicateDocuments: List<Document> = getNonDuplicateDocuments(unreadCourtDocuments)

    return ThingsToDo(
      prisonerId = prisonerId,
      thingsToDo = unreadNonDuplicateDocuments.map {
        ToDoType.HMCTS_API_DOCUMENT_RECEIVED
      },
    )
  }

  private fun getNonDuplicateDocuments(courtDocuments: List<CourtDocumentEntity>): List<Document> {
    if (courtDocuments.isEmpty()) return emptyList()

    val courtDocumentUuids: List<UUID> = courtDocuments.map { it.prisonDocumentId }
    val documents = documentManagementApiClient.searchByDocumentUuids(DocumentSearchByUuidsRequest(courtDocumentUuids))
    return documents.filter { it.duplicateOf == null && courtDocumentUuids.contains(it.documentUuid) }
  }
}
