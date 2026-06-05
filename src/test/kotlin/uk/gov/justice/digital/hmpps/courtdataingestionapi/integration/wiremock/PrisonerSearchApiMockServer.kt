package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.HttpHeaders
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.PrisonerSearchEventListenerIntTest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.prisonersearch.Prisoner

class PrisonerSearchApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val prisonerSearchApi = PrisonerSearchApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    prisonerSearchApi.stubPrisonerSearch(PrisonerSearchEventListenerIntTest.PRISONER_NUMBER_WITH_MATCH)
    prisonerSearchApi.stubPrisonerSearch("ABC123")
    prisonerSearchApi.stubPrisonerSearchWithNoPrison("XYZ789")
    prisonerSearchApi.stubPrisonerSearchNotFound("XXX404")
    prisonerSearchApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    prisonerSearchApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    prisonerSearchApi.stop()
  }
}

class PrisonerSearchApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8336
  }

  fun stubPrisonerSearchNotFound(prisonerNumber: String) {
    stubFor(
      get(urlEqualTo("/prisoner/$prisonerNumber"))
        .willReturn(
          aResponse().withStatus(404),
        ),
    )
  }

  fun stubPrisonerSearch(prisonerNumber: String) {
    stubFor(
      get(urlEqualTo("/prisoner/$prisonerNumber"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              TestUtil.objectMapper().writeValueAsString(prisonerRecord(prisonerNumber = prisonerNumber)),
            ),
        ),
    )
  }

  fun stubPrisonerSearchWithNoPrison(prisonerNumber: String) {
    stubFor(
      get(urlEqualTo("/prisoner/$prisonerNumber"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              TestUtil.objectMapper().writeValueAsString(prisonerRecord(prisonerNumber = prisonerNumber, prisonId = null)),
            ),
        ),
    )
  }

  private fun prisonerRecord(prisonerNumber: String, prisonId: String? = "Mock01") = Prisoner(prisonerNumber, prisonId)
}
