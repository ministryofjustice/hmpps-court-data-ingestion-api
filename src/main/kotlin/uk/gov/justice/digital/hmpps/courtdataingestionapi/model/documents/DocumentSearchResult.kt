package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(
  description = "Describes the search parameters that were used to filter documents and the documents matching the supplied search parameters",
)
data class DocumentSearchResult(
  @Schema(
    description = "Describes the search parameters that were used to filter documents",
  )
  val request: DocumentSearchRequest,

  @Schema(
    description = "The documents matching the supplied search parameters. Note that documents with types that require " +
      "additional roles will have been filtered out of these results if the client does not have the required roles.",
  )
  val results: Collection<Document>,

  @Schema(
    description = "The total number of available results not limited by page size",
    example = "56",
  )
  val totalResultsCount: Long,
) {
  fun hasMorePages(): Boolean {
    val total = this.totalResultsCount
    val maxInPage = (this.request.page + 1) * this.request.pageSize

    return total > maxInPage
  }

  fun getDocumentUuids(): Set<UUID> {
    val documentUuids: MutableSet<UUID> = HashSet()

    this.results.forEach { doc ->
      documentUuids.add(doc.documentUuid)
    }
    return documentUuids
  }
}
