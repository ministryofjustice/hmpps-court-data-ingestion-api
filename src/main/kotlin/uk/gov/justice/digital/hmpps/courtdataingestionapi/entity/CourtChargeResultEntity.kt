package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "court_charge_result")
data class CourtChargeResultEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  @ManyToOne
  var courtCharge: CourtChargeEntity? = null,
  val resultCode: String,
  val resultDescription: String,
)
