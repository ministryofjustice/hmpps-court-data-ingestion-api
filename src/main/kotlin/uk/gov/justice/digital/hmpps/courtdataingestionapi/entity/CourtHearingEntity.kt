package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.EntityNotFoundException
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearingDocument
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "court_hearing")
data class CourtHearingEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  var courtId: UUID,
  var courtName: String,
  var courtCode: String? = null,
  var hearingType: String,
  var hearingDate: LocalDateTime,
  var hmctsCourtHearingId: UUID,
  @OneToMany(mappedBy = "courtHearing")
  var courtDocuments: MutableList<CourtDocumentEntity>,
  var createdAt: LocalDateTime = LocalDateTime.now(),
  var updatedAt: LocalDateTime = LocalDateTime.now(),
) {

  // TODO make not nullable string once old endpoint is removed.
  fun toCourtHearing(prisonerNumber: String?): CourtHearing {
    val documents = courtDocuments
      .filter {
        prisonerNumber == null || it.prisonerNumber == prisonerNumber
      }
    if (documents.isEmpty()) {
      throw EntityNotFoundException("No hearing document found for $prisonerNumber hearing $hmctsCourtHearingId")
    }
    return CourtHearing(
      hearingId = hmctsCourtHearingId,
      courtName = courtName,
      courtId = courtId,
      courtCode = courtCode,
      hearingDate = hearingDate,
      caseReferences = documents.flatMap { it.courtDocumentCases.map { case -> case.caseReference } }.distinct(),
      hearingType = hearingType,
      documents = documents.map {
        CourtHearingDocument(
          it.courtDocumentType,
          it.prisonDocumentId,
          it.ingestionAt,
        )
      },
    )
  }
}
