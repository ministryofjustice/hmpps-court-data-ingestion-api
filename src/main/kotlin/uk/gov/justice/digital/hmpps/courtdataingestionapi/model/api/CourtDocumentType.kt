package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api

import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentApiType

enum class CourtDocumentType(
  val documentApiType: DocumentApiType,
) {
  PRISON_COURT_REGISTER(DocumentApiType.PRISON_COURT_REGISTER),
  SENTENCING_WARRANT(DocumentApiType.HMCTS_WARRANT),
  REMAND_WARRANT(DocumentApiType.HMCTS_WARRANT),
  COMMON_PLATFORM_DOCUMENT(DocumentApiType.HMCTS_WARRANT),
}
