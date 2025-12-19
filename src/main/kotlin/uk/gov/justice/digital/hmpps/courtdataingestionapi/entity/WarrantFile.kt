package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.time.LocalDateTime
import java.util.UUID

@Entity
data class WarrantFile(
  @Id
  val id: UUID = UUID.randomUUID(),
  var defendantId: UUID,
  var externalFileId: String,
  val ingestionAt: LocalDateTime = LocalDateTime.now(),
  @OneToMany(mappedBy = "warrantFile")
  val identifiedWarrantFiles: List<IdentifiedWarrantFile> = emptyList(),
)
