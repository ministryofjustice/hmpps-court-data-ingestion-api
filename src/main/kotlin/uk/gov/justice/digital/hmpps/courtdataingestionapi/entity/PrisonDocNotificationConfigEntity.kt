package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "prison_doc_notification_config")
data class PrisonDocNotificationConfigEntity(
  @Id
  val prisonId: String,
  val newDocDateFrom: LocalDateTime,
)
