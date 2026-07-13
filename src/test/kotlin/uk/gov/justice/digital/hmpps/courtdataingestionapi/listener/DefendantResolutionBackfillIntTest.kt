package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CorePersonApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtCaseDefendantService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.DefendantMatchingService
import java.time.LocalDate
import java.util.UUID

class DefendantResolutionBackfillIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var defendantMatchingService: DefendantMatchingService

  @Autowired
  private lateinit var courtCaseDefendantService: CourtCaseDefendantService

  private val dob = LocalDate.of(1990, 6, 1)

  @Test
  fun `defendant resolution matches a previously unmatched document once the store resolves it`() {
    val masterDefendantId = UUID.randomUUID()
    val defendantId = UUID.randomUUID()

    // 1. ingest with nothing to resolve on: CPR has no record for the master, so it lands unmatched
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePersonNotFound(masterDefendantId)
    sendSubscriptionNotification(masterDefendantId)

    val before = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(masterDefendantId)!!
    assertThat(before.prisonerNumber).isNull()
    assertThat(before.matchOutcome).isEqualTo(MatchOutcome.NO_CORE_PERSON)

    // 2. the store now resolves (master, case) -> defendant, and CPR knows that defendant
    courtCaseDefendantService.upsert(defendantId, CASE_REFERENCE, masterDefendantId, "Some One", dob)
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, listOf("RES900"))

    // 3. defendant resolution by master (matches every unmatched document for the person in one resolution)
    val matched = defendantMatchingService.resolveDefendantForMasterDefendant(masterDefendantId)

    // 4. it now matches, on the defendant id
    assertThat(matched).isEqualTo(1)
    val after = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(masterDefendantId)!!
    assertThat(after.prisonerNumber).isEqualTo("RES900")
    assertThat(after.identifiedAt).isNotNull
    assertThat(after.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_DEFENDANT_ID)
  }

  @Test
  fun `defendant resolution leaves an already-matched document alone`() {
    val masterDefendantId = UUID.randomUUID()
    val defendantId = UUID.randomUUID()
    courtCaseDefendantService.upsert(defendantId, CASE_REFERENCE, masterDefendantId, "Some One", dob)
    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePerson(defendantId, listOf("RES901"))
    sendSubscriptionNotification(masterDefendantId)

    val doc = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(masterDefendantId)!!
    assertThat(doc.prisonerNumber).isEqualTo("RES901")

    val matched = defendantMatchingService.resolveDefendantForMasterDefendant(masterDefendantId)

    assertThat(matched).isEqualTo(0)
  }
}
