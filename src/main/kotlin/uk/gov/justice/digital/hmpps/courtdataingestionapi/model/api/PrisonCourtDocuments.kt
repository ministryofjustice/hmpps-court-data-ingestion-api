package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Schema(description = "Documents received on one day, for the people currently held at a prison")
data class CourtDocumentDayCount(
  val date: LocalDate,
  val documents: Int,
  val people: Int,
)

@Schema(description = "One document received for a person currently held at the prison")
data class PrisonCourtDocument(
  val courtDocumentId: UUID,
  val prisonDocumentId: UUID?,
  val prisonerNumber: String,
  val documentType: String?,
  val caseReferences: List<String>,
  @Schema(
    description = "Prison the document was delivered to.",
  )
  val addressedPrison: String?,
  @Schema(description = "Null where the hearing link has not resolved")
  val courtHearingId: UUID?,
  val hearingDate: LocalDate?,
  val hearingType: String?,
  val courtName: String?,
  val ingestedAt: LocalDateTime,
  val documentGeneratedAt: LocalDateTime?,
)

@Schema(description = "What was received, and who it was for, over a range")
sealed interface PrisonCourtDocumentResponse {
  val prisonCode: String
  val rollTakenAt: LocalDateTime
  val rollSize: Int
}

@Schema(
  description = "A week for one prison.",
)
data class PrisonCourtDocumentWeek(
  override val prisonCode: String,
  val from: LocalDate,
  val to: LocalDate,
  override val rollTakenAt: LocalDateTime,
  override val rollSize: Int,
  @Schema(description = "Monday to Sunday. Days in the future are present with a count of zero.")
  val days: List<CourtDocumentDayCount>,
  val totalDocuments: Int,
  @Schema(description = "The Monday of the week before this one, for stepping back")
  val previousWeek: LocalDate,
  @Schema(description = "The Monday of the week after this one. Null when this is the current week.")
  val nextWeek: LocalDate?,
  @Schema(description = "Null when the week is too large to list. Choose a day instead.")
  val rows: List<PrisonCourtDocument>?,
  @Schema(description = "The number of documents above which rows are not returned")
  val rowThreshold: Int,
) : PrisonCourtDocumentResponse

@Schema(
  description = "A hearing that had documents arrive, with those documents.",
)
data class PrisonCourtDocumentHearing(
  @Schema(description = "The key an autopopulation assessment is reported against")
  val courtHearingId: UUID,
  val prisonerNumber: String,
  val hearingDate: LocalDate?,
  val hearingType: String?,
  val courtName: String?,
  @Schema(description = "Every case reference on the hearing.")
  val caseReferences: List<String>,
  @Schema(description = "The most recent arrival on this hearing")
  val receivedAt: LocalDateTime,
  val documents: List<PrisonCourtDocument>,
)

@Schema(description = "One day for one prison")
data class PrisonCourtDocumentDay(
  override val prisonCode: String,
  val date: LocalDate,
  override val rollTakenAt: LocalDateTime,
  override val rollSize: Int,
  val hearings: List<PrisonCourtDocumentHearing>,
  @Schema(
    description = "Documents whose hearing link has not resolved.",
  )
  val documentsWithoutAHearing: List<PrisonCourtDocument>,
  val totalDocuments: Int,
  @Schema(description = "True when the day held more documents than the cap and the list is partial")
  val truncated: Boolean,
  @Schema(
    description = "The people this day covers, deduplicated",
  )
  val prisonerNumbers: List<String>,
) : PrisonCourtDocumentResponse
