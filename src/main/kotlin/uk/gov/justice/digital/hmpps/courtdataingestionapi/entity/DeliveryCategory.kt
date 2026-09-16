package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "delivery_category")
data class DeliveryCategory(
  @Id
  val code: String,

  val name: String,

  @Column(name = "requires_prison_code")
  val requiresPrisonCode: Boolean = false,

  @Column(name = "unmatched_needs_review")
  val unmatchedNeedsReview: Boolean = true,

  @Column(name = "created_by")
  val createdBy: String? = null,

  @Column(name = "created_at")
  val createdAt: LocalDateTime = LocalDateTime.now(),
)
