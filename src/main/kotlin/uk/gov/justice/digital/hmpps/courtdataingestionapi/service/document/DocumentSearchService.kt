package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.document

import org.slf4j.LoggerFactory
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchResult
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.enumeration.DocumentSearchOrderBy
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.enumeration.DocumentType
import java.util.UUID

@Service
@Transactional(readOnly = true)
class DocumentSearchService(
  private val documentManagementApiClient: HmppsDocumentManagementApi,
) {

  fun searchByPrisoner(prisonerId: String): Set<UUID> {
    var page = 0
    var documents = searchByPrisoner(prisonerId, page)

    val documentUuids: Set<UUID> = documents.getDocumentUuids()

    while (documents.hasMorePages()) {
      page++
      documents = searchByPrisoner(prisonerId, page)

      documentUuids.plus(documents.getDocumentUuids())
    }

    return documentUuids
  }

  private fun searchByPrisoner(prisonerId: String, page: Int): DocumentSearchResult {
    val searchRequest = getSearchParameters(prisonerId)
    searchRequest.page = page

    val documents = documentManagementApiClient.search(searchRequest)
    log.info("Documents found: ${documents.totalResultsCount}, current page: ${documents.request.page}, showing ${documents.request.pageSize}")

    return documents
  }

  private fun getSearchParameters(prisonerId: String): DocumentSearchRequest = DocumentSearchRequest(
    DocumentType.entries,
    JsonMapper().readTree(String.format(DocumentSearchRequest.METADATA_PRISONER_ID, prisonerId)),
    0,
    DocumentSearchRequest.PAGE_SIZE,
    DocumentSearchOrderBy.CREATED_TIME,
    Sort.Direction.DESC,
    DocumentSearchRequest.CANONICAL_EXCLUDE_DUPLICATES,
  )

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
