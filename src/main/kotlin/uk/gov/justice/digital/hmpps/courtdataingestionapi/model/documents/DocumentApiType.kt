package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents

enum class DocumentApiType(val visibleInDocumentList: Boolean = true) {
  PRISON_COURT_REGISTER,
  HMCTS_WARRANT,
  TRIAL_RECORD_SHEET,
  INDICTMENT,
  BAIL_ORDER,
  SUSPENDED_IMPRISONMENT_ORDER,
  NOTICE_OF_DISCONTINUANCE,
  COMMUNITY_ORDER,
  APPEAL_ORDER(visibleInDocumentList = false),
  BREACH_ORDER(visibleInDocumentList = false),
}
