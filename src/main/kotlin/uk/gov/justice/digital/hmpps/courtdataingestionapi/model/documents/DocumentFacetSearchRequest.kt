package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents

import com.fasterxml.jackson.annotation.JsonIgnore

data class DocumentFacetSearchRequest(

  val documentTypes: List<DocumentApiType>?,

  val page: Int = 0,
  val pageSize: Int = 10,

  val metadataFilters: List<MetadataFilter> = emptyList(),
  val facets: List<FacetRequest> = emptyList(),
)

data class MetadataFilter(
  val field: String,
  val operator: FilterOperator = FilterOperator.EQUALS,
  val values: List<String> = emptyList(),
) {
  val value: String
    @JsonIgnore
    get() {
      if (values.size == 1) {
        return values.first()
      } else {
        error("Expected only one value for the filter operation $operator")
      }
    }
}

enum class FilterOperator {
  EQUALS,
  NOT_EQUALS,
  IN,
  JSON_ARRAY_CONTAINS,
  EXISTS,
  NOT_EXISTS,
}

data class FacetRequest(
  val field: String,
  val type: FacetType,
  val filter: MetadataFilter? = null,
)

enum class FacetType {
  VALUE,
  ARRAY,
}
