package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CorePersonApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtCaseDefendantService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate
import java.util.UUID

class PrisonerCreatedCoDefendantIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var courtCaseDefendantService: CourtCaseDefendantService

  private val dob = LocalDate.of(1990, 6, 1)

  @Test
  fun `a co-defendant's document is found by resolving CPR's defendant id back to its master`() {
    val master = UUID.randomUUID()
    val defendantId = UUID.randomUUID()
    val prisonerNumber = "CD0001"

    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePersonNotFound(master)
    sendSubscriptionNotification(master)

    courtCaseDefendantService.upsert(defendantId, CASE_REFERENCE, master, "Co Defendant", dob)

    CorePersonApiExtension.corePersonApi.stubPrisonerCorePerson(prisonerNumber, listOf(defendantId))

    sendPrisonerCreatedMessage(prisonerNumber)

    awaitAtMost30Secs untilCallTo {
      courtWarrantTestQueue.sqsClient.countMessagesOnQueue(courtWarrantTestQueue.queueUrl).get()
    } matches { it == 1 }

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(master)!!
    assertThat(file.prisonerNumber).isEqualTo(prisonerNumber)
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_DEFENDANT_ID)
  }

  @Test
  fun `a first-case defendant still matches from CPR's defendant id with nothing in the store`() {
    val sharedId = UUID.randomUUID()
    val prisonerNumber = "FB0001"

    CorePersonApiExtension.corePersonApi.stubCommonPlatformCorePersonNotFound(sharedId)
    sendSubscriptionNotification(sharedId)

    CorePersonApiExtension.corePersonApi.stubPrisonerCorePerson(prisonerNumber, listOf(sharedId))

    sendPrisonerCreatedMessage(prisonerNumber)

    awaitAtMost30Secs untilCallTo {
      courtWarrantTestQueue.sqsClient.countMessagesOnQueue(courtWarrantTestQueue.queueUrl).get()
    } matches { it == 1 }

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(sharedId)!!
    assertThat(file.prisonerNumber).isEqualTo(prisonerNumber)
  }
}
