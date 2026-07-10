package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "court_case_defendant")
data class CourtCaseDefendantEntity(
  @Id
  @Column(name = "defendant_id")
  val defendantId: UUID,

  @Column(name = "case_reference")
  var caseReference: String,

  @Column(name = "master_defendant_id")
  var masterDefendantId: UUID,

  var name: String? = null,

  @Column(name = "date_of_birth")
  var dateOfBirth: LocalDate? = null,

  @Column(name = "retrieved_at")
  var retrievedAt: LocalDateTime = LocalDateTime.now(),

  var source: String = "hmcts-court-defendant-api",
)
