package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository

class AddressedPrisonResolutionIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  private lateinit var prisonEmailMappingRepository: PrisonEmailMappingRepository

  @BeforeEach
  fun seedMapping() {
    jdbcTemplate.update(
      PRISON_EMAIL_ADD_MAPPING_SQL.trimIndent(),
      PRISON_EMAIL,
      PRISON_CODE_MAPPING,
      "PRISON",
    )
  }

  @Test
  fun `diagnostic - the seeded mapping is readable through the repository`() {
    // If this fails, the problem is the seed, the table, or the lookup query, not the enricher.
    assertThat(prisonEmailMappingRepository.findMappingByEmail(PRISON_EMAIL)?.prisonCode).isEqualTo(PRISON_CODE_MAPPING)
  }

  @Test
  fun `ingesting a document resolves the delivery mailbox to a prison code`() {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    val document = courtDocumentRepository.findFirstByDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!

    assertThat(document.prisonEmailAddress).isEqualTo(PRISON_EMAIL)
    assertThat(document.addressedPrison).isEqualTo(PRISON_CODE_MAPPING)
  }

  companion object {
    const val PRISON_CODE_MAPPING: String = "LII"
    const val PRISON_EMAIL_ADD_MAPPING_SQL: String = """
      INSERT INTO prison_email_mapping (email, prison_code, source_type)
      VALUES (?, ?, ?)
      ON CONFLICT (email) DO UPDATE SET prison_code = EXCLUDED.prison_code, source_type = EXCLUDED.source_type
      """
  }
}
