package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.todo

import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentViewEventType
import java.time.LocalDateTime

object ToDoScenarioEngine {

  // TODO: false = current production behaviour (status-blind). true = the proposed fix.
  var statusAware: Boolean = false

  // TODO: Current CDIA's countable state is LIVE. After the status alignment this becomes "ACTIVE".
  var activeStatuses: Set<String> = setOf("LIVE")

  data class Result(val todoCount: Int, val newUuids: List<String>)

  fun run(scenario: ToDoScenario): Result {
    val newUuids = scenario.documents
      .filter { it.inDocumentStore }
      .filter { isUnread(it, scenario.newDocCutoff) }
      .filter { it.duplicateOf == null }
      .filter { !statusAware || it.status in activeStatuses } // this will be changed after the status stuff
      .map { it.uuid }

    return Result(newUuids.size, newUuids)
  }

  private fun isUnread(document: ScenarioDocument, cutoff: LocalDateTime): Boolean = when (latestEventType(document)) {
    CourtDocumentViewEventType.VIEWED -> false
    CourtDocumentViewEventType.MARKED_NEW -> true
    null -> document.ingestedAt.isAfter(cutoff)
  }

  private fun latestEventType(document: ScenarioDocument): CourtDocumentViewEventType? = document.viewEvents
    .maxByOrNull { it.at }
    ?.let { CourtDocumentViewEventType.valueOf(it.type) }
}
