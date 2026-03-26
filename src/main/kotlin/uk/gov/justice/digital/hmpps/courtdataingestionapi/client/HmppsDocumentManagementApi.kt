package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ResponseUtils.rethrowAnyHttpErrorWithContext
import java.util.UUID

@Component
class HmppsDocumentManagementApi(@Qualifier("hmppsDocumentManagementApiWebClient") private val webClient: WebClient) {

  fun uploadDocument(
    file: HmctsFile,
    metadata: Map<String, String> = mapOf(),
  ): Document {
    val documentType = DocumentType.HMCTS_WARRANT
    val documentUuid = UUID.randomUUID().toString()
    return webClient.post()
      .uri("/documents/$documentType/$documentUuid")
      .header("Service-Name", "court-data-ingestion-api")
      .bodyValue(
        MultipartBodyBuilder().apply {
          part("file", ByteArrayResource(file.bytes), MediaType.valueOf(file.contentType)).filename(file.name)
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
  }
}
