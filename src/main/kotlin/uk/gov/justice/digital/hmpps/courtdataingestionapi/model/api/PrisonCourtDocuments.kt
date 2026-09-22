package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class PrisonCourtDocumentDayCount(
  val date: LocalDate,
  val documents: Int,
  val people: Int,
)

data class PrisonCourtDocument(
  val prisonDocumentId: UUID,
  val prisonerNumber: String,
  val documentType: CourtDocumentType,
  val caseReferences: List<String>,
  val addressedPrison: String?,
  val receivedAt: LocalDateTime,
)

data class PrisonCourtHearing(
  val courtHearingId: UUID,
  val prisonerNumber: String,
  val hearingDate: LocalDate,
  val hearingType: String,
  val courtName: String,
  val caseReferences: List<String>,
  val receivedAt: LocalDateTime,
  val documents: List<PrisonCourtDocument>,
)

data class PrisonCourtDocumentWeek(
  val prisonCode: String,
  val from: LocalDate,
  val to: LocalDate,
  val rollSize: Int,
  val days: List<PrisonCourtDocumentDayCount>,
  val totalDocuments: Int,
  val documents: List<PrisonCourtDocument>?,
  val previousWeek: LocalDate,
  val nextWeek: LocalDate?,
)

data class PrisonCourtDocumentDay(
  val prisonCode: String,
  val date: LocalDate,
  val rollSize: Int,
  val hearings: List<PrisonCourtHearing>,
  val documentsWithoutAHearing: List<PrisonCourtDocument>,
  val prisonerNumbers: List<String>,
)
