package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.awaitility.core.ConditionFactory
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill.BackfillBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.controller.BackfillEndpoint
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CorePersonApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CourtRegisterApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsAuthApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtDefendantApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtScheduleApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourthouseApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsCourtCasesReleaseDatesApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.PrisonerSearchApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HMPPSPrisonerSearchEvent
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsCase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsSubscriptionNotificationRequestBody
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.PrisonerSearchEventAdditionalInformation
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.SQSMessage
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

@ExtendWith(
  HmppsAuthApiExtension::class,
  HmctsAuthApiExtension::class,
  CorePersonApiExtension::class,
  HmctsSubcriptionApiExtension::class,
  HmppsDocumentManagementApiExtension::class,
  HmppsCourtCasesReleaseDatesApiExtension::class,
  PrisonerSearchApiExtension::class,
  HmctsCourtScheduleApiExtension::class,
  HmctsCourthouseApiExtension::class,
  HmctsCourtDefendantApiExtension::class,
  CourtRegisterApiExtension::class,
)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
abstract class IntegrationTestBase {
  protected val awaitAtMost30Secs: ConditionFactory get() = await.atMost(Duration.ofSeconds(30))

  @Autowired
  protected lateinit var courtDocumentRepository: CourtDocumentRepository

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  @Autowired
  private lateinit var dataSource: DataSource

  @MockitoSpyBean
  protected lateinit var hmppsSqsPropertiesSpy: HmppsSqsProperties

  protected val courtDataIngestionQueue by lazy { hmppsQueueService.findByQueueId("courtdataingestion") as HmppsQueue }
  protected val courtWarrantTestQueue by lazy { hmppsQueueService.findByQueueId("courtwarranttest") as HmppsQueue }
  protected val prisonerCreatedQueue by lazy { hmppsQueueService.findByQueueId("prisonercreated") as HmppsQueue }

  @BeforeEach
  fun cleanQueue() {
    cleanQueue(courtDataIngestionQueue)
    cleanQueue(courtWarrantTestQueue)
    cleanQueue(prisonerCreatedQueue)
  }

  @BeforeEach
  fun cleanDatabase() {
    dataSource.connection.use { connection ->
      connection.autoCommit = true
      connection.createStatement().use { it.execute("TRUNCATE court_document CASCADE") }
    }
  }

  fun cleanQueue(queue: HmppsQueue) {
    await untilCallTo {
      queue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queue.queueUrl).build())
      queue.sqsClient.countMessagesOnQueue(queue.queueUrl).get()
    } matches { it == 0 }
  }

  fun getLatestMessage(queue: HmppsQueue): ReceiveMessageResponse? = queue.sqsClient.receiveMessage(ReceiveMessageRequest.builder().maxNumberOfMessages(1).queueUrl(queue.queueUrl).build()).get()

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  protected fun stubPingWithResponse(status: Int) {
    hmppsAuth.stubHealthPing(status)
  }

  protected fun sendSubscriptionNotification(
    defendantId: UUID,
    documentId: UUID = COURT_DOCUMENT_ID,
  ): HmctsSubscriptionNotificationRequestBody {
    val event =
      HmctsSubscriptionNotificationRequestBody(
        masterDefendantId = defendantId,
        documentId = documentId,
        cases = listOf(
          HmctsCase(CASE_REFERENCE),
        ),
        prisonEmailAddress = PRISON_EMAIL,
        documentGeneratedTimestamp = ZonedDateTime.of(2026, 6, 12, 16, 0, 0, 0, ZoneOffset.UTC),
        eventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
        hearingId = UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID),
      )
    courtDataIngestionQueue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(courtDataIngestionQueue.queueUrl)
        .messageBody(TestUtil.objectMapper().writeValueAsString(event))
        .build(),
    )

    awaitAtMost30Secs untilCallTo {
      courtDocumentRepository.countByMasterDefendantId(defendantId)
    } matches { it!! >= 1L }
    return event
  }

  protected fun sendPrisonerCreatedMessage(prisonerNumber: String) {
    val event = SQSMessage(
      Type = "Notification",
      MessageId = UUID.randomUUID().toString(),
      Message = TestUtil.objectMapper().writeValueAsString(
        HMPPSPrisonerSearchEvent(
          eventType = "prisoner-offender-search.prisoner.created",
          additionalInformation = PrisonerSearchEventAdditionalInformation(
            prisonerNumber,
          ),
        ),
      ),
    )
    prisonerCreatedQueue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(prisonerCreatedQueue.queueUrl)
        .messageBody(TestUtil.objectMapper().writeValueAsString(event))
        .build(),
    )
  }

  protected fun sendPrisonerUpdatedMessage(prisonerNumber: String, categoriesChanged: List<String>) {
    val event = SQSMessage(
      Type = "Notification",
      MessageId = UUID.randomUUID().toString(),
      Message = TestUtil.objectMapper().writeValueAsString(
        HMPPSPrisonerSearchEvent(
          eventType = "prisoner-offender-search.prisoner.updated",
          additionalInformation = PrisonerSearchEventAdditionalInformation(
            prisonerNumber,
            categoriesChanged,
          ),
        ),
      ),
    )
    prisonerCreatedQueue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(prisonerCreatedQueue.queueUrl)
        .messageBody(TestUtil.objectMapper().writeValueAsString(event))
        .build(),
    )
  }

  protected fun runBackfill(backfillId: String) {
    startBackfill(backfillId)
    backfillCallBack(backfillId)
  }

  private fun startBackfill(backfillId: String) {
    webTestClient.post()
      .uri("/backfill")
      .bodyValue(BackfillBody(backfillId))
      .exchange()
      .expectStatus()
      .isOk
  }

  private fun backfillCallBack(backfillId: String) {
    await untilCallTo {
      webTestClient.get()
        .uri("/backfill/$backfillId")
        .exchange()
        .expectStatus()
        .isOk
        .expectBody<BackfillEndpoint.StatusResponse>()
        .returnResult().responseBody!!
    } matches { it?.status == "COMPLETED" }
  }

  companion object {
    val COURT_DOCUMENT_ID = UUID.randomUUID()
    val PRISON_DOCUMENT_ID = UUID.randomUUID()
    val NOT_FOUND_CORE_PERSON = UUID.randomUUID()
    val NO_MATCHING_IDS_PERSON = UUID.randomUUID()
    val MATCHING_CORE_PERSON = UUID.randomUUID()
    val MATCHING_CORE_ALIASES = UUID.randomUUID()
    val CASE_REFERENCE = "CASE123456"
    const val MATCHING_PRISONER_NUMBER = "ABC123"
    const val TEST_USERNAME = "testuser"
    const val PRISON_EMAIL: String = "prison.email@example.com"

    @JvmStatic
    private val localStackContainer: LocalStackContainer =
      LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
        .apply {
          withEnv("DEFAULT_REGION", "eu-west-2")
          withServices(Service.SNS, Service.SQS, Service.SECRETSMANAGER)
        }

    @JvmStatic
    private val postgresContainer = PostgreSQLContainer<Nothing>("postgres:18")
      .apply {
        withUsername("court_data_ingestion")
        withPassword("court_data_ingestion")
        withDatabaseName("court_data_ingestion")
        withReuse(true)
      }

    @BeforeAll
    @JvmStatic
    fun startContainers() {
      localStackContainer.start()
      postgresContainer.start()
    }

    @DynamicPropertySource
    @JvmStatic
    fun setUpProperties(registry: DynamicPropertyRegistry) {
      registry.add("hmpps.sqs.localstackUrl") { localStackContainer.getEndpointOverride(Service.SNS).toString() }
      registry.add("hmpps.sqs.region") { localStackContainer.region }
      registry.add("hmpps.secret.localstackUrl") { localStackContainer.getEndpointOverride(Service.SECRETSMANAGER).toString() }
      registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
      registry.add("spring.datasource.username") { postgresContainer.username }
      registry.add("spring.datasource.password") { postgresContainer.password }
    }
  }
}
