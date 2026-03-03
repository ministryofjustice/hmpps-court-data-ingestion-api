package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Entity
data class WarrantFile(
  @Id
  val id: UUID = UUID.randomUUID(),
  var defendantId: UUID,
  var externalFileId: String,
  var defendantName: String? = null,
  val defendantDateOfBirth: LocalDate? = null,
  val prisonEmailAddress: String? = null,
  val documentGeneratedTimestamp: LocalDateTime? = null,
  val ingestionAt: LocalDateTime = LocalDateTime.now(),
  @OneToMany(mappedBy = "warrantFile", cascade = [CascadeType.ALL])
  val warrantFileCases: List<WarrantFileCase> = emptyList(),
  @OneToMany(mappedBy = "warrantFile")
  val identifiedWarrantFiles: List<IdentifiedWarrantFile> = emptyList(),
) {
  init {
    warrantFileCases.forEach { case -> case.warrantFile = this }
  }
}
