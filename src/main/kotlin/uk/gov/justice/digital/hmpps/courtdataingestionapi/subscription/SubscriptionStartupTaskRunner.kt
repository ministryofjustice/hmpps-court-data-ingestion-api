package uk.gov.justice.digital.hmpps.courtdataingestionapi.subscription

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.StartupLock
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.StartupLockRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.SubscriptionService

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
    try {
      lockRepository.save(
        StartupLock(
          lockName = LOCK_NAME,
        ),
      )
      log.info("Lock acquired")
    } catch (ex: DataIntegrityViolationException) {
      log.info("Another pod already holds the lock. Skipping startup task.")
      return
    }

    try {
      subscriptionService.subscribe()
    } finally {
      lockRepository.deleteById(LOCK_NAME)
      log.info("Lock released")
    }
  }
}
