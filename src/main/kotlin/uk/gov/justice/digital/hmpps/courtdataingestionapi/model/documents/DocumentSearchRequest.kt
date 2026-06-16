package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Min
import org.springframework.data.domain.Sort.Direction
import tools.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.enumeration.DocumentSearchOrderBy
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.enumeration.DocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.validation.Between
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.validation.DocumentTypeOrMetadataCriteriaRequired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.validation.NoNullOrEmptyStringMetadataValues

@Schema(
  description = "Describes the search parameters to use to filter documents. Document type or metadata criteria " +
    "must be supplied.",
)
@DocumentTypeOrMetadataCriteriaRequired
data class DocumentSearchRequest(
  @Schema(
    description = "The types or categories of the document within HMPPS",
    example = "[HMCTS_WARRANT]",
  )
  val documentTypes: List<DocumentType>?,

  @Schema(
    description = "JSON structured metadata to match with document metadata. Documents will match if their metadata " +
      "contains all the supplied properties and their values e.g. prisonCode = \"KMI\" AND prisonNumber = \"A1234BC\". " +
      "Value matching is partial and case insensitive so court = \"ham magis\" will match \"Birmingham Magistrates\". " +
      "Property values must be strings and cannot be null or empty.",
    example =
    """
    {
      "prisonCode": "KMI",
      "prisonNumber": "C3456DE"
    }
    """,
  )
  @field:NoNullOrEmptyStringMetadataValues
  val metadata: JsonNode?,

  @Schema(
    description = "The requested page of search results. Starts from 0",
    example = "5",
    defaultValue = "0",
  )
  @field:Min(0, message = "Page must be 0 or greater.")
  var page: Int = 0,

  @Schema(
    description = "The number of results to return per page",
    example = "25",
    defaultValue = "10",
    minimum = "1",
    maximum = "100",
  )
  @field:Between(min = 1, max = 100, message = "Page size must be between 1 and 100.")
  val pageSize: Int = 10,

  @Schema(
    description = "The property to order the search results by",
    example = "FILESIZE",
    defaultValue = "CREATED_TIME",
  )
  val orderBy: DocumentSearchOrderBy = DocumentSearchOrderBy.CREATED_TIME,

  @Schema(
    description = "The sort direction to use when ordering search results",
    example = "ASC",
    defaultValue = "DESC",
  )
  val orderByDirection: Direction = Direction.DESC,

  @Schema(
    description = "Filter by canonical status. True returns only canonical documents (those that are not a duplicate " +
      "of another), false returns only duplicates. Low selectivity on its own, so it refines a query rather than " +
      "standing alone and must be combined with document types or metadata.",
    example = "true",
  )
  val canonical: Boolean? = null,
) {
  companion object {
    const val METADATA_PRISONER_ID: String = "{ \"prisonerId\": \"%s\" }"
    const val PAGE_SIZE: Int = 100
  }
}
