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

class DefendantResolutionBehaviourIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var courtCaseDefendantRepository: CourtCaseDefendantRepository

  @Autowired
  private lateinit var courtCaseDefendantService: CourtCaseDefendantService

  private val dob = LocalDate.of(1990, 6, 1)

  @Test
  fun `co-defendant - master defendant resolves to this person's defendant id, and the prisoner matches on it`() {
    val ourMaster = UUID.randomUUID()
    val ourDefendantId = UUID.randomUUID()
    val coDefendantMaster = UUID.randomUUID()
    val coDefendantId = UUID.randomUUID()

    // The case has two defendants. Ours is not the first.
    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
      CASE_REFERENCE,
      listOf(
        DefendantDetails(coDefendantId, coDefendantMaster, "Co Defendant", dob),
        DefendantDetails(ourDefendantId, ourMaster, "Our Defendant", dob),
      ),
    )
    // CPR keys on defendantId. It knows our defendant, and would answer nothing for the master.
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(ourDefendantId, listOf("CO0001"))
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePersonNotFound(ourMaster)

    sendSubscriptionNotification(ourMaster)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(ourMaster)!!
    assertThat(file.prisonerNumber).isEqualTo("CO0001")
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_DEFENDANT_ID)

    // Both defendants on the case are stored, not just ours: we take what the source gives us.
    assertThat(courtCaseDefendantRepository.findById(ourDefendantId)).isPresent
    assertThat(courtCaseDefendantRepository.findById(coDefendantId)).isPresent
  }

  @Test
  fun `first-case defendant - the claimed master defendant is really a defendant id, and still resolves`() {
    val sharedId = UUID.randomUUID()

    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
      CASE_REFERENCE,
      listOf(DefendantDetails(sharedId, sharedId, "Single Case", dob)),
    )
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(sharedId, listOf("FC0001"))

    sendSubscriptionNotification(sharedId)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(sharedId)!!
    assertThat(file.prisonerNumber).isEqualTo("FC0001")
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_DEFENDANT_ID)
  }

  @Test
  fun `a stored co-defendant resolves from the store without calling HMCTS again`() {
    val ourMaster = UUID.randomUUID()
    val ourDefendantId = UUID.randomUUID()

    courtCaseDefendantService.upsert(ourDefendantId, CASE_REFERENCE, ourMaster, "Our Defendant", dob)
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(ourDefendantId, listOf("CA0001"))

    sendSubscriptionNotification(ourMaster)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(ourMaster)!!
    assertThat(file.prisonerNumber).isEqualTo("CA0001")
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_DEFENDANT_ID)

    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.verify(
      0,
      getRequestedFor(urlPathMatching("/defendants/cases/.*")),
    )
  }

  @Test
  fun `records NO_PRISON_NUMBER when the defendant resolves but CPR holds no prison number`() {
    val master = UUID.randomUUID()
    val defendantId = UUID.randomUUID()

    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
      CASE_REFERENCE,
      listOf(DefendantDetails(defendantId, master, "Not In Custody", dob)),
    )
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, emptyList())

    sendSubscriptionNotification(master)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(master)!!
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.NO_PRISON_NUMBER)
  }

  @Test
  fun `records MULTIPLE_PRISON_NUMBERS rather than picking one`() {
    val master = UUID.randomUUID()
    val defendantId = UUID.randomUUID()

    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
      CASE_REFERENCE,
      listOf(DefendantDetails(defendantId, master, "Ambiguous", dob)),
    )
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, listOf("AM0001", "AM0002"))

    sendSubscriptionNotification(master)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(master)!!
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MULTIPLE_PRISON_NUMBERS)
  }

  @Test
  fun `resolution does not rewrite the master on the document`() {
    val claimedMaster = UUID.randomUUID()
    val realMaster = UUID.randomUUID()
    val defendantId = UUID.randomUUID()

    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
      CASE_REFERENCE,
      listOf(
        DefendantDetails(claimedMaster, claimedMaster, "Other Defendant", dob),
        DefendantDetails(defendantId, realMaster, "Our Defendant", dob),
      ),
    )
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(claimedMaster, listOf("RW0001"))

    sendSubscriptionNotification(claimedMaster)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(claimedMaster)!!
    assertThat(file.masterDefendantId).isEqualTo(claimedMaster)
  }
}
