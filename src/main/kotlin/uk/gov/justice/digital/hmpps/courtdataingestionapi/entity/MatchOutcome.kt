package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

enum class MatchOutcome {
  MATCHED_ON_MASTER_DEFENDANT_ID,
  MATCHED_ON_DEFENDANT_ID,
  NO_CORE_PERSON,
  NO_PRISON_NUMBER,
  MULTIPLE_PRISON_NUMBERS,
}
