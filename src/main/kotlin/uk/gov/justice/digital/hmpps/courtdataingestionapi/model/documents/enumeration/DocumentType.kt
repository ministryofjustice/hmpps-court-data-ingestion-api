package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.enumeration

enum class DocumentType(
  val description: String,
) {
  HMCTS_WARRANT(
    description = "Warrants for Remand and Sentencing",
  ),
  TRIAL_RECORD_SHEET(
    description = "Trial record sheet of a Sentence",
  ),
  INDICTMENT(
    description = "Indictment document of a Sentence",
  ),
  PRISON_COURT_REGISTER(
    description = "Prison court register",
  ),
  BAIL_ORDER(
    description = "Bail order",
  ),
  SUSPENDED_IMPRISONMENT_ORDER(
    description = "Suspended imprisonment order",
  ),
  NOTICE_OF_DISCONTINUANCE(
    description = "Notice of discontinuance",
  ),
  COMMUNITY_ORDER(
    description = "Community order",
  ),
}
