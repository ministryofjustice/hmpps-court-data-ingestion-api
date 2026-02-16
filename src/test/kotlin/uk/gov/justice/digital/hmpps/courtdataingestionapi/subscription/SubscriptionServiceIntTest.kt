package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.HmctsSubscriptionApiMockServer
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository

class SubscriptionServiceIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var subscriptionRepository: SubscriptionRepository

  @Test
  fun `should subscribe on startup`() {
    val subscriptions = subscriptionRepository.findAll()

    assertThat(subscriptions).hasSize(1)
    assertThat(subscriptions.first().id).isEqualTo(HmctsSubscriptionApiMockServer.TEST_SUBSCRIPTION_ID)
  }
}
