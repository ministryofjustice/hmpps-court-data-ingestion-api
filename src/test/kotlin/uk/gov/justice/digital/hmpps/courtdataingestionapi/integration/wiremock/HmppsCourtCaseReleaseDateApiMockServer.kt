package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase

class HmppsCourtCasesReleaseDatesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmppsCourtCasesReleaseDatesApi = HmppsCourtCasesReleaseDatesApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmppsCourtCasesReleaseDatesApi.stubEvictCache()
    hmppsCourtCasesReleaseDatesApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmppsCourtCasesReleaseDatesApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    hmppsCourtCasesReleaseDatesApi.stop()
  }
}

class HmppsCourtCasesReleaseDatesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8335
  }

  fun stubEvictCache() {
    stubFor(
      delete(urlMatching("/things-to-do/prisoner/${IntegrationTestBase.Companion.MATCHING_PRISONER_NUMBER}/evict"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(204),
        ),
    )
  }

  fun verifyEvictCache() {
    val request = deleteRequestedFor(urlMatching("/things-to-do/prisoner/${IntegrationTestBase.Companion.MATCHING_PRISONER_NUMBER}/evict"))
    verify(1, request)
  }
}
