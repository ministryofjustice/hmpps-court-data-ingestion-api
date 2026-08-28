package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class CourtHearing(
  val hearingId: UUID,
  val courtName: String,
  val courtId: UUID,
  val courtCode: String? = null,
  val hearingDate: LocalDate,
  val caseReferences: List<String>,
  val hearingType: String,
  val documents: List<CourtHearingDocument>,
)

data class CourtHearingDocument(
  val documentType: CourtDocumentType,
  val documentId: UUID,
  val ingestionAt: LocalDateTime,
)
