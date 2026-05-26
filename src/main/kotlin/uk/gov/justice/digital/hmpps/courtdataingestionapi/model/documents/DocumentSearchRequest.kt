//package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents
//
//import io.swagger.v3.oas.annotations.media.Schema
//import jakarta.validation.constraints.Max
//import jakarta.validation.constraints.Min
//import org.springframework.data.domain.Sort.Direction
//
//
//@Schema(
//  description = "Describes the search parameters to use to filter documents. Document type or metadata criteria " +
//    "must be supplied.",
//)
//data class DocumentSearchRequest(
//  @Schema(
//    description = "The types or categories of the document within HMPPS",
//    example = "[HMCTS_WARRANT]",
//    defaultValue = "[HMCTS_WARRANT]"
//  )
//  val documentTypes: List<DocumentType>?,
//
//  @Schema(
//    description = "The requested page of search results. Starts from 0",
//    example = "5",
//    defaultValue = "0",
//  )
//  @field:Min(0, message = "Page must be 0 or greater.")
//  val page: Int = 0,
//
//  @Schema(
//    description = "The number of results to return per page",
//    example = "25",
//    defaultValue = "10",
//    minimum = "1",
//    maximum = "100",
//  )
//  @field:Min(0, message = "Page must be 0 or greater.")
//  @field:Max(100, message = "Page must be 100 or greater.")
//  val pageSize: Int = 20,
//
//  @Schema(
//    description = "The property to order the search results by",
//    example = "CREATED_TIME",
//    defaultValue = "CREATED_TIME",
//  )
//  val orderBy: DocumentSearchOrderBy = DocumentSearchOrderBy.CREATED_TIME,
//
//  @Schema(
//    description = "The sort direction to use when ordering search results",
//    example = "ASC",
//    defaultValue = "DESC",
//  )
//  val orderByDirection: Direction = Direction.DESC,
//)
//
//enum class DocumentSearchOrderBy(
//  val property: String,
//) {
//  EVENT_TYPE("eventType"),
//  CREATED_TIME("ingestionAt"),
//}
