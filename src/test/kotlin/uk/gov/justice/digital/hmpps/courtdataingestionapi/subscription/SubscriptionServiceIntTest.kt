package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubscriptionApiMockServer
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.SecretsManagerService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.SubscriptionService

class SubscriptionServiceIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var subscriptionService: SubscriptionService

  @Autowired
  lateinit var subscriptionRepository: SubscriptionRepository

  @Autowired
  lateinit var secretsManagerService: SecretsManagerService

  @Test
  fun `should create subscribe on startup if none exists`() {
    val subscriptions = subscriptionRepository.findAll()

    assertThat(subscriptions).hasSize(1)
    assertThat(subscriptions.first().id).isEqualTo(HmctsSubscriptionApiMockServer.TEST_SUBSCRIPTION_ID)
  }
}
