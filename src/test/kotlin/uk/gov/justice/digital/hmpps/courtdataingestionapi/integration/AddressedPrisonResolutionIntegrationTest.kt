package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
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
  fun `ingesting a document resolves the delivery address to a prison code`() {
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)

    val document = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!

    assertThat(document.prisonEmailAddress).isEqualTo(PRISON_EMAIL)
    assertThat(document.addressedPrison).isEqualTo(PRISON_CODE_MAPPING)
  }

  @Test
  fun `ingesting a document mirrors its delivery source from the mapping`() {
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)

    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verify(
      patchRequestedFor(urlMatching("/documents/.*/metadata"))
        .withRequestBody(matchingJsonPath("$.deliverySource", equalTo("PRISON"))),
    )
  }

  companion object {
    const val PRISON_CODE_MAPPING: String = "LII"
    const val PRISON_EMAIL_ADD_MAPPING_SQL: String = """
      INSERT INTO prison_email_mapping (email, prison_code, category_code)
      VALUES (?, ?, ?)
      ON CONFLICT (email) DO UPDATE SET prison_code = EXCLUDED.prison_code, category_code = EXCLUDED.category_code
      """
  }
}
