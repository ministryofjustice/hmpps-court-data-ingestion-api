package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsCase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsSubscriptionNotificationRequestBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.Boolean

class CourtDataIngestionServiceTest : IntegrationTestBase() {
  @MockitoBean
  lateinit var fileService: FileService

  @Autowired
  lateinit var courtDataIngestionService: CourtDataIngestionService

  @BeforeEach
  fun setUp() {
    courtDocumentRepository.deleteAll()
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
    val prisonDocumentUuid = UUID.randomUUID()
    setupFileServiceMock(prisonDocumentUuid, contentHashPushed, metadataPushed)
    val message = setupSubscriptionNotificationRequest()

    courtDataIngestionService.receiveMessage(message)

    val curtDocument = courtDocumentRepository.findFirstByPrisonDocumentId(prisonDocumentUuid).get()

    assertThat(curtDocument.metadataVersion).isEqualTo(expected)
  }

  private fun setupFileServiceMock(documentUuid: UUID, contentHashPushed: Boolean, metadataPushed: Boolean) {
    whenever(fileService.ingestFile(any(), any()))
      .thenReturn(setupDocumentResponseFromFileService(documentUuid))

    whenever(fileService.mirrorEnrichmentToDocumentStore(any()))
      .thenReturn(
        FileService.MirrorOutcome(
          contentHashPushed,
          metadataPushed,
          if (contentHashPushed) null else Exception(),
          if (metadataPushed) null else Exception(),
        ),
      )
  }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)

    @JvmStatic
    private fun setupSubscriptionNotificationRequest(): HmctsSubscriptionNotificationRequestBody = HmctsSubscriptionNotificationRequestBody(
      masterDefendantId = MATCHING_CORE_PERSON,
      documentId = COURT_DOCUMENT_ID,
      cases = listOf(HmctsCase(CASE_REFERENCE)),
      prisonEmailAddress = "prison.email@example.com",
      documentGeneratedTimestamp = ZonedDateTime.of(2026, 6, 12, 16, 0, 0, 0, ZoneOffset.UTC),
      eventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
      hearingId = UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID),
    )

    @JvmStatic
    private fun setupDocumentResponseFromFileService(documentUuid: UUID): Document = Document(
      documentUuid = documentUuid,
      documentType = DocumentApiType.PRISON_COURT_REGISTER,
      documentFilename = "mocked-test-file",
      filename = "mocked-test-file",
      fileExtension = "",
      fileSize = 1L,
      fileHash = "mocked-file-hash",
      fileContentHash = null,
      mimeType = "mock",
      metadata = mapOf(),
      createdTime = LocalDateTime.now(),
      createdByServiceName = "test",
      createdByUsername = "test",
      duplicateOf = null,
    )
  }
}
