package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api

data class ThingsToDo(
  val prisonerId: String,
  val thingsToDo: List<ToDoType> = emptyList(),
)
