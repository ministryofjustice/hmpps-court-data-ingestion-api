package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "court_document_case")
data class CourtDocumentCaseEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  val caseReference: String? = null,
  @ManyToOne
  var courtDocument: CourtDocumentEntity? = null,
)
