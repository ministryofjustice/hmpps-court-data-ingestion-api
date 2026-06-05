package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsSubscriptionApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.SubscriptionCallbackConfig
import java.util.UUID

@Service
@Transactional
class FileService(
  private val hmctsSubscriptionApiClient: HmctsSubscriptionApiClient,
  private val subscriptionRepository: SubscriptionRepository,
  private val hmppsDocumentManagementApi: HmppsDocumentManagementApi,
  private val subscriptionCallbackConfig: SubscriptionCallbackConfig,
) {

  fun ingestFile(courtDocumentId: UUID, documentType: DocumentApiType): Document {
    val subscription = subscriptionRepository.findAll()[0]

    val file = hmctsSubscriptionApiClient.getFile(subscription.id, courtDocumentId, subscriptionCallbackConfig.subscriptionKey)

    return hmppsDocumentManagementApi.uploadDocument(
      documentType,
      file,
      mapOf(
        "source" to "court-data-ingestion-api",
        "status" to "LIVE",
      ),
    )
  }

  fun setPrisonerId(prisonDocumentId: UUID, prisonerId: String): Document = hmppsDocumentManagementApi.mergeMetadata(
    prisonDocumentId,
    mapOf("prisonerId" to prisonerId),
  )

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  fun stampIngestionMetadata(
    prisonDocumentId: UUID,
    deliverySource: DestinationType?,
    downloadedFileSha256: String?,
    extractedTextSha256: String?,
    duplicateOf: UUID?,
  ) {
    val isCanonical = duplicateOf == null
    val updates = buildMap {
      deliverySource?.let { put("deliverySource", it.name) }
      downloadedFileSha256?.let { put("downloadedFileSha256", it) }
      extractedTextSha256?.let { put("extractedTextSha256", it) }
      put("canonical", isCanonical.toString())
      duplicateOf?.let { put("duplicateOf", it.toString()) }
    }
    hmppsDocumentManagementApi.mergeMetadata(prisonDocumentId, updates)

    duplicateOf?.let { canonicalId ->
      hmppsDocumentManagementApi.mergeMetadata(canonicalId, mapOf("canonical" to "true"))
    }
  }
}
