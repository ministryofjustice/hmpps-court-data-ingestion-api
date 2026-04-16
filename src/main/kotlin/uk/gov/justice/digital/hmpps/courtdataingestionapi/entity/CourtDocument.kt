package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.time.LocalDateTime
import java.util.UUID

@Entity
data class CourtDocument(
  @Id
  val id: UUID = UUID.randomUUID(),
  var defendantId: UUID,
  var courtDocumentId: UUID,
  var prisonDocumentId: UUID,
  val prisonEmailAddress: String,
  val documentGeneratedTimestamp: LocalDateTime,
  var ingestionAt: LocalDateTime = LocalDateTime.now(),
  @OneToMany(mappedBy = "courtDocument", cascade = [CascadeType.ALL])
  val courtDocumentCases: List<CourtDocumentCase> = emptyList(),

  // Updated once identified
  var prisonerNumber: String? = null,
  val identifiedAt: LocalDateTime? = null,
) {
  init {
    courtDocumentCases.forEach { case -> case.courtDocument = this }
  }
}
