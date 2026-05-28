package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonDocNotificationConfigRepository
import kotlin.jvm.optionals.getOrElse

@Service
@Transactional(readOnly = true)
class PrisonDocumentNotificationService(
  private val prisonerSearchService: PrisonerSearchService,
  private val notificationConfigRepository: PrisonDocNotificationConfigRepository,
) {
  fun getIsUnread(document: CourtDocumentEntity): Boolean {
    if (document.courtDocumentViews.isNotEmpty()) return false

    val prisonerNumber = document.prisonerNumber

    if (prisonerNumber.isNullOrBlank()) return true

    val prisonId = prisonerSearchService.getPrison(prisonerNumber)

    if (prisonId.isNullOrBlank()) return true

    val notificationConfig = notificationConfigRepository.findByPrisonId(prisonId).getOrElse {
      log.debug("No notification configuration found for prisonId={} ", prisonId)
      return true
    }

    return (document.ingestionAt.isAfter(notificationConfig.newDocDateFrom))
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
