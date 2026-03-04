package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class HmctsSubscriptionApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmctsSubscriptionApi = HmctsSubscriptionApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmctsSubscriptionApi.stubCreateSubscription()
    hmctsSubscriptionApi.stubUpdateSubscription()
    hmctsSubscriptionApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmctsSubscriptionApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    hmctsSubscriptionApi.stop()
  }
}

class HmctsSubscriptionApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8333
    const val TEST_SUBSCRIPTION_ID = "a5c06879-ee4e-4ebd-90ec-8a85efc1aed2"
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
                "clientSubscriptionId": "$TEST_SUBSCRIPTION_ID"
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
}
