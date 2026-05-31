package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "court_document")
data class CourtDocumentEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  var defendantId: UUID,
  var courtDocumentId: UUID,
  var prisonDocumentId: UUID,
  val prisonEmailAddress: String,
  @Enumerated(EnumType.STRING)
  val eventType: HmctsEventType,
  @Enumerated(EnumType.STRING)
  val courtDocumentType: CourtDocumentType,
  val documentGeneratedTimestamp: LocalDateTime,
  var ingestionAt: LocalDateTime = LocalDateTime.now(),
  @OneToMany(mappedBy = "courtDocument", cascade = [CascadeType.ALL])
  val courtDocumentCases: MutableList<CourtDocumentCaseEntity> = mutableListOf(),
  @OneToMany(mappedBy = "courtDocument", cascade = [CascadeType.ALL])
  var courtDocumentViews: MutableList<CourtDocumentViewEntity> = mutableListOf(),

  // Updated once identified
  var prisonerNumber: String? = null,
  var identifiedAt: LocalDateTime? = null,
) {
  init {
    courtDocumentCases.forEach { case -> case.courtDocument = this }
  }
}
