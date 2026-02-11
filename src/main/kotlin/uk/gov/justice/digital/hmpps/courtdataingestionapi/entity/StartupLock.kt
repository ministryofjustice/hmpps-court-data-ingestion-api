package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import java.time.LocalDateTime

@Entity
class StartupLock(
  @Id
  val lockName: String,
  val lockedAt: LocalDateTime = LocalDateTime.now(),
)
