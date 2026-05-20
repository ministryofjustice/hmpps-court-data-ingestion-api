package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsSubscriptionApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
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

  fun setPrisonerId(prisonDocumentId: UUID, prisonerId: String): Document = hmppsDocumentManagementApi.updateMetadata(
    prisonDocumentId,
    mapOf(
      "prisonerId" to prisonerId,
      "source" to "court-data-ingestion-api",
      "status" to "LIVE",
    ),
  )
}
