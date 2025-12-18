package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.WarrantFileRepository
import java.util.UUID

class CourtDataIngestionIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var mapper: ObjectMapper

  @Autowired
  private lateinit var repository: WarrantFileRepository

  @Test
  fun `Test receiving a message from the queue not found response for core person api`() {
    sendMessage(NOT_FOUND_CORE_PERSON)

    awaitAtMost30Secs untilCallTo {
      val files = repository.findAll()

      assertThat(files.size).isEqualTo(1)
      assertThat(files[0].defendantId).isEqualTo(NOT_FOUND_CORE_PERSON)
      assertThat(files[0].externalFileId).isEqualTo(FILE_ID)
      assertThat(files[0].identifiedWarrantFiles.size).isEqualTo(0)
    }
  }

  @Test
  fun `Test receiving a message from the queue no prisoner ids from core person api`() {
    sendMessage(NO_MATCHING_IDS_PERSON)

    awaitAtMost30Secs untilCallTo {
      val files = repository.findAll()

      assertThat(files.size).isEqualTo(1)
      assertThat(files[0].defendantId).isEqualTo(NO_MATCHING_IDS_PERSON)
      assertThat(files[0].externalFileId).isEqualTo(FILE_ID)
      assertThat(files[0].identifiedWarrantFiles.size).isEqualTo(0)
    }
  }

  @Test
  fun `Test receiving a message from the queue with matching prisoner numbers from core person api`() {
    sendMessage(MATCHING_CORE_PERSON)

    awaitAtMost30Secs untilCallTo {
      val files = repository.findAll()

      assertThat(files.size).isEqualTo(1)
      assertThat(files[0].defendantId).isEqualTo(MATCHING_CORE_PERSON)
      assertThat(files[0].externalFileId).isEqualTo(FILE_ID)
      assertThat(files[0].identifiedWarrantFiles.size).isEqualTo(2)
      assertThat(files[0].identifiedWarrantFiles[0].prisonerNumber).isEqualTo("ABC123")
      assertThat(files[0].identifiedWarrantFiles[1].prisonerNumber).isEqualTo("XYZ987")
    }
  }

  private fun sendMessage(defendantId: UUID) {
    val event = SQSMessage(
      Type = CourtDataIngestionListener.MESSAGE_TYPE,
      Message = mapper.writeValueAsString(
        InternalMessage(
          CourtDataIngestionEvent(
            defendantId = defendantId,
            fileId = "file-123",
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
  }

  companion object {
    const val FILE_ID = "file-123"
    val NOT_FOUND_CORE_PERSON = UUID.randomUUID()
    val NO_MATCHING_IDS_PERSON = UUID.randomUUID()
    val MATCHING_CORE_PERSON = UUID.randomUUID()
    val MATCHING_PRISONER_NUMBERS = listOf("ABC123", "XYZ987")
  }
}
