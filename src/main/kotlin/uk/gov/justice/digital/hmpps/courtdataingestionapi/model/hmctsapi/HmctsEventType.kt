package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi

import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType

@Suppress("ktlint:standard:enum-entry-name-case")
enum class HmctsEventType(
  val documentType: CourtDocumentType,
) {
  PRISON_COURT_REGISTER_GENERATED(CourtDocumentType.PRISON_COURT_REGISTER),

  /* Sentencing Warrants */
  WEE_CustodialSentence(CourtDocumentType.SENTENCING_WARRANT),
  WEE_CustodialSentenceWitness(CourtDocumentType.SENTENCING_WARRANT),
  WEE_DetentionInYouthDetentionAccommodationBreach(CourtDocumentType.SENTENCING_WARRANT),
  OPE_SupervisionOnBreachOfDetentionAndTraining(CourtDocumentType.SENTENCING_WARRANT),
  OXE_DetentionAndTraining(CourtDocumentType.SENTENCING_WARRANT),

  /* Remand Warrants */
  WEE_Remand(CourtDocumentType.REMAND_WARRANT),
  WEE_RemandAfterBailAppealByProsecutor(CourtDocumentType.REMAND_WARRANT),
  WEE_CommittalToCrownCourtForSentence(CourtDocumentType.REMAND_WARRANT),
  WEE_CommittalToCrownCourtForConsiderationOfTheQuestionOfBailOnAChargeOfMurder(CourtDocumentType.REMAND_WARRANT),
  WEE_CommittalToCrownCourtAuthorityToHoldInYouthDetentionAccommodation(CourtDocumentType.REMAND_WARRANT),
  WEE_SendingToCrownCourtForTrial(CourtDocumentType.REMAND_WARRANT),

  /* Unknown common platform documents. */
  WEE_SendingToCrownCourtAuthorityToHoldInYouthDetentionAccommodation(CourtDocumentType.COMMON_PLATFORM_DOCUMENT),
  WEE_CommitmentPendingTransferToServiceCustody(CourtDocumentType.COMMON_PLATFORM_DOCUMENT),
  WEE_NonPaymentOfMoneyOwedCivilDebt(CourtDocumentType.COMMON_PLATFORM_DOCUMENT),
  WEE_Detention(CourtDocumentType.COMMON_PLATFORM_DOCUMENT),
  WEE_InjunctionDetention(CourtDocumentType.COMMON_PLATFORM_DOCUMENT),
  WEE_ExtraditionSupplementToCustodyWarrant(CourtDocumentType.COMMON_PLATFORM_DOCUMENT),
  OEE_BailAppealEndOfCustody(CourtDocumentType.COMMON_PLATFORM_DOCUMENT),
  OEE_MedicalRemandAdditionalDetails(CourtDocumentType.COMMON_PLATFORM_DOCUMENT),
  NEE_DetentionOnRecommendationForDeportation(CourtDocumentType.COMMON_PLATFORM_DOCUMENT),
}
