package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "court_pcr_api_response")
data class CourtPcrApiResponse(
  @Id
  val id: UUID = UUID.randomUUID(),
  @ManyToOne
  var courtHearing: CourtHearingEntity? = null,
  val defendantId: UUID,
  val masterDefendantId: UUID,
  @JdbcTypeCode(SqlTypes.JSON)
  var apiResponse: String,
)
