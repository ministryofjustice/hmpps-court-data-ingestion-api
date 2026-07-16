package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsSubscriptionApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository
import java.util.UUID

@Service
@Transactional
class FileService(
  private val hmctsSubscriptionApiClient: HmctsSubscriptionApiClient,
  private val subscriptionRepository: SubscriptionRepository,
  private val hmppsDocumentManagementApi: HmppsDocumentManagementApi,
  @Value("\${environment.name}")
  private val environmentName: String,
) {

  fun ingestFile(courtDocumentId: UUID, documentType: DocumentApiType): Document {
    val subscription = subscriptionRepository.findByEnvironment(environmentName)!!

    val file = hmctsSubscriptionApiClient.getFile(subscription.id, courtDocumentId)

    return hmppsDocumentManagementApi.uploadDocument(
      documentType,
      file,
      mapOf(
        "source" to HmppsDocumentManagementApi.COURT_DATA_DOCUMENT_SOURCE,
        "status" to DOCUMENT_STATUS_ACTIVE,
      ),
    )
  }

  fun setPrisonerId(prisonDocumentId: UUID, prisonerId: String): Document = hmppsDocumentManagementApi.mergeMetadata(
    prisonDocumentId,
    mapOf("prisonerId" to prisonerId),
  )

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  fun mirrorEnrichmentToDocumentStore(document: CourtDocumentEntity): MirrorOutcome {
    val contentHashOutcome = document.extractedTextSha256
      ?.takeIf { it.isNotBlank() }
      ?.let { hash ->
        runCatching { hmppsDocumentManagementApi.setFileContentHash(document.prisonDocumentId, hash) }
          .onFailure {
            log.warn(
              "Mirror: setFileContentHash failed for {} (court_document {})",
              document.prisonDocumentId,
              document.id,
              it,
            )
          }
      }

    val metadata = buildMap {
      document.deliverySource?.let { put("deliverySource", it.name) }
    }
    val metadataOutcome = if (metadata.isNotEmpty()) {
      runCatching { hmppsDocumentManagementApi.mergeMetadata(document.prisonDocumentId, metadata) }
        .onFailure {
          log.warn(
            "Mirror: mergeMetadata failed for {} (court_document {})",
            document.prisonDocumentId,
            document.id,
            it,
          )
        }
    } else {
      null
    }

    return MirrorOutcome(
      contentHashPushed = contentHashOutcome?.isSuccess ?: (document.extractedTextSha256.isNullOrBlank()),
      metadataPushed = metadataOutcome?.isSuccess ?: true,
      contentHashError = contentHashOutcome?.exceptionOrNull(),
      metadataError = metadataOutcome?.exceptionOrNull(),
    )
  }

  data class MirrorOutcome(
    val contentHashPushed: Boolean,
    val metadataPushed: Boolean,
    val contentHashError: Throwable? = null,
    val metadataError: Throwable? = null,
  ) {
    val fullySuccessful: Boolean get() = contentHashPushed && metadataPushed
  }

  companion object {
    private val log = LoggerFactory.getLogger(FileService::class.java)

    const val DOCUMENT_STATUS_ACTIVE = "ACTIVE"
  }
}
