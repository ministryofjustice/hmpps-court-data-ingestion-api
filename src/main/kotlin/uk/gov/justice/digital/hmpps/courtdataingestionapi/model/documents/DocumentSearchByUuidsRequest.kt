package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents

import java.util.UUID

data class DocumentSearchByUuidsRequest(
  val documentUuids: Collection<UUID>,
)
