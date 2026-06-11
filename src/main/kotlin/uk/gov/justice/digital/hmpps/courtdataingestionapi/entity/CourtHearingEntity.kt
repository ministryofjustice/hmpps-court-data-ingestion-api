package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "court_hearing")
data class CourtHearingEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  var courtId: UUID,
  var courtName: String,
  var hearingType: String,
  var hearingDate: LocalDateTime,
  var hmctsCourtHearingId: UUID,
  @OneToMany(mappedBy = "courtHearing")
  var courtDocuments: MutableList<CourtDocumentEntity>,
  var createdAt: LocalDateTime = LocalDateTime.now(),
  var updatedAt: LocalDateTime = LocalDateTime.now(),

)
