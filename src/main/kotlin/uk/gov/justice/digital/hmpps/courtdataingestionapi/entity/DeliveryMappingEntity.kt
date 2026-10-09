package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Immutable
import java.util.UUID

@Entity
@Immutable
@Table(name = "prison_email_mapping")
data class DeliveryMappingEntity(
  @Id
  val id: UUID,

  val email: String,

  @Column(name = "prison_code")
  val prisonCode: String?,

  @ManyToOne
  @JoinColumn(name = "category_code")
  val category: DeliveryCategory?,
) {
  val destinationPrison: String?
    get() = if (category?.requiresPrisonCode == false) null else prisonCode
}
