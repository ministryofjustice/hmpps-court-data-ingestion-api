package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CorePersonApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtDefendantApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.DefendantDetails
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtCaseDefendantRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtCaseDefendantService
import java.time.LocalDate
import java.util.UUID

class DefendantResolutionIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var courtCaseDefendantRepository: CourtCaseDefendantRepository

  @Autowired
  private lateinit var courtCaseDefendantService: CourtCaseDefendantService

  private val dob = LocalDate.of(1990, 6, 1)

  @Test
  fun `resolves the person on defendant_id when the store yields a single defendant`() {
    val masterDefendantId = UUID.randomUUID()
    val defendantId = UUID.randomUUID()
    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
      CASE_REFERENCE,
      listOf(DefendantDetails(defendantId, masterDefendantId, "Some One", dob)),
    )
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, listOf("RES001"))

    sendSubscriptionNotification(masterDefendantId)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(masterDefendantId)!!
    assertThat(file.prisonerNumber).isEqualTo("RES001")
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_DEFENDANT_ID)

    val stored = courtCaseDefendantRepository.findAllByMasterDefendantIdAndCaseReference(masterDefendantId, CASE_REFERENCE)
    assertThat(stored.first().defendantId).isEqualTo(defendantId)
  }

  @Test
  fun `falls back to the master id when no defendant resolves`() {
    val masterDefendantId = UUID.randomUUID()
    // defendant api returns no defendants (default stub)
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(masterDefendantId, listOf("RES002"))

    sendSubscriptionNotification(masterDefendantId)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(masterDefendantId)!!
    assertThat(file.prisonerNumber).isEqualTo("RES002")
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID)
  }

  @Test
  fun `uses the store and does not call HMCTS when the master and case are already known`() {
    val masterDefendantId = UUID.randomUUID()
    val defendantId = UUID.randomUUID()
    courtCaseDefendantService.upsert(defendantId, CASE_REFERENCE, masterDefendantId, "Some One", dob)
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, listOf("RES003"))

    sendSubscriptionNotification(masterDefendantId)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(masterDefendantId)!!
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_DEFENDANT_ID)

    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.verify(
      0,
      getRequestedFor(urlPathMatching("/defendants/cases/.*")),
    )
  }
}
