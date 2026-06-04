package uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.step

import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionContext
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.IngestionEnricher

@Component
@Order(100)
class DownloadFile(
  private val documentApi: HmppsDocumentManagementApi,
) : IngestionEnricher {

  private val log = LoggerFactory.getLogger(this::class.java)

  override fun enrich(context: IngestionContext): IngestionContext {
    val documentId = context.prisonDocumentId ?: return context

    return runCatching {
      context.copy(downloadedFileBytes = documentApi.downloadFile(documentId))
    }.getOrElse {
      log.warn("File download skipped for document {}", documentId, it)
      context.copy(warnings = context.warnings + "File download failed")
    }
  }
}
