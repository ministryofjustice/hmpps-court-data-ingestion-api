package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonDocNotificationConfigRepository
import java.time.LocalDateTime
import kotlin.jvm.optionals.getOrElse

@Service
@Transactional(readOnly = true)
class PrisonDocumentNotificationService(
  private val prisonerSearchService: PrisonerSearchService,
  private val notificationConfigRepository: PrisonDocNotificationConfigRepository,
) {
  fun isUnread(document: CourtDocumentEntity, unreadDocumentDateFrom: LocalDateTime): Boolean {
    if (document.courtDocumentViews.isNotEmpty()) return false

    val prisonerNumber = document.prisonerNumber

    if (prisonerNumber.isNullOrBlank()) return true

    return (document.ingestionAt.isAfter(unreadDocumentDateFrom))
  }

  fun getUnreadDocumentDateFrom(prisonerId: String): LocalDateTime {
    if (prisonerId.isBlank()) return LocalDateTime.MIN

    val prisonId = prisonerSearchService.getPrison(prisonerId)

    if (prisonId.isNullOrBlank()) return LocalDateTime.MIN

    val notificationConfig = notificationConfigRepository.findByPrisonId(prisonId).getOrElse {
      log.debug("No notification configuration found for prisonId={} ", prisonId)
      return LocalDateTime.MIN
    }

    return notificationConfig.newDocDateFrom
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
