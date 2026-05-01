package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ThingsToDo
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.ToDoType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository

@Service
@Transactional(readOnly = true)
class ThingsToDoService(
  private val courtDocumentRepository: CourtDocumentRepository,
) {

  fun getToDoList(prisonerId: String): ThingsToDo {
    val courtDocuments = courtDocumentRepository.findByPrisonerNumber(prisonerId)
    return ThingsToDo(
      prisonerId = prisonerId,
      thingsToDo = courtDocuments.filter {
        it.courtDocumentViews.isEmpty()
      }.map {
        ToDoType.HMCTS_API_DOCUMENT_RECEIVED
      },
    )
  }
}
