package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtDataIngestionService
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@Service
class CourtDataIngestionListener(
  private val objectMapper: ObjectMapper,
  private val courtDataIngestionService: CourtDataIngestionService,
) {

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  @SqsListener(
    "courtdataingestion",
    factory = "hmppsQueueContainerFactoryProxy",
  )
  fun onMessage(
    rawMessage: String,
  ) {
    log.debug("Received message {}", rawMessage)
    val message = objectMapper.readValue<HmctsSubscriptionRequestBody>(rawMessage)
    courtDataIngestionService.receiveMessage(message)
  }
}

data class HmctsSubscriptionRequestBody(
  val cases: List<HmctsCase>,
  val masterDefendantId: UUID,
  val defendantName: String,
  val defendantDateOfBirth: LocalDate,
  val documentId: String,
  val documentGeneratedTimestamp: LocalDateTime,
  val prisonEmailAddress: String,
)

data class HmctsCase(
  val urn: String,
)
