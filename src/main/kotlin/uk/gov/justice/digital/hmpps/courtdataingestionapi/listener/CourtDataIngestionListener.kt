package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDataIngestionService
import java.util.UUID

@Service
class CourtDataIngestionListener(
  private val objectMapper: ObjectMapper,
  private val courtDataIngestionService: CourtDataIngestionService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val MESSAGE_TYPE = "court-data-ingestion.message"
  }

  @SqsListener(
    "courtdataingestion",
    factory = "hmppsQueueContainerFactoryProxy",
  )
  fun onMessage(
    rawMessage: String,
  ) {
    log.debug("Received message {}", rawMessage)
    val sqsMessage: SQSMessage = objectMapper.readValue(rawMessage)
    return when (sqsMessage.Type) {
      MESSAGE_TYPE -> {
        val message = objectMapper.readValue<InternalMessage<CourtDataIngestionEvent>>(sqsMessage.Message)
        courtDataIngestionService.receiveMessage(message.body)
      } else -> {}
    }
  }
}
data class InternalMessage<T>(
  val body: T,
)

data class CourtDataIngestionEvent(
  val defendantId: UUID,
  val fileId: String,
)

@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SQSMessage(val Type: String, val Message: String, val MessageId: String? = null, val MessageAttributes: MessageAttributes? = null)
data class MessageAttributes(val eventType: EventType)
data class EventType(val Value: String, val Type: String)
