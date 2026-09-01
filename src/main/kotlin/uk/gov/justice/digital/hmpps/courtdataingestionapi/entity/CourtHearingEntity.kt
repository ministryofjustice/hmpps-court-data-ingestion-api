package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EntityNotFoundException
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtCharge
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearingDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtResult
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.NextCourtHearing
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "court_hearing")
data class CourtHearingEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  var hmctsCourtId: UUID,
  var courtName: String,
  var hmppsCourtId: String? = null,
  var hearingType: String,
  var hearingDate: LocalDate,
  var hmctsCourtHearingId: UUID,
  @OneToMany(mappedBy = "courtHearing")
  var courtDocuments: MutableList<CourtDocumentEntity>,
  @OneToMany(mappedBy = "courtHearing", cascade = [CascadeType.ALL], orphanRemoval = true)
  var courtCharges: MutableList<CourtChargeEntity>,
  @OneToMany(mappedBy = "courtHearing", cascade = [CascadeType.ALL], orphanRemoval = true)
  var nextCourtHearings: MutableList<CourtNextHearingEntity>,
  var createdAt: LocalDateTime = LocalDateTime.now(),
  var updatedAt: LocalDateTime = LocalDateTime.now(),
  @JdbcTypeCode(SqlTypes.JSON)
  var apiResponse: String? = null,
) {
  init {
    courtCharges.forEach { charge -> charge.courtHearing = this }
    nextCourtHearings.forEach { courtHearing -> courtHearing.courtHearing = this }
  }

  fun toCourtHearing(prisonerNumber: String): CourtHearing {
    val documents = courtDocuments
      .filter { it.prisonerNumber == prisonerNumber }
    if (documents.isEmpty()) {
      throw EntityNotFoundException("No hearing document found for $prisonerNumber hearing $hmctsCourtHearingId")
    }
    val masterDefendantId = documents.first().masterDefendantId
    return CourtHearing(
      hearingId = hmctsCourtHearingId,
      courtName = courtName,
      courtId = hmctsCourtId,
      courtCode = hmppsCourtId,
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
      charges = courtCharges.filter {
        it.masterDefendantId == masterDefendantId
      }.map {
        CourtCharge(
          listingNumber = it.listingNumber,
          offenceLegislation = it.offenceLegislation,
          pleaDate = it.pleaDate,
          pleaValue = it.pleaValue,
          startDate = it.startDate,
          title = it.title,
          wording = it.wording,
          code = it.code,
          results = it.results.map { result ->
            CourtResult(
              code = result.resultCode,
              description = result.resultDescription,
            )
          },
        )
      },
      nextHearing = nextCourtHearings.find {
        it.masterDefendantId == masterDefendantId
      }?.let {
        NextCourtHearing(
          courtName = it.courtName,
          hmctsCourtId = it.hmctsCourtId,
          hmppsCourtId = it.hmppsCourtId,
          hearingDate = it.dateTime,
        )
      },
    )
  }
}
