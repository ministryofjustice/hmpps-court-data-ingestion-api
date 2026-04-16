package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import java.util.UUID

@Entity
data class CourtDocumentCase(
  @Id
  val id: UUID = UUID.randomUUID(),
  val caseReference: String? = null,
  @ManyToOne
  var courtDocument: CourtDocument? = null,
)
