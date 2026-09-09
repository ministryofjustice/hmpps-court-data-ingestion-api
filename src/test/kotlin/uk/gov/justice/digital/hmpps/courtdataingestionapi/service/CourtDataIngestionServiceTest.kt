package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import jakarta.transaction.Transactional
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.AddressedPrisonResolutionIntegrationTest.Companion.PRISON_CODE_MAPPING
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.AddressedPrisonResolutionIntegrationTest.Companion.PRISON_EMAIL_ADD_MAPPING_SQL
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsCase
import kotlin.Boolean

class CourtDataIngestionServiceTest : IntegrationTestBase() {
  @Autowired
  private lateinit var jdbcTemplate: JdbcTemplate

  @Autowired
  lateinit var defendantMatchingService: DefendantMatchingService

  @BeforeEach
  fun setUp() {
    courtDocumentRepository.deleteAll()
    setupPrisonEmailMappingRepository()
  }

  @ParameterizedTest
  @CsvSource(
    "true, true, 2",
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

  @Transactional
  @ParameterizedTest
  @MethodSource("getHmctsCasesForDataIngestionTestParameters")
  fun `When passing case URNs {cases}, then should create {expectedTotal} case references and first one should be {expectedFirstValue}`(cases: List<String>, expectedTotal: Int, expectedFirstValue: String) {
    log.debug("When passing case URNs [{}], then should create [{}] case references and first one should be [{}]", cases, expectedTotal, expectedFirstValue)

    sendSubscriptionNotification(MATCHING_CORE_PERSON, hmctsCases = cases.map { HmctsCase(it) })

    val curtDocument = courtDocumentRepository.findFirstByPrisonDocumentId(PRISON_DOCUMENT_ID).get()
    assertThat(curtDocument.courtDocumentCases.size).isEqualTo(expectedTotal)
    assertThat(curtDocument.courtDocumentCases.first().caseReference).isEqualTo(expectedFirstValue)
  }

  private fun setupMocks(contentHashPushed: Boolean, metadataPushed: Boolean) {
    if (!contentHashPushed) {
      HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubSetFileContentHashError()
    }

    if (!metadataPushed) {
      HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.stubMergeMetadataError()
    }
  }

  fun setupPrisonEmailMappingRepository() = jdbcTemplate.update(
    PRISON_EMAIL_ADD_MAPPING_SQL.trimIndent(),
    PRISON_EMAIL,
    PRISON_CODE_MAPPING,
    "PRISON",
  )

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)

    @JvmStatic
    fun getHmctsCasesForDataIngestionTestParameters() = listOf(
      Arguments.of(listOf(CASE_REFERENCE), 1, CASE_REFERENCE),
      Arguments.of(listOf("$CASE_REFERENCE,$CASE_REFERENCE"), 1, CASE_REFERENCE),
      Arguments.of(listOf("$CASE_REFERENCE, $CASE_REFERENCE"), 1, CASE_REFERENCE),
      Arguments.of(listOf(CASE_REFERENCE, "$CASE_REFERENCE,$CASE_REFERENCE"), 1, CASE_REFERENCE),
    )
  }
}
