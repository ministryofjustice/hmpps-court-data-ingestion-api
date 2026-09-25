package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "court_charge_result_text")
data class CourtChargeResultTextEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  @ManyToOne
  var courtChargeResult: CourtChargeResultEntity? = null,
  val key: String,
  val value: String?,
)
