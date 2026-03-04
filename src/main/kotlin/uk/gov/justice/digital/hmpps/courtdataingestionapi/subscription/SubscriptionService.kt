package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsSubscriptionApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.Subscription
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.NotificationEndpoint
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.SubscriptionRequest
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository
import java.time.LocalDateTime

@Service
class SubscriptionService(
  private val subscriptionRepository: SubscriptionRepository,
  private val hmctsSubscriptionApiClient: HmctsSubscriptionApiClient,
  private val subscriptionCallbackConfig: SubscriptionCallbackConfig,
) {

  @Transactional
  fun subscribe() {
    val subscription = subscriptionRepository.findAll().firstOrNull()
    if (subscription == null) {
      val subscriptionResponse = hmctsSubscriptionApiClient.createSubscription(
        subscriptionRequest(),
        subscriptionCallbackConfig.subscriptionKey,
      )
      subscriptionRepository.save(
        Subscription(
          id = subscriptionResponse.clientSubscriptionId,
        ),
      )

      log.info("Subscription created")
    } else {
      hmctsSubscriptionApiClient.updateSubscription(
        subscriptionRequest(),
        subscriptionCallbackConfig.subscriptionKey,
        subscription.id,
      )
      subscription.updatedAt = LocalDateTime.now()

      log.info("Subscription updated")
    }
  }

  fun subscriptionRequest() = SubscriptionRequest(
    notificationEndpoint = NotificationEndpoint(
      callbackUrl = subscriptionCallbackConfig.callbackUrl,
    ),
    eventTypes = listOf(
      "PRISON_COURT_REGISTER_GENERATED",
    ),
  )

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
