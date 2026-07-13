package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api

import java.time.LocalDateTime
import java.util.UUID

data class CourtDocument(
  val prisonDocumentId: UUID,
  val caseReferences: List<String>,
  val isUnread: Boolean,
  val documentType: CourtDocumentType,
  val courtHearing: CourtDocumentHearing?,
)

data class CourtDocumentHearing(
  val courtName: String,
  val hearingType: String,
  val hearingDate: LocalDateTime,
)
