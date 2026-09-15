package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CorePersonApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtDefendantApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsPcrApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.DefendantDetails
import java.util.UUID

@TestPropertySource(
  properties = [
    "feature-toggles.offence-data-enabled=true",
  ],
)
class HearingDataBackfillIntTest : IntegrationTestBase() {

  @Test
  @Transactional(readOnly = true)
  fun `Cdia documents are backfilled`() {
    // Setup
    val defendantId = UUID.randomUUID()
    val hearingId = UUID.randomUUID()
    val masterDefendantId = UUID.randomUUID()
    val prisonerNumber = "QWERT123"
    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
      CASE_REFERENCE,
      listOf(
        DefendantDetails(defendantId, masterDefendantId),
      ),
    )
    HmctsPcrApiExtension.hmctsPcrApiMockServer.stubGetPcr(
      CASE_REFERENCE,
      hearingId,
      defendantId,
      "[]",
    )
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, listOf(prisonerNumber))
    // Ingest data where hearing is not loaded
    sendSubscriptionNotification(masterDefendantId, hearingId = hearingId)

    val document = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(masterDefendantId)!!
    val hearing = document.courtHearing
    assertThat(hearing).isNull()

    // Now stub hearing.
    HmctsPcrApiExtension.hmctsPcrApiMockServer.stubGetPcr(
      CASE_REFERENCE,
      hearingId,
      defendantId,
    )

    // Run
    runBackfill("hearing-data-backfill")

    // Check results
    val apiHearing = getCourtHearing(prisonerNumber, hearingId.toString())
    assertThat(apiHearing).isNotNull
  }
}
