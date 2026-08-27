package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "court_next_hearing")
data class CourtNextHearingEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  @ManyToOne
  var courtHearing: CourtHearingEntity? = null,
  val defendantId: UUID,
  val masterDefendantId: UUID,
  var hmctsCourtId: UUID,
  var courtName: String,
  var hmppsCourtId: String? = null,
  val dateTime: LocalDateTime,
  val hearingId: String,
)
