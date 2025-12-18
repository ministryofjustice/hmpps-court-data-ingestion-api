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
import uk.gov.justice.digital.hmpps.courtdataingestionapi.coreperson.model.CanonicalEthnicity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.coreperson.model.CanonicalIdentifiers
import uk.gov.justice.digital.hmpps.courtdataingestionapi.coreperson.model.CanonicalRecord
import uk.gov.justice.digital.hmpps.courtdataingestionapi.coreperson.model.CanonicalReligion
import uk.gov.justice.digital.hmpps.courtdataingestionapi.coreperson.model.CanonicalSex
import uk.gov.justice.digital.hmpps.courtdataingestionapi.coreperson.model.CanonicalTitle
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.CourtDataIngestionIntTest
import java.util.UUID

class CorePersonApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val corePersonApi = CorePersonApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    corePersonApi.stubCorePersonNotFound(CourtDataIngestionIntTest.NOT_FOUND_CORE_PERSON)
    corePersonApi.stubCorePersonNoPrisonNumber(CourtDataIngestionIntTest.NO_MATCHING_IDS_PERSON)
    corePersonApi.stubCorePersonWithPrisonNumber(CourtDataIngestionIntTest.MATCHING_CORE_PERSON, CourtDataIngestionIntTest.MATCHING_PRISONER_NUMBERS)
    corePersonApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    corePersonApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    corePersonApi.stop()
  }
}

class CorePersonApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8332
  }

  fun stubCorePersonNotFound(defendantId: UUID) {
    stubFor(
      get(urlEqualTo("/person/commonplatform/$defendantId"))
        .willReturn(
          aResponse().withStatus(404),
        ),
    )
  }

  fun stubCorePersonNoPrisonNumber(defendantId: UUID) {
    stubFor(
      get(urlEqualTo("/person/commonplatform/$defendantId"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              TestUtil.objectMapper().writeValueAsString(canonicalRecord(defendantId, emptyList())),
            ),
        ),
    )
  }

  fun stubCorePersonWithPrisonNumber(defendantId: UUID, prisonerNumbers: List<String>) {
    stubFor(
      get(urlEqualTo("/person/commonplatform/$defendantId"))
        .willReturn(
          aResponse()
            .withHeaders(HttpHeaders(HttpHeader("Content-Type", "application/json")))
            .withBody(
              TestUtil.objectMapper().writeValueAsString(canonicalRecord(defendantId, prisonerNumbers)),
            ),
        ),
    )
  }

  private fun canonicalRecord(defendantId: UUID, prisonerNumbers: List<String>) = CanonicalRecord(
    title = CanonicalTitle(),
    sex = CanonicalSex(),
    religion = CanonicalReligion(),
    ethnicity = CanonicalEthnicity(),
    aliases = listOf(),
    nationalities = listOf(),
    addresses = listOf(),
    identifiers = CanonicalIdentifiers(
      crns = listOf(),
      prisonNumbers = prisonerNumbers,
      defendantIds = listOf(defendantId.toString()),
      cids = listOf(),
      pncs = listOf(),
      cros = listOf(),
      nationalInsuranceNumbers = listOf(),
      driverLicenseNumbers = listOf(),
      arrestSummonsNumbers = listOf(),
    ),
  )
}
