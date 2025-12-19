package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.WarrantFileRepository
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.util.UUID

@Transactional(readOnly = true)
class CourtDataIngestionIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Autowired
  private lateinit var repository: WarrantFileRepository

  @Test
  fun `Test receiving a message from the queue not found response for core person api`() {
    sendMessage(NOT_FOUND_CORE_PERSON)

    val file = repository.findFirstByDefendantId(NOT_FOUND_CORE_PERSON)!!
    assertThat(file.defendantId).isEqualTo(NOT_FOUND_CORE_PERSON)
    assertThat(file.externalFileId).isEqualTo(FILE_ID)
    assertThat(file.identifiedWarrantFiles.size).isEqualTo(0)
  }

  @Test
  fun `Test receiving a message from the queue no prisoner ids from core person api`() {
    sendMessage(NO_MATCHING_IDS_PERSON)

    val file = repository.findFirstByDefendantId(NO_MATCHING_IDS_PERSON)!!
    assertThat(file.defendantId).isEqualTo(NO_MATCHING_IDS_PERSON)
    assertThat(file.externalFileId).isEqualTo(FILE_ID)
    assertThat(file.identifiedWarrantFiles.size).isEqualTo(0)
  }

  @Test
  fun `Test receiving a message from the queue with matching prisoner numbers from core person api`() {
    sendMessage(MATCHING_CORE_PERSON)

    val file = repository.findFirstByDefendantId(MATCHING_CORE_PERSON)!!
    assertThat(file.defendantId).isEqualTo(MATCHING_CORE_PERSON)
    assertThat(file.externalFileId).isEqualTo(FILE_ID)
    assertThat(file.identifiedWarrantFiles[0].prisonerNumber).isEqualTo("ABC123")
    assertThat(file.identifiedWarrantFiles[1].prisonerNumber).isEqualTo("XYZ987")

    awaitAtMost30Secs untilCallTo { courtWarrantTestQueue.sqsClient.countMessagesOnQueue(courtWarrantTestQueue.queueUrl).get() } matches { it == 1 }
    val latestMessage: String = getLatestMessage(courtWarrantTestQueue)!!.messages()[0].body()
    assertThat(latestMessage).contains("court-warrant.file.received")
    assertThat(latestMessage).contains("ABC123")
    assertThat(latestMessage).contains("XYZ987")
    assertThat(latestMessage).contains(FILE_ID)
  }

  private fun sendMessage(defendantId: UUID) {
    val event = SQSMessage(
      Type = CourtDataIngestionListener.MESSAGE_TYPE,
      Message = mapper.writeValueAsString(
        InternalMessage(
          CourtDataIngestionEvent(
            defendantId = defendantId,
            fileId = FILE_ID,
          ),
        ),
      ),
      MessageId = UUID.randomUUID().toString(),
      MessageAttributes = null,
    )
    courtDataIngestionQueue.sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(courtDataIngestionQueue.queueUrl)
        .messageBody(mapper.writeValueAsString(event))
        .build(),
    )

    awaitAtMost30Secs untilCallTo {
      repository.countByDefendantId(defendantId)
    } matches { it == 1L }
  }

  companion object {
    const val FILE_ID = "file-123"
    val NOT_FOUND_CORE_PERSON = UUID.randomUUID()
    val NO_MATCHING_IDS_PERSON = UUID.randomUUID()
    val MATCHING_CORE_PERSON = UUID.randomUUID()
    val MATCHING_PRISONER_NUMBERS = listOf("ABC123", "XYZ987")
  }
}
