package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.DefendantMatchingService

@Service
class PrisonerSearchEventListener(
  private val objectMapper: ObjectMapper,
  private val defendantMatchingService: DefendantMatchingService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener("prisonercreated", factory = "hmppsQueueContainerFactoryProxy")
  fun onDomainEvent(
    rawMessage: String,
  ) {
    val sqsMessage: SQSMessage = objectMapper.readValue(rawMessage)
    when (sqsMessage.Type) {
      "Notification" -> {
        val event = objectMapper.readValue<HMPPSPrisonerSearchEvent>(sqsMessage.Message)
        if (event.eventType == "prisoner-offender-search.prisoner.created") {
          log.debug("Received prisoner created event message {}", rawMessage)
          defendantMatchingService.attemptToMatchForNewPrisoner(event.additionalInformation.nomsNumber)
        } else if (event.eventType == "prisoner-offender-search.prisoner.updated") {
          if (event.additionalInformation.categoriesChanged.contains("PERSONAL_DETAILS")) {
            log.debug("Received prisoner updated event message {}", rawMessage)
            defendantMatchingService.attemptToMatchForNewPrisoner(event.additionalInformation.nomsNumber)
          }
        } else {
          log.debug("Received unknown message {}", rawMessage)
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

data class HMPPSPrisonerSearchEvent(
  val eventType: String,
  val additionalInformation: PrisonerSearchEventAdditionalInformation,
)

data class PrisonerSearchEventAdditionalInformation(
  val nomsNumber: String,
  val categoriesChanged: List<String> = emptyList(),
)
