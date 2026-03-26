package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsSubscriptionApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository

@Service
@Transactional
class FileService(
  private val hmctsSubscriptionApiClient: HmctsSubscriptionApiClient,
  private val subscriptionRepository: SubscriptionRepository,
  private val hmppsDocumentManagementApi: HmppsDocumentManagementApi,
) {

  fun ingestFile(externalFileId: String, prisonerId: String): Document {
    val subscription = subscriptionRepository.findAll()[0]

    val file = hmctsSubscriptionApiClient.getFile(subscription.id, externalFileId)

    return hmppsDocumentManagementApi.uploadDocument(
      file,
      mapOf(
        "prisonerId" to prisonerId,
        "source" to "court-data-ingestion-api",
      ),
    )
  }
}
