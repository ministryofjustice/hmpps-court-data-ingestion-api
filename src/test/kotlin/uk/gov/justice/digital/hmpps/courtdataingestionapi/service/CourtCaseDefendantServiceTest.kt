package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtCaseDefendantRepository
import java.time.LocalDate
import java.util.UUID

@Transactional
class CourtCaseDefendantServiceTest : IntegrationTestBase() {

  @Autowired
  lateinit var courtCaseDefendantService: CourtCaseDefendantService

  @Autowired
  lateinit var courtCaseDefendantRepository: CourtCaseDefendantRepository

  private val master = UUID.randomUUID()
  private val defendantOnCaseA = UUID.randomUUID()
  private val defendantOnCaseB = UUID.randomUUID()

  @Test
  fun `persists a case-scoped defendant row and reads it back by defendant id`() {
    courtCaseDefendantService.upsert(defendantOnCaseA, "20GD1234567", master, "John Doe", LocalDate.of(1980, 1, 31))

    val stored = courtCaseDefendantRepository.findById(defendantOnCaseA).orElseThrow()
    assertThat(stored.caseReference).isEqualTo("20GD1234567")
    assertThat(stored.masterDefendantId).isEqualTo(master)
    assertThat(stored.name).isEqualTo("John Doe")
    assertThat(stored.dateOfBirth).isEqualTo(LocalDate.of(1980, 1, 31))
  }

  @Test
  fun `upserts in place on defendant id, storing identity as given including blank and null`() {
    courtCaseDefendantService.upsert(defendantOnCaseA, "20GD1234567", master, "John Doe", LocalDate.of(1980, 1, 31))
    courtCaseDefendantService.upsert(defendantOnCaseA, "20GD1234567", master, "", null)

    val rows = courtCaseDefendantRepository.findAllByMasterDefendantId(master)
    assertThat(rows).hasSize(1)
    assertThat(rows[0].name).isEmpty()
    assertThat(rows[0].dateOfBirth).isNull()
  }

  @Test
  fun `keeps a row per case for the same person, preserving cross-case identity divergence`() {
    courtCaseDefendantService.upsert(defendantOnCaseA, "20GD1234567", master, "John Doe", LocalDate.of(1980, 1, 31))
    // Same person on another case, name recorded differently: both rows are retained.
    courtCaseDefendantService.upsert(defendantOnCaseB, "20GD7654321", master, "Jon Doe", LocalDate.of(1980, 1, 31))

    val rows = courtCaseDefendantRepository.findAllByMasterDefendantId(master)
    assertThat(rows).hasSize(2)
    assertThat(rows.map { it.name }).containsExactlyInAnyOrder("John Doe", "Jon Doe")
  }

  @Test
  fun `resolves a person on a specific case to their case-scoped defendant id`() {
    courtCaseDefendantService.upsert(defendantOnCaseA, "20GD1234567", master, "John Doe", dob())
    courtCaseDefendantService.upsert(defendantOnCaseB, "20GD7654321", master, "John Doe", dob())

    val found = courtCaseDefendantRepository.findByMasterDefendantIdAndCaseReference(master, "20GD7654321")

    assertThat(found?.defendantId).isEqualTo(defendantOnCaseB)
  }

  private fun dob() = LocalDate.of(1980, 1, 31)
}
