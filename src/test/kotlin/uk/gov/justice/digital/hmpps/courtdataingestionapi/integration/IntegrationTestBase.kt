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
import org.springframework.http.HttpHeaders
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.localstack.LocalStackContainer
import org.testcontainers.containers.localstack.LocalStackContainer.Service
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CorePersonApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsAuthApiExtension.Companion.hmppsAuth
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.Duration

@ExtendWith(HmppsAuthApiExtension::class, CorePersonApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
abstract class IntegrationTestBase {
  protected val awaitAtMost30Secs: ConditionFactory get() = await.atMost(Duration.ofSeconds(30))

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  @MockitoSpyBean
  protected lateinit var hmppsSqsPropertiesSpy: HmppsSqsProperties

  protected val courtDataIngestionQueue by lazy { hmppsQueueService.findByQueueId("courtdataingestion") as HmppsQueue }

  @BeforeEach
  fun cleanQueue() {
    await untilCallTo {
      courtDataIngestionQueue.sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(courtDataIngestionQueue.queueUrl).build())
      courtDataIngestionQueue.sqsClient.countMessagesOnQueue(courtDataIngestionQueue.queueUrl).get()
    } matches { it == 0 }
  }

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

  companion object {

    @JvmStatic
    private val localStackContainer: LocalStackContainer =
      LocalStackContainer(DockerImageName.parse("localstack/localstack"))
        .apply {
          withEnv("DEFAULT_REGION", "eu-west-2")
          withServices(Service.SNS, Service.SQS)
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
      registry.add("hmpps.sqs.localstackUrl") { localStackContainer.getEndpointOverride(org.testcontainers.containers.localstack.LocalStackContainer.Service.SNS).toString() }
      registry.add("hmpps.sqs.region") { localStackContainer.region }
      registry.add("spring.datasource.url") { postgresContainer.jdbcUrl }
      registry.add("spring.datasource.username") { postgresContainer.username }
      registry.add("spring.datasource.password") { postgresContainer.password }
    }
  }
}
