package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.time.LocalDateTime
import java.time.ZoneOffset

class HmctsAuthApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmctsAuth = HmctsAuthMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmctsAuth.start()
    hmctsAuth.stubGrantToken()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmctsAuth.resetRequests()
    hmctsAuth.stubGrantToken()
  }

  override fun afterAll(context: ExtensionContext) {
    hmctsAuth.stop()
  }
}

class HmctsAuthMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8091
  }

  fun stubGrantToken() {
    stubFor(
      post(urlEqualTo("/oauth2/v2.0/token"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              """
                {
                  "token_type": "bearer",
                  "access_token": "ABCDE",
                  "expires_in": ${LocalDateTime.now().plusHours(2).toEpochSecond(ZoneOffset.UTC)}
                }
              """.trimIndent(),
            ),
        ),
    )
  }
}
