package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsCourtCasesReleaseDatesApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentViewEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentViewEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentView
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.Document
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.LocalDateTime
import java.util.UUID
import kotlin.jvm.optionals.getOrElse

@Service
@Transactional(readOnly = true)
class CourtDocumentService(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val courtCasesReleaseDatesApiClient: HmppsCourtCasesReleaseDatesApiClient,
  private val documentNotificationService: PrisonDocumentNotificationService,
  private val hmppsDocumentManagementApi: HmppsDocumentManagementApi,
) {

  @Transactional
  fun recordDocumentView(prisonDocumentId: UUID, courtDocumentView: CourtDocumentView) = recordEvent(prisonDocumentId, courtDocumentView, CourtDocumentViewEventType.VIEWED)

  @Transactional
  fun recordMarkAsNew(prisonDocumentId: UUID, courtDocumentView: CourtDocumentView) = recordEvent(prisonDocumentId, courtDocumentView, CourtDocumentViewEventType.MARKED_NEW)

  private fun recordEvent(prisonDocumentId: UUID, courtDocumentView: CourtDocumentView, eventType: CourtDocumentViewEventType) {
    val courtDocument = courtDocumentRepository.findFirstByPrisonDocumentId(prisonDocumentId).getOrElse { throw EntityNotFoundException("Court document not found $prisonDocumentId") }

    courtDocument.courtDocumentViews.add(
      CourtDocumentViewEntity(
        username = courtDocumentView.username,
        courtDocument = courtDocument,
        occurredAt = LocalDateTime.now(),
        eventType = eventType,
      ),
    )
    courtDocument.prisonerNumber?.let {
      courtCasesReleaseDatesApiClient.deleteThingsToDoCache(it)
    }

    updateDocumentIsUnread(courtDocument, eventType)
  }

  fun getCourtDocumentsByPersonIdAndPrisonDocumentIds(
    personId: String,
    prisonDocumentIds: List<UUID>,
  ): List<CourtDocument> {
    val unreadDocumentDateFrom: LocalDateTime = documentNotificationService.getUnreadDocumentDateFrom(personId)
    return courtDocumentRepository.findByPrisonerNumberAndPrisonDocumentIdIn(personId, prisonDocumentIds).map { document ->
      CourtDocument(
        prisonDocumentId = document.prisonDocumentId,
        caseReferences = document.courtDocumentCases.map { it.caseReference },
        isUnread = documentNotificationService.isUnread(document, unreadDocumentDateFrom),
        documentType = document.courtDocumentType,
        courtHearing = document.courtHearing?.let {
          CourtDocumentHearing(
            it.courtName,
            it.hearingType,
            it.hearingDate,
          )
        },
      )
    }
  }

  private fun updateDocumentIsUnread(courtDocument: CourtDocumentEntity, eventType: CourtDocumentViewEventType): () -> Result<Document> {
    val metadata = buildMap {
      put("isUnread", convertIsUnread(eventType))
    }

    return {
      runCatching { hmppsDocumentManagementApi.mergeMetadata(courtDocument.prisonDocumentId, metadata) }
        .onFailure {
          log.warn(
            "Record event: updating metadata document status failed for {} ",
            courtDocument.prisonDocumentId,
            it,
          )
        }
    }
  }

  companion object {
    private val log = LoggerFactory.getLogger(FileService::class.java)

    private fun convertIsUnread(eventType: CourtDocumentViewEventType): Boolean = (eventType == CourtDocumentViewEventType.MARKED_NEW)
  }
}
