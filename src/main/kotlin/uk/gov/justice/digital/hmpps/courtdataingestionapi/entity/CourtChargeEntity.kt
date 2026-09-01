package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "court_charge")
data class CourtChargeEntity(
  @Id
  val id: UUID = UUID.randomUUID(),
  @ManyToOne
  var courtHearing: CourtHearingEntity? = null,
  val defendantId: UUID,
  val masterDefendantId: UUID,
  val listingNumber: Int,
  val offenceLegislation: String,
  val pleaDate: LocalDate,
  val pleaValue: String,
  val startDate: LocalDate,
  val endDate: LocalDate?,
  val code: String,
  val title: String,
  val wording: String,
  @OneToMany(mappedBy = "courtCharge", cascade = [CascadeType.ALL])
  val results: List<CourtChargeResultEntity>,
) {
  init {
    results.forEach { result -> result.courtCharge = this }
  }
}
