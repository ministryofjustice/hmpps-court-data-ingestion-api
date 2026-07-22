package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import com.google.common.util.concurrent.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription.HmctsApiConfiguration

@Component
class HmctsRateLimiter(
  private val hmctsApiConfiguration: HmctsApiConfiguration,
) {

  private val rateLimited = RateLimiter.create(hmctsApiConfiguration.rateLimit)

  fun acquire() {
    val waited = rateLimited.acquire()
    if (waited > 0.0) {
      log.warn("Rate limited: waited {} ms", Math.round(waited * 1000))
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(HmctsRateLimiter::class.java)
  }
}
