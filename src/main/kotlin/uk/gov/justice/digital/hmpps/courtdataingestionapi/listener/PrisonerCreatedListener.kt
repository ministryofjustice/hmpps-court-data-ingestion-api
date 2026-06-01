package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDataIngestionService

@Service
class PrisonerCreatedListener(
  private val objectMapper: ObjectMapper,
  private val courtDataIngestionService: CourtDataIngestionService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("prisonercreated", factory = "hmppsQueueContainerFactoryProxy")
  fun onDomainEvent(
    rawMessage: String,
  ) {
    log.debug("Received prisoner created message {}", rawMessage)
    val sqsMessage: SQSMessage = objectMapper.readValue(rawMessage)
    return when (sqsMessage.Type) {
      "Notification" -> {
        val event = objectMapper.readValue<HMPPSPrisonerCreatedDomainEvent>(sqsMessage.Message)
        if (event.eventType == "prisoner-offender-search.prisoner.created") {
          courtDataIngestionService.attemptToMatchForNewPrisoner(event.additionalInformation.nomsNumber)
        } else {
          throw IllegalArgumentException("Received a message I wasn't expecting: ${event.eventType}")
        }
      }

      else -> {}
    }
  }
}

@Suppress("PropertyName")
@JsonNaming(value = PropertyNamingStrategies.UpperCamelCaseStrategy::class)
data class SQSMessage(val Type: String, val Message: String, val MessageId: String? = null)

data class HMPPSPrisonerCreatedDomainEvent(
  val eventType: String,
  val additionalInformation: PrisonerCreatedAdditionalInformation,
)
data class PrisonerCreatedAdditionalInformation(
  val nomsNumber: String,
)
