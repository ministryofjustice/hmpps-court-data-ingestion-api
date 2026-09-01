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
  val charges: List<CourtCharge>,
  val nextHearing: NextCourtHearing?,
)

data class CourtHearingDocument(
  val documentType: CourtDocumentType,
  val documentId: UUID,
  val ingestionAt: LocalDateTime,
)

data class CourtCharge(
  val listingNumber: Int,
  val offenceLegislation: String,
  val pleaDate: LocalDate,
  val pleaValue: String,
  val startDate: LocalDate,
  val title: String,
  val wording: String,
  val results: List<CourtResult>,
)

data class CourtResult(
  val code: String,
  val description: String,

)

data class NextCourtHearing(
  val courtName: String,
  val hmctsCourtId: UUID,
  val hmppsCourtId: String? = null,
  val hearingDate: LocalDateTime,
)
