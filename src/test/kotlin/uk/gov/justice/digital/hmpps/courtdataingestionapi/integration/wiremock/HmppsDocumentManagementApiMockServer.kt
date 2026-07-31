package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aMultipart
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.notContaining
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.common.ContentTypes.APPLICATION_JSON
import com.github.tomakehurst.wiremock.common.ContentTypes.CONTENT_TYPE
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import java.util.UUID

class HmppsDocumentManagementApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val hmppsDocumentManagementApi = HmppsDocumentManagementApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    hmppsDocumentManagementApi.start()
    hmppsDocumentManagementApi.stubUploadDocument()
    hmppsDocumentManagementApi.stubGetDocument()
    hmppsDocumentManagementApi.stubUpdateMetadata()
    hmppsDocumentManagementApi.stubMergeMetadata(IntegrationTestBase.PRISON_DOCUMENT_ID)
    hmppsDocumentManagementApi.stubSetFileContentHash()
    hmppsDocumentManagementApi.stubDownloadFile()
    hmppsDocumentManagementApi.stubDocumentFindByUuids()
  }

  override fun beforeEach(context: ExtensionContext) {
    hmppsDocumentManagementApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    hmppsDocumentManagementApi.stop()
  }
}

class HmppsDocumentManagementApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8334
    private const val SERVICE_NAME = "hmpps-court-data-ingestion-api"
    private const val USERNAME = "hmcts-getcourtdata"
  }

  fun stubUploadDocument() {
    stubFor(
      post(urlMatching("/documents/${DocumentApiType.PRISON_COURT_REGISTER}/[a-z0-9A-Z|-]{36}"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(happyResponse)
            .withStatus(201),
        ),
    )
  }

  fun stubGetDocument() {
    stubFor(
      get(urlPathMatching("/documents/[a-zA-Z0-9\\-]{36}"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(happyResponse)
            .withStatus(200),
        ),
    )
  }

  fun stubUpdateMetadata() {
    stubFor(
      put(urlEqualTo("/documents/${IntegrationTestBase.PRISON_DOCUMENT_ID}/metadata"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(happyResponse)
            .withStatus(200),
        ),
    )
  }

  fun stubMergeMetadataError() {
    stubFor(
      patch(urlEqualTo("/documents/${IntegrationTestBase.PRISON_DOCUMENT_ID}/metadata"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .withRequestBody(notContaining("prisonerId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(happyResponse)
            .withStatus(400),
        ),
    )
  }

  fun stubSetFileContentHash() {
    stubFor(
      put(urlPathMatching("/documents/[a-zA-Z0-9\\-]{36}/file-content-hash"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(happyResponse)
            .withStatus(200),
        ),
    )
  }

  fun stubSetFileContentHashError() {
    stubFor(
      put(urlPathMatching("/documents/[a-zA-Z0-9\\-]{36}/file-content-hash"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(happyResponse)
            .withStatus(400),
        ),
    )
  }

  fun stubDownloadFile(
    fileBytes: ByteArray = "test file contents".toByteArray(),
    contentType: String = "application/pdf",
  ) {
    stubFor(
      get(urlPathMatching("/documents/[a-zA-Z0-9\\-]{36}/file"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", contentType)
            .withHeader("Content-Disposition", "attachment; filename=\"test.pdf\"")
            .withBody(fileBytes),
        ),
    )
  }

  fun stubDocumentFindByUuids() {
    stubFor(
      post(urlPathMatching("/documents"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON)
            .withBody(happyDocumentFindByUuids)
            .withStatus(200),
        ),
    )
  }

  fun stubSearch(page: Int, response: String) {
    stubFor(
      post(urlPathMatching("/documents/search"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .withRequestBody(matchingJsonPath("$.page", equalTo(page.toString())))
        .willReturn(
          aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON)
            .withBody(response)
            .withStatus(200),
        ),
    )
  }

  fun stubMergeMetadata(documentId: UUID) {
    stubFor(
      patch(urlEqualTo("/documents/$documentId/metadata"))
        .withHeader("Service-Name", equalTo(SERVICE_NAME))
        .withHeader("Username", equalTo(USERNAME))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(happyResponse)
            .withStatus(200),
        ),
    )
  }

  fun verifyMergeMetadata(
    didHappenXTimes: Int = 1,
    withUuid: String = "[a-z0-9A-Z|-]{36}",
    withMetadata: Map<String, String>? = null,
  ) {
    var request = patchRequestedFor(urlMatching("/documents/$withUuid/metadata"))

    if (withMetadata != null) {
      request = request.withRequestBody(
        equalToJson(
          TestUtil.objectMapper().writeValueAsString(withMetadata),
        ),
      )
    }

    verify(didHappenXTimes, request)
  }

  fun verifyUploadedDocument(
    didHappenXTimes: Int = 1,
    withUuid: String = "[a-z0-9A-Z|-]{36}",
    withType: DocumentApiType = DocumentApiType.PRISON_COURT_REGISTER,
    fileWasUploaded: ByteArray = ByteArray(0),
    withMetadata: Map<String, String> = mapOf(),
    withFilename: String = "file.txt",
  ): UUID? {
    val request = postRequestedFor(urlMatching("/documents/$withType/$withUuid"))
      .withRequestBodyPart(aMultipart("file").withBody(binaryEqualTo(fileWasUploaded)).build())
      .withRequestBodyPart(aMultipart("file").withFileName(withFilename).build())
      .withRequestBodyPart(aMultipart("metadata").withBody(equalToJson(TestUtil.objectMapper().writeValueAsString(withMetadata))).build())

    verify(didHappenXTimes, request)

    return if (didHappenXTimes > 0) {
      UUID.fromString(findAll(request).first().url.substringAfter("/documents/$withType/"))
    } else {
      null
    }
  }

  private var happyResponse = """
    {
      "documentUuid": "${IntegrationTestBase.PRISON_DOCUMENT_ID}",
      "documentType": "HMCTS_WARRANT",
      "documentFilename": "warrant_for_remand",
      "filename": "test",
      "fileExtension": "txt",
      "fileSize": 48243,
      "fileHash": "d58e3582afa99040e27b92b13c8f2280",
      "mimeType": "txt",
      "metadata": {
        "prisonCode": "KMI",
        "prisonNumber": "C3456DE",
        "status": "ACTIVE",
        "court": "Birmingham Magistrates",
        "warrantDate": "2023-11-14"
      },
      "createdTime": "2025-06-03T13:04:03.393Z",
      "createdByServiceName": "court-data-ingestion-api",
      "createdByUsername": "AAA01U"
    }
  """.trimMargin()

  var happyDocumentFindByUuids = """
    [ $happyResponse ]
  """.trimMargin()
}
