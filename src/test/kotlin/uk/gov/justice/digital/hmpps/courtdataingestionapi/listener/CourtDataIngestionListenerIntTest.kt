package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmppsDocumentManagementApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.ZoneId

@Transactional(readOnly = true)
class CourtDataIngestionListenerIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Test
  fun `Test receiving a message from the queue not found response for core person api and all data is ingested`() {
    val event = sendSubscriptionNotification(NOT_FOUND_CORE_PERSON)

    val file = courtDocumentRepository.findFirstByDefendantIdOrderByIngestionAtDesc(NOT_FOUND_CORE_PERSON)!!
    assertThat(file.defendantId).isEqualTo(NOT_FOUND_CORE_PERSON)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonDocumentId).isEqualTo(PRISON_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.identifiedAt).isNull()
    assertThat(file.courtDocumentCases.size).isEqualTo(1)
    assertThat(file.courtDocumentCases[0].caseReference).isEqualTo(event.cases[0].urn)
    assertThat(file.documentGeneratedTimestamp)
      .isEqualTo(event.documentGeneratedTimestamp.withZoneSameInstant(ZoneId.of("Europe/London")).toLocalDateTime())
    assertThat(file.prisonEmailAddress).isEqualTo(event.prisonEmailAddress)
    assertThat(file.eventType).isEqualTo(HmctsEventType.PRISON_COURT_REGISTER_GENERATED)
    assertThat(file.courtDocumentType).isEqualTo(CourtDocumentType.PRISON_COURT_REGISTER)
    assertThat(file.hmctsCourtHearingId).isNotNull
  }

  @Test
  fun `Test receiving a message from the queue no prisoner ids from core person api`() {
    sendSubscriptionNotification(NO_MATCHING_IDS_PERSON)

    val file = courtDocumentRepository.findFirstByDefendantIdOrderByIngestionAtDesc(NO_MATCHING_IDS_PERSON)!!
    assertThat(file.defendantId).isEqualTo(NO_MATCHING_IDS_PERSON)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.identifiedAt).isNull()
  }

  @Test
  fun `Test receiving a message from the queue with matching prisoner numbers from core person api`() {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    val file = courtDocumentRepository.findFirstByDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_PERSON)!!
    assertThat(file.defendantId).isEqualTo(MATCHING_CORE_PERSON)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isEqualTo("ABC123")
    assertThat(file.identifiedAt).isNotNull

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

    val file = courtDocumentRepository.findFirstByDefendantIdOrderByIngestionAtDesc(MATCHING_CORE_ALIASES)!!
    assertThat(file.defendantId).isEqualTo(MATCHING_CORE_ALIASES)
    assertThat(file.hmctsCourtDocumentId).isEqualTo(COURT_DOCUMENT_ID)
    assertThat(file.prisonerNumber).isNull()
    assertThat(file.identifiedAt).isNull()
  }
}
