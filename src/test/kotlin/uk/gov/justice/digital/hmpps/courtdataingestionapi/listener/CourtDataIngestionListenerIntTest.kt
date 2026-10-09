package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.MatchOutcome
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsCourtDefendantApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubcriptionApiMockServer
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.DefendantDetails
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

@Transactional(readOnly = true)
class CourtDataIngestionListenerIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Test
  fun `Test receiving a message from the queue not found response for core person api and all data is ingested`() {
    val event = sendSubscriptionNotificationWaitForRecordToBeCreated(NOT_FOUND_CORE_PERSON)

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
    sendSubscriptionNotificationWaitForRecordToBeCreated(NO_MATCHING_IDS_PERSON)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(NO_MATCHING_IDS_PERSON)!!
    assertThat(file.masterDefendantId).isEqualTo(NO_MATCHING_IDS_PERSON)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.identifiedAt).isNull()
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.NO_PRISON_NUMBER)
  }

  @Test
  fun `Test receiving a message from the queue with matching prisoner numbers from core person api`() {
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_PERSON)

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
        "status" to "ACTIVE",
      ),
      withFilename = "test.txt",
    )
  }

  @Test
  fun `Test receiving a message for a person with aliases`() {
    sendSubscriptionNotificationWaitForRecordToBeCreated(MATCHING_CORE_ALIASES)

    val file = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_ALIASES)!!
    assertThat(file.masterDefendantId).isEqualTo(MATCHING_CORE_ALIASES)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.identifiedAt).isNull()
    assertThat(file.matchOutcome).isEqualTo(MatchOutcome.MULTIPLE_PRISON_NUMBERS)
  }

  @Test
  fun `Test uploaded document is deleted if exception from API`() {
    val masterDefendantId = UUID.randomUUID()
    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendantsError(
      CASE_REFERENCE,
    )
    sendSubscriptionNotification(masterDefendantId)
    awaitAtMost30Secs untilAsserted {
      HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyDeleteDocument()
    }
  }

  /*
   * Send two messages in order to create race condition where same defendant is attempted to insert twice with a unique constraint in db.
   * The delay in response from defendant API will ensure both threads attempt to insert the defendant record.
   * This causes an exception when the transaction is commited.
   */
  @Test
  fun `Test uploaded document is deleted if unhandled exception rolls back transaction`() {
    val masterDefendantId = UUID.randomUUID()
    val defendantId = UUID.randomUUID()
    HmctsCourtDefendantApiExtension.hmctsCourtDefendantApi.stubDefendants(
      CASE_REFERENCE,
      listOf(DefendantDetails(defendantId, masterDefendantId)),
      delay = 1000 * 2,
    )
    val event =
      HmctsSubscriptionNotificationRequestBody(
        masterDefendantId = masterDefendantId,
        documentId = COURT_DOCUMENT_ID,
        cases = listOf(HmctsCase(CASE_REFERENCE)),
        prisonEmailAddress = PRISON_EMAIL,
        documentGeneratedTimestamp = ZonedDateTime.of(2026, 6, 12, 16, 0, 0, 0, ZoneOffset.UTC),
        eventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
        hearingId = UUID.fromString(HmctsSubcriptionApiMockServer.TEST_HMCTS_HEARING_ID),
      )
    courtDataIngestionQueue.sqsClient.sendMessageBatch(
      SendMessageBatchRequest.builder()
        .queueUrl(courtDataIngestionQueue.queueUrl)
        .entries(
          SendMessageBatchRequestEntry.builder().id(UUID.randomUUID().toString()).messageBody(TestUtil.objectMapper().writeValueAsString(event)).build(),
          SendMessageBatchRequestEntry.builder().id(UUID.randomUUID().toString()).messageBody(TestUtil.objectMapper().writeValueAsString(event)).build(),
        )
        .build(),
    )
    awaitAtMost60Secs untilAsserted {
      HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyDeleteDocument()
    }
  }
}
