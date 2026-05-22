package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aMultipart
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
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
    hmppsDocumentManagementApi.stubUpdateMetadata()
    hmppsDocumentManagementApi.stubDownloadFile()
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
        "court": "Birmingham Magistrates",
        "warrantDate": "2023-11-14"
      },
      "createdTime": "2025-06-03T13:04:03.393Z",
      "createdByServiceName": "court-data-ingestion-api",
      "createdByUsername": "AAA01U"
    }
  """.trimMargin()
}
