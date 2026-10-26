package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "court_schedule_snapshot")
data class CourtScheduleSnapshotEntity(
  @Id
  var id: UUID = UUID.randomUUID(),
  var caseReference: String,
  var fetchedAt: LocalDateTime = LocalDateTime.now(),
  var httpStatus: Int,
  var bodySha256: String? = null,
  @JdbcTypeCode(SqlTypes.JSON)
  var body: String? = null,
  var errorBody: String? = null,
  var changed: Boolean,
)
