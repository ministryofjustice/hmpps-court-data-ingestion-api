package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api

class CourtDocumentView(
  val username: String,
  val type: CourtDocumentViewType = CourtDocumentViewType.DOCUMENT_VIEW,
)

enum class CourtDocumentViewType {
  DOCUMENT_VIEW,
  DOCUMENT_PROCESSED,
}
