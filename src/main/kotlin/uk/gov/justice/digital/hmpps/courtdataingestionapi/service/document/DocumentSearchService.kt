package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.document

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchByUuidsRequest

@Service
@Transactional(readOnly = true)
class DocumentSearchService(
  private val documentManagementApiClient: HmppsDocumentManagementApi,
) {

  fun search(documentSearchRequest: DocumentSearchByUuidsRequest): Collection<Document> = documentManagementApiClient.searchByDocumentUuids(documentSearchRequest)
}
