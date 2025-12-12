package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table
data class WarrantFile(
  @Id
  @NotNull
  val id: UUID = UUID.randomUUID(),
  @NotNull
  var defendantId: String = "",
  @NotNull
  var externalFileId: String = "",
  @NotNull
  val ingestionAt: LocalDateTime = LocalDateTime.now(),
)
