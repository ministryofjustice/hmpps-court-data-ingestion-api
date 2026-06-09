package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.core.io.ClassPathResource
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase

class HmctsSubcriptionApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmctsSubcriptionApi = HmctsSubcriptionApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmctsSubcriptionApi.stubCreateSubscription()
    hmctsSubcriptionApi.stubUpdateSubscription()
    hmctsSubcriptionApi.stubFile()
    hmctsSubcriptionApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmctsSubcriptionApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    hmctsSubcriptionApi.stop()
  }
}

class HmctsSubcriptionApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8333
    const val TEST_SUBSCRIPTION_ID = "a5c06879-ee4e-4ebd-90ec-8a85efc1aed2"
    const val TEST_HMAC_KEY = "ef026f37-7552-4fb7-8e22-72243188b4a3"
    const val TEST_HMCTS_HEARING_ID = "e4ee99d2-8cfd-4444-8ef1-d79b93e0cdec"
    const val TEST_HMCTS_COURTHOUSE_ID = "e2d1bad5-0222-485a-a6ca-6d01a8804db6"
  }

  fun stubCreateSubscription() {
    stubFor(
      post(urlEqualTo("/client-subscriptions"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "clientSubscriptionId": "$TEST_SUBSCRIPTION_ID",
                "hmac": {
                  "secret": "$TEST_HMAC_KEY"
                }
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubUpdateSubscription() {
    stubFor(
      put(urlEqualTo("/client-subscriptions/$TEST_SUBSCRIPTION_ID"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(
              """
              {
                "clientSubscriptionId": "$TEST_SUBSCRIPTION_ID"
              }
              """.trimIndent(),
            ),
        ),
    )
  }

  fun stubFile() {
    stubFor(
      get(urlEqualTo("/client-subscriptions/$TEST_SUBSCRIPTION_ID/documents/${IntegrationTestBase.COURT_DOCUMENT_ID}"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "text/plain")
            .withStatus(200)
            .withBody(ClassPathResource("test.txt").contentAsByteArray)
            .withHeader("Content-Disposition", "attachment; filename=\"test.txt\""),
        ),
    )
  }
}
