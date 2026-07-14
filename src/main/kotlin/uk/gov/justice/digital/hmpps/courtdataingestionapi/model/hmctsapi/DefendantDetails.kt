package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi

import java.time.LocalDate
import java.util.UUID

data class DefendantDetails(
  val defendantId: UUID,
  val masterDefendantId: UUID,
  val name: String? = null,
  val dateOfBirth: LocalDate? = null,
)
