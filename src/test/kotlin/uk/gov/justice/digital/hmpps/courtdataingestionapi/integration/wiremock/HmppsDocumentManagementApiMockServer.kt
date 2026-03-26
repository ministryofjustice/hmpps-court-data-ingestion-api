package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aMultipart
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.binaryEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentType
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
    hmppsDocumentManagementApi.stubUploadDocument()
    hmppsDocumentManagementApi.start()
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
  }

  fun stubUploadDocument() {
    stubFor(
      post(urlMatching("/documents/${DocumentType.HMCTS_WARRANT}/[a-z0-9A-Z|-]{36}"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(happyResponse)
            .withStatus(201),
        ),
    )
  }

  fun verifyUploadedDocument(
    didHappenXTimes: Int = 1,
    withUuid: String = "[a-z0-9A-Z|-]{36}",
    withType: DocumentType = DocumentType.HMCTS_WARRANT,
    fileWasUploaded: ByteArray = ByteArray(0),
    withMetadata: Map<String, String> = mapOf(),
  ): UUID? {
    val request = postRequestedFor(urlMatching("/documents/$withType/$withUuid"))
      .withRequestBodyPart(aMultipart("file").withBody(binaryEqualTo(fileWasUploaded)).build())
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
      "documentUuid": "e2487a03-7cf9-4a9c-85e4-1d51efd7b3f1",
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
