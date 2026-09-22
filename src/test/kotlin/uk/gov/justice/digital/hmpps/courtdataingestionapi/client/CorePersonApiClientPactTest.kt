package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import au.com.dius.pact.consumer.MockServer
import au.com.dius.pact.consumer.dsl.LambdaDsl.newJsonBody
import au.com.dius.pact.consumer.dsl.PactDslWithProvider
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt
import au.com.dius.pact.consumer.junit5.PactTestFor
import au.com.dius.pact.core.model.PactSpecVersion
import au.com.dius.pact.core.model.RequestResponsePact
import au.com.dius.pact.core.model.annotations.Pact
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.web.client.RestClient
import org.springframework.web.client.support.RestClientAdapter
import org.springframework.web.service.invoker.HttpServiceProxyFactory
import org.springframework.web.service.invoker.createClient
import java.util.UUID

@ExtendWith(PactConsumerTestExt::class)
class CorePersonApiClientPactTest {

  @Test
  @PactTestFor(
    pactMethod = "getPersonByCommonPlatformId",
    pactVersion = PactSpecVersion.V3,
  )
  fun `get person by common platform ID`(mockServer: MockServer) {
    val client = createCorePersonApiClientMock(mockServer.getUrl())

    val person = client.getPersonByCommonPlatformId(defendantId)

    assertThat(person.identifiers.defendantIds).isNotEmpty()
    assertThat(person.identifiers.defendantIds.first()).isEqualTo(defendantId.toString())
    assertThat(person.identifiers.prisonNumbers).isNotEmpty()
    assertThat(person.identifiers.prisonNumbers.first()).isEqualTo(PRISONER_NUMBER)
  }

  @Pact(consumer = "hmpps-court-data-ingestion-api", provider = "hmpps-person-record")
  fun getPersonByCommonPlatformId(builder: PactDslWithProvider): RequestResponsePact = builder
    .given("A person exists for the requested common platform Id")
    .uponReceiving("a request for a person by common platform Id")
    .pathFromProviderState("/person/commonplatform/\${defendantId}", "/person/commonplatform/$defendantId")
    .method("GET")
    .willRespondWith()
    .status(200)
    .headers(JSON_HEADERS)
    .body(
      newJsonBody { body ->
        body.`object`("identifiers") { identifiers ->
          identifiers.array("prisonNumbers") { prisonNumbers ->
            prisonNumbers.stringType(PRISONER_NUMBER)
          }
          identifiers.array("defendantIds") { defendantIds ->
            defendantIds.stringType(defendantId.toString())
          }
        }
      }.build(),
    )
    .toPact()

  companion object {
    const val PRISONER_NUMBER = "OFF900"
    private val defendantId = UUID.randomUUID()
    private val JSON_HEADERS = mapOf("Content-Type" to "application/json")

    private fun createCorePersonApiClientMock(baseUrl: String): CorePersonProvider {
      val restClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

      val proxyFactory = HttpServiceProxyFactory
        .builderFor(RestClientAdapter.create(restClient))
        .build()

      return proxyFactory.createClient<CorePersonProvider>()
    }
  }
}
