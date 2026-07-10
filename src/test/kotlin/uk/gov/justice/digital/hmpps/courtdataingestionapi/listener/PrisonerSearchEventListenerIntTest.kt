package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.util.UUID
import kotlin.String

@Transactional
class PrisonerSearchEventListenerIntTest : IntegrationTestBase() {

  @Test
  fun `Test previously ingested warrant file matches once prisoner is created in NOMIS`() {
    // Send a notification which has no matching prisoner id in core person.
    sendSubscriptionNotification(DEFENDANT_ID_NUMBER_WITH_MATCH_AFTER_CREATION)

    // Later a prisoner is created matching the file created above.
    sendPrisonerCreatedMessage(PRISONER_NUMBER_WITH_MATCH)

    awaitAtMost30Secs untilCallTo {
      courtWarrantTestQueue.sqsClient.countMessagesOnQueue(courtWarrantTestQueue.queueUrl).get()
    } matches { it == 1 }
    val latestMessage: String = getLatestMessage(courtWarrantTestQueue)!!.messages()[0].body()
    assertThat(latestMessage).contains("court-document.file.received")
    assertThat(latestMessage).contains(PRISONER_NUMBER_WITH_MATCH)
    assertThat(latestMessage).contains(COURT_DOCUMENT_ID.toString())
    assertThat(latestMessage).contains(PRISON_DOCUMENT_ID.toString())

    val file = courtDocumentRepository.findFirstByDefendantIdOrderByIngestionAtDesc(DEFENDANT_ID_NUMBER_WITH_MATCH_AFTER_CREATION)!!

    assertThat(file.prisonerNumber).isEqualTo(PRISONER_NUMBER_WITH_MATCH)
    assertThat(file.identifiedAt).isNotNull
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_DEFENDANT_ID)
  }

  @Test
  fun `Test previously ingested warrant file matches once prisoner is updated in NOMIS`() {
    // Send a notification which has no matching prisoner id in core person.
    sendSubscriptionNotification(DEFENDANT_ID_NUMBER_WITH_MATCH_AFTER_CREATION)

    // Later a prisoner is created matching the file created above.
    sendPrisonerUpdatedMessage(PRISONER_NUMBER_WITH_MATCH, listOf("PERSONAL_DETAILS"))

    awaitAtMost30Secs untilCallTo {
      courtWarrantTestQueue.sqsClient.countMessagesOnQueue(courtWarrantTestQueue.queueUrl).get()
    } matches { it == 1 }
    val latestMessage: String = getLatestMessage(courtWarrantTestQueue)!!.messages()[0].body()
    assertThat(latestMessage).contains("court-document.file.received")
    assertThat(latestMessage).contains(PRISONER_NUMBER_WITH_MATCH)
    assertThat(latestMessage).contains(COURT_DOCUMENT_ID.toString())
    assertThat(latestMessage).contains(PRISON_DOCUMENT_ID.toString())

    val file = courtDocumentRepository.findFirstByDefendantIdOrderByIngestionAtDesc(DEFENDANT_ID_NUMBER_WITH_MATCH_AFTER_CREATION)!!

    assertThat(file.prisonerNumber).isEqualTo(PRISONER_NUMBER_WITH_MATCH)
    assertThat(file.identifiedAt).isNotNull
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_DEFENDANT_ID)
  }

  @Test
  fun `Test no attempt to match document when prisoner updated event is not change to personal details`() {
    // Send a notification which has no matching prisoner id in core person.
    sendSubscriptionNotification(DEFENDANT_ID_NUMBER_WITH_MATCH_AFTER_CREATION)

    // Later a prisoner is created matching the file created above.
    sendPrisonerUpdatedMessage(PRISONER_NUMBER_WITH_MATCH, listOf("SENTENCE"))

    val file = courtDocumentRepository.findFirstByDefendantIdOrderByIngestionAtDesc(DEFENDANT_ID_NUMBER_WITH_MATCH_AFTER_CREATION)!!

    assertThat(file.prisonerNumber).isNull()
    assertThat(file.identifiedAt).isNull()
  }

  companion object {
    const val PRISONER_NUMBER_WITH_MATCH = "EFG456"
    val DEFENDANT_ID_NUMBER_WITH_MATCH_AFTER_CREATION = UUID.randomUUID()
  }
}
