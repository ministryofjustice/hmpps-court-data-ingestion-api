package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDateTime

@Transactional(readOnly = true)
class CourtDataIngestionListenerIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Test
  fun `Test receiving a message from the queue not found response for core person api and all data is ingested`() {
    val event = sendSubscriptionNotification(NOT_FOUND_CORE_PERSON)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(NOT_FOUND_CORE_PERSON)!!
    assertThat(file.masterDefendantId).isEqualTo(NOT_FOUND_CORE_PERSON)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonDocumentId).isEqualTo(PRISON_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.identifiedAt).isNull()
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.NO_CORE_PERSON)
    assertThat(file.courtDocumentCases.size).isEqualTo(1)
    assertThat(file.courtDocumentCases[0].caseReference).isEqualTo(event.cases[0].urn)
    // 16:00 UTC in June is 17:00 BST: pin the converted value
    assertThat(file.documentGeneratedTimestamp).isEqualTo(LocalDateTime.of(2026, 6, 12, 17, 0))
    assertThat(file.prisonEmailAddress).isEqualTo(event.prisonEmailAddress)
    assertThat(file.eventType).isEqualTo(HmctsEventType.PRISON_COURT_REGISTER_GENERATED)
    assertThat(file.courtDocumentType).isEqualTo(CourtDocumentType.PRISON_COURT_REGISTER)
    assertThat(file.hmctsCourtHearingId).isNotNull
  }

  @Test
  fun `Test receiving a message from the queue no prisoner ids from core person api`() {
    sendSubscriptionNotification(NO_MATCHING_IDS_PERSON)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(NO_MATCHING_IDS_PERSON)!!
    assertThat(file.masterDefendantId).isEqualTo(NO_MATCHING_IDS_PERSON)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.identifiedAt).isNull()
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.NO_PRISON_NUMBER)
  }

  @Test
  fun `Test receiving a message from the queue with matching prisoner numbers from core person api`() {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!
    assertThat(file.masterDefendantId).isEqualTo(MATCHING_CORE_PERSON)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isEqualTo("ABC123")
    assertThat(file.identifiedAt).isNotNull
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MATCHED_ON_MASTER_DEFENDANT_ID)

    awaitAtMost30Secs untilCallTo {
      courtWarrantTestQueue.sqsClient.countMessagesOnQueue(courtWarrantTestQueue.queueUrl).get()
    } matches { it == 1 }
    val latestMessage: String = getLatestMessage(courtWarrantTestQueue)!!.messages()[0].body()
    assertThat(latestMessage).contains("court-document.file.received")
    assertThat(latestMessage).contains("ABC123")
    assertThat(latestMessage).contains(COURT_DOCUMENT_ID.toString())
    assertThat(latestMessage).contains(PRISON_DOCUMENT_ID.toString())

    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyUploadedDocument(
      1,
      fileWasUploaded = ClassPathResource("test.txt").contentAsByteArray,
      withMetadata = mapOf(
        "source" to "court-data-ingestion-api",
        "status" to "LIVE",
      ),
      withFilename = "test.txt",
    )
  }

  @Test
  fun `Test receiving a message for a person with aliases`() {
    sendSubscriptionNotification(MATCHING_CORE_ALIASES)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_ALIASES)!!
    assertThat(file.masterDefendantId).isEqualTo(MATCHING_CORE_ALIASES)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.identifiedAt).isNull()
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MULTIPLE_PRISON_NUMBERS)
  }
}
