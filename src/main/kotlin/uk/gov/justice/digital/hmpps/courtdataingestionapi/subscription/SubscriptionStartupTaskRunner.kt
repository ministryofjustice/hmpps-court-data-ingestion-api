package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsSubscriptionApiClient.Companion.X_CORRELATION_ID_HEADER
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.StartupLock
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.StartupLockRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.SubscriptionService
import java.util.UUID

@Component
class SubscriptionStartupTaskRunner(
  private val lockRepository: StartupLockRepository,
  private val subscriptionService: SubscriptionService,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val LOCK_NAME = "subscription_startup"
  }

  @EventListener(ApplicationReadyEvent::class)
  @Transactional
  fun runOnStartup() {
    val xCorrelationId: UUID = UUID.randomUUID()
    MDC.put(X_CORRELATION_ID_HEADER, xCorrelationId.toString())

    try {
      lockRepository.save(
        StartupLock(
          lockName = LOCK_NAME,
        ),
      )
      log.info("Lock acquired")
    } catch (ex: DataIntegrityViolationException) {
      log.info("Another pod already holds the lock. Skipping startup task.")
      MDC.remove(X_CORRELATION_ID_HEADER)
      return
    }

    try {
      subscriptionService.subscribe(xCorrelationId)
    } finally {
      lockRepository.deleteById(LOCK_NAME)
      log.info("Lock released")
      MDC.remove(X_CORRELATION_ID_HEADER)
    }
  }
}
