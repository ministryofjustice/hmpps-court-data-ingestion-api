package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import kotlin.Boolean

class CourtDataIngestionServiceTest : IntegrationTestBase() {
  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  lateinit var courtDataIngestionService: CourtDataIngestionService

  @BeforeEach
  fun setUp() {
    courtDocumentRepository.deleteAll()
    setupPrisonEmailMappingRepository()
  }

  @ParameterizedTest
  @CsvSource(
    "true, true, 1",
    "true, false, 0",
    "false, true, 0",
    "false, false, 0",
  )
  fun `When given mirror outcome {contentHashPushed} and {metadataPushed}, then should return metadata_version as {expected}`(contentHashPushed: Boolean, metadataPushed: Boolean, expected: Int) {
    log.debug("When given mirror outcome contentHashPushed=[{}] and metadataPushed=[{}], then should return metadata_version as expected=[{}]", contentHashPushed, metadataPushed, expected)
    setupMocks(contentHashPushed, metadataPushed)

    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    val curtDocument = courtDocumentRepository.findFirstByPrisonDocumentId(PRISON_DOCUMENT_ID).get()
    assertThat(curtDocument.metadataVersion).isEqualTo(expected)
  }

  private fun setupMocks(contentHashPushed: Boolean, metadataPushed: Boolean) {
    if (!contentHashPushed) {
      HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubSetFileContentHashError()
    }

    if (!metadataPushed) {
      HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubUpdateMetadataError()
    }
  }

  fun setupPrisonEmailMappingRepository() = jdbcTemplate.update(
    PRISON_EMAIL_ADD_MAPPING_SQL.trimIndent(),
    PRISON_EMAIL_MAPPING,
    PRISON_CODE_MAPPING,
    "PRISON",
  )

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
