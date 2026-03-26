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
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.WarrantFileRepository
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue

@Transactional(readOnly = true)
class CourtDataIngestionListenerIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Autowired
  private lateinit var repository: WarrantFileRepository

  @Test
  fun `Test receiving a message from the queue not found response for core person api and all data is ingested`() {
    val event = sendSubscriptionNotification(NOT_FOUND_CORE_PERSON)

    val file = repository.findFirstByDefendantId(NOT_FOUND_CORE_PERSON)!!
    assertThat(file.defendantId).isEqualTo(NOT_FOUND_CORE_PERSON)
    assertThat(file.externalFileId).isEqualTo(FILE_ID)
    assertThat(file.identifiedWarrantFiles.size).isEqualTo(0)
    assertThat(file.warrantFileCases.size).isEqualTo(2)
    assertThat(file.warrantFileCases[0].caseReference).isEqualTo(event.cases[0].urn)
    assertThat(file.warrantFileCases[1].caseReference).isEqualTo(event.cases[1].urn)
    assertThat(file.defendantName).isEqualTo(event.defendantName)
    assertThat(file.defendantDateOfBirth).isEqualTo(event.defendantDateOfBirth)
    assertThat(file.documentGeneratedTimestamp).isEqualTo(event.documentGeneratedTimestamp)
    assertThat(file.prisonEmailAddress).isEqualTo(event.prisonEmailAddress)
  }

  @Test
  fun `Test receiving a message from the queue no prisoner ids from core person api`() {
    sendSubscriptionNotification(NO_MATCHING_IDS_PERSON)

    val file = repository.findFirstByDefendantId(NO_MATCHING_IDS_PERSON)!!
    assertThat(file.defendantId).isEqualTo(NO_MATCHING_IDS_PERSON)
    assertThat(file.externalFileId).isEqualTo(FILE_ID)
    assertThat(file.identifiedWarrantFiles.size).isEqualTo(0)
  }

  @Test
  fun `Test receiving a message from the queue with matching prisoner numbers from core person api`() {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)

    val file = repository.findFirstByDefendantId(MATCHING_CORE_PERSON)!!
    assertThat(file.defendantId).isEqualTo(MATCHING_CORE_PERSON)
    assertThat(file.externalFileId).isEqualTo(FILE_ID)
    assertThat(file.identifiedWarrantFiles[0].prisonerNumber).isEqualTo("ABC123")

    awaitAtMost30Secs untilCallTo {
      courtWarrantTestQueue.sqsClient.countMessagesOnQueue(courtWarrantTestQueue.queueUrl).get()
    } matches { it == 1 }
    val latestMessage: String = getLatestMessage(courtWarrantTestQueue)!!.messages()[0].body()
    assertThat(latestMessage).contains("court-warrant.file.received")
    assertThat(latestMessage).contains("ABC123")
    assertThat(latestMessage).contains(FILE_ID)

    HmppsDocumentManagementApiExtension.hmppsDocumentManagementApi.verifyUploadedDocument(
      1,
      fileWasUploaded = ClassPathResource("test.txt").contentAsByteArray,
      withMetadata = mapOf(
        "prisonerId" to "ABC123",
        "source" to "court-data-ingestion-api",
      ),

    )
  }

  @Test
  fun `Test receiving a message for a person with aliases`() {
    sendSubscriptionNotification(MATCHING_CORE_ALIASES)

    val file = repository.findFirstByDefendantId(MATCHING_CORE_ALIASES)!!
    assertThat(file.defendantId).isEqualTo(MATCHING_CORE_ALIASES)
    assertThat(file.externalFileId).isEqualTo(FILE_ID)
    assertThat(file.identifiedWarrantFiles.size).isEqualTo(0)
  }
}
