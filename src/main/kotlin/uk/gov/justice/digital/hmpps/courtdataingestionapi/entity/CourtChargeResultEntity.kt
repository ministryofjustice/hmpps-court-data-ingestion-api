package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OneToOne
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
  @OneToOne(mappedBy = "courtChargeResult", cascade = [CascadeType.ALL])
  val courtNextHearing: CourtNextHearingEntity?,
  @OneToMany(mappedBy = "courtChargeResult", cascade = [CascadeType.ALL])
  val resultTexts: List<CourtChargeResultTextEntity>,
) {
  init {
    resultTexts.forEach { resultText -> resultText.courtChargeResult = this }
    courtNextHearing?.courtChargeResult = this
  }
}
