package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentFindByUuidsRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ResponseUtils.rethrowAnyHttpErrorWithContext
import java.util.UUID

@Component
class HmppsDocumentManagementApi(
  @Qualifier("hmppsDocumentManagementApiWebClient") private val webClient: WebClient,
  @param:Value("\${spring.application.name}") private val appName: String,
) {

  fun uploadDocument(
    documentType: DocumentApiType,
    file: HmctsFile,
    metadata: Map<String, String> = mapOf(),
  ): Document {
    log.info("Uploading document: ${file.originalFilename}")
    val documentUuid = UUID.randomUUID().toString()

    val contentType = file.contentType?.takeIf { it.isNotBlank() }
      ?: MediaType.APPLICATION_OCTET_STREAM_VALUE

    val prisonDocument = webClient.post()
      .uri("/documents/$documentType/$documentUuid")
      .header("Service-Name", appName)
      .header("Username", SYSTEM_USERNAME)
      .bodyValue(
        MultipartBodyBuilder().apply {
          part("file", ByteArrayResource(file.bytes), MediaType.valueOf(contentType)).filename(file.originalFilename)
          part("metadata", metadata)
        }.build(),
      )
      .accept(MediaType.APPLICATION_JSON)
      .retrieve()
      .rethrowAnyHttpErrorWithContext { response, body ->
        "Error during uploading document (UUID=$documentUuid, StatusCode=${
          response.statusCode().value()
        }, Response=$body)"
      }
      .bodyToMono(Document::class.java)
      .block() ?: error("Error during uploading document (UUID=$documentUuid)")

    log.info("Uploaded document: ${prisonDocument.filename}")
    return prisonDocument
  }

  fun updateMetadata(documentId: UUID, metadata: Map<String, String> = mapOf()): Document = webClient
    .put()
    .uri("/documents/$documentId/metadata")
    .header("Service-Name", appName)
    .header("Username", SYSTEM_USERNAME)
    .bodyValue(metadata)
    .retrieve()
    .bodyToMono(Document::class.java)
    .block()!!

  fun getDocument(documentId: UUID): Document = webClient
    .get()
    .uri("/documents/$documentId")
    .header("Service-Name", appName)
    .header("Username", SYSTEM_USERNAME)
    .accept(MediaType.APPLICATION_JSON)
    .retrieve()
    .rethrowAnyHttpErrorWithContext { response, body ->
      "Error fetching document (UUID=$documentId, StatusCode=${response.statusCode().value()}, Response=$body)"
    }
    .bodyToMono(Document::class.java)
    .block()
    ?: error("No document returned for $documentId")

  fun mergeMetadata(documentId: UUID, updates: Map<String, String>): Document {
    if (updates.isEmpty()) return getDocument(documentId)
    return updateMetadata(documentId, getDocument(documentId).metadata + updates)
  }

  fun setFileContentHash(documentId: UUID, fileContentHash: String) {
    webClient.put()
      .uri("/documents/$documentId/file-content-hash")
      .header("Service-Name", appName)
      .header("Username", SYSTEM_USERNAME)
      .bodyValue(mapOf("fileContentHash" to fileContentHash))
      .retrieve()
      .rethrowAnyHttpErrorWithContext { response, body ->
        "Error setting file content hash (UUID=$documentId, StatusCode=${response.statusCode().value()}, Response=$body)"
      }
      .toBodilessEntity()
      .block()
  }

  fun setDuplicateOf(documentId: UUID, duplicateOf: UUID) {
    webClient.put()
      .uri("/documents/$documentId/duplicate-of")
      .header("Service-Name", appName)
      .header("Username", SYSTEM_USERNAME)
      .bodyValue(mapOf("duplicateOf" to duplicateOf))
      .retrieve()
      .rethrowAnyHttpErrorWithContext { response, body ->
        "Error setting duplicateOf (UUID=$documentId, StatusCode=${response.statusCode().value()}, Response=$body)"
      }
      .toBodilessEntity()
      .block()
  }

  fun downloadFile(documentId: UUID): ByteArray = webClient
    .get()
    .uri("/documents/$documentId/file")
    .header("Service-Name", appName)
    .header("Username", SYSTEM_USERNAME)
    .accept(MediaType.ALL)
    .retrieve()
    .rethrowAnyHttpErrorWithContext { response, body ->
      "Error downloading document file (UUID=$documentId, StatusCode=${response.statusCode().value()}, Response=$body)"
    }
    .bodyToMono(ByteArray::class.java)
    .block()
    ?: error("No file bytes returned for document $documentId")

  fun findByDocumentUuids(documentFindRequest: DocumentFindByUuidsRequest): Collection<Document> = webClient.post()
    .uri("/documents/")
    .header("Service-Name", appName)
    .header("Username", SYSTEM_USERNAME)
    .accept(MediaType.APPLICATION_JSON)
    .bodyValue(documentFindRequest)
    .retrieve()
    .rethrowAnyHttpErrorWithContext { response, body ->
      "Error whilst finding documents by UUIDs (documentUuids=[${documentFindRequest.documentUuids.joinToString { it.toString() }}], StatusCode=${response.statusCode().value()}, Response=$body)"
    }
    .bodyToMono<Collection<Document>>()
    .block()
    ?: error("No documents returned")

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val SYSTEM_USERNAME = "hmcts-getcourtdata"
  }
}
