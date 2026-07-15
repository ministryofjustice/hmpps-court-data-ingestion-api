package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CorePersonApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtDefendantApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.DefendantDetails
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtCaseDefendantRepository
import java.time.LocalDate
import java.util.UUID

@TestPropertySource(properties = ["feature-toggles.defendant-resolution=false"])
class DefendantResolutionDisabledIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var courtCaseDefendantRepository: CourtCaseDefendantRepository

  private val dob = LocalDate.of(1990, 6, 1)

  @Test
  fun `matches on the notification id alone, without calling HMCTS or writing to the store`() {
    val claimedId = UUID.randomUUID()
    val someOtherDefendant = UUID.randomUUID()
    val someOtherMaster = UUID.randomUUID()

    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
      CASE_REFERENCE,
      listOf(DefendantDetails(someOtherDefendant, someOtherMaster, "Would Resolve", dob)),
    )
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(claimedId, listOf("OFF001"))

    sendSubscriptionNotification(claimedId)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(claimedId)!!

    // Matched the old way: the notification's id went straight to Core Person Record.
    assertThat(file.prisonerNumber).isEqualTo("OFF001")
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID)

    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.verify(
      0,
      getRequestedFor(urlPathMatching("/defendants/cases/.*")),
    )
    assertThat(courtCaseDefendantRepository.findById(someOtherDefendant)).isEmpty
  }

  @Test
  fun `a co-defendant stays unmatched, as it did before the feature`() {
    val claimedId = UUID.randomUUID()

    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePersonNotFound(claimedId)

    sendSubscriptionNotification(claimedId)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(claimedId)!!
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.NO_CORE_PERSON)
  }
}
