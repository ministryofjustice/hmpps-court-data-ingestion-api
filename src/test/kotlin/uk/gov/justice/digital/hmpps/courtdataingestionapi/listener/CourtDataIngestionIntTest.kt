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
  fun `Test receiving a message from the queue`() {
    val defendantId = UUID.randomUUID()
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

    awaitAtMost30Secs untilCallTo {
      val files = repository.findAll()

      assertThat(files.size).isEqualTo(1)
      assertThat(files[0].defendantId).isEqualTo(defendantId)
      assertThat(files[0].externalFileId).isEqualTo("file-123")
    }
  }
}
