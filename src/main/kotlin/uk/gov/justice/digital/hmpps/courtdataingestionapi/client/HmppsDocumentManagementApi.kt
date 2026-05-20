package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ResponseUtils.rethrowAnyHttpErrorWithContext
import java.util.UUID

@Component
class HmppsDocumentManagementApi(@Qualifier("hmppsDocumentManagementApiWebClient") private val webClient: WebClient) {

  fun uploadDocument(
    documentType: DocumentApiType,
    file: HmctsFile,
    metadata: Map<String, String> = mapOf(),
  ): Document {
    log.info("Uploading document: $file")
    val documentUuid = UUID.randomUUID().toString()
    val prisonDocument = webClient.post()
      .uri("/documents/$documentType/$documentUuid")
      .header("Service-Name", "court-data-ingestion-api")
      .bodyValue(
        MultipartBodyBuilder().apply {
          part("file", ByteArrayResource(file.bytes), MediaType.valueOf(file.contentType)).filename(file.originalFilename)
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

    log.info("Uploaded document: $prisonDocument")
    return prisonDocument
  }

  fun updateMetadata(documentId: UUID, metadata: Map<String, String> = mapOf()): Document = webClient
    .put()
    .uri("/documents/$documentId/metadata")
    .header("Service-Name", "court-data-ingestion-api")
    .bodyValue(metadata)
    .retrieve()
    .bodyToMono(Document::class.java)
    .block()!!

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
