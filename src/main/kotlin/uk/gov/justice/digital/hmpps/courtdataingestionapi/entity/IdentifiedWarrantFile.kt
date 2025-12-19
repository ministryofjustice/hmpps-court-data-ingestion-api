package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.time.LocalDateTime
import java.util.UUID

@Entity
data class IdentifiedWarrantFile(
  @Id
  val id: UUID = UUID.randomUUID(),
  @ManyToOne
  @JoinColumn(name = "warrant_file_id")
  val warrantFile: WarrantFile,
  val prisonerNumber: String,
  val identifiedAt: LocalDateTime = LocalDateTime.now(),
)
