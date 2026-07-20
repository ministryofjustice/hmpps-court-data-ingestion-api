package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.todo

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
data class ToDoScenario(
  val name: String,
  val prisoner: String,
  val newDocCutoff: LocalDateTime,
  val documents: List<ScenarioDocument> = emptyList(),
  val expected: Expected,
  val pending: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ScenarioDocument(
  val uuid: String,
  val source: String,
  val status: String,
  val duplicateOf: String? = null,
  val ingestedAt: LocalDateTime,
  val viewEvents: List<ScenarioViewEvent> = emptyList(),
  val contentHash: String? = null,
  val inDocumentStore: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ScenarioViewEvent(
  val type: String,
  val at: LocalDateTime,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Expected(
  val todoCount: Int,
  val newUuids: List<String>? = null,
)
