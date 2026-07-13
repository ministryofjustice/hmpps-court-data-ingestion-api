package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsSubscriptionApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.Subscription
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.NotificationEndpoint
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.HmctsApiConfiguration
import java.time.LocalDateTime

@Service
class SubscriptionService(
  private val subscriptionRepository: SubscriptionRepository,
  private val hmctsSubscriptionApiClient: HmctsSubscriptionApiClient,
  private val hmctsApiConfiguration: HmctsApiConfiguration,
  private val secretsManagerService: SecretsManagerService,
  @Value("\${environment.name}")
  private val environmentName: String,
) {

  @Transactional
  fun subscribe() {
    if (hmctsApiConfiguration.enabled) {
      val subscription = subscriptionRepository.findByEnvironment(environmentName)
      if (subscription == null) {
        val subscriptionResponse = hmctsSubscriptionApiClient.createSubscription(
          subscriptionRequest(),
        )
        subscriptionRepository.save(
          Subscription(
            id = subscriptionResponse.clientSubscriptionId,
            environment = environmentName,
          ),
        )
        secretsManagerService.setSecretValue(subscriptionResponse.hmac.secret)

        log.info("Subscription created")
      } else if (hmctsApiConfiguration.updateSubscriptionOnStartup) {
        val subscriptionResponse = hmctsSubscriptionApiClient.updateSubscription(
          subscriptionRequest(),
          subscription.id,
        )
        subscription.updatedAt = LocalDateTime.now()

        log.info("Subscription updated")
      }
    } else {
      log.info("Subscription disabled")
    }
  }

  private fun subscriptionRequest() = SubscriptionRequest(
    notificationEndpoint = NotificationEndpoint(
      callbackUrl = hmctsApiConfiguration.callbackUrl,
    ),
    eventTypes = hmctsApiConfiguration.getEventTypesToSubscribe(),
  )

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
