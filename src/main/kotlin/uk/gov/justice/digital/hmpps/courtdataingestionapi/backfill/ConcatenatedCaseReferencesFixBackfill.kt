package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentCaseEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.HmctsCase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.util.UUID

@Component
class ConcatenatedCaseReferencesFixBackfill(
  private val courtDocumentRepository: CourtDocumentRepository,
) : Backfill<UUID> {

  override val id = "concatenated-cases"
  override val concurrency = 8

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<UUID> {
    val afterId = parseCursorUUID(cursor)
    val items = courtDocumentRepository.findCourtDocumentIdsWithConcatenatedCaseReferencesAfter(afterId, batchSize)
    val nextCursor = items.lastOrNull()?.toString() ?: cursor
    return BackfillBatch(items, nextCursor)
  }

  @Transactional
  override fun process(item: UUID) {
    val document = courtDocumentRepository.findById(item).get()

    val concatenatedCaseReferences: List<CourtDocumentCaseEntity> = document.courtDocumentCases.filter { it.caseReference.contains(",") }.distinct()
    val correctedConcatenatedCases: List<String> = concatenatedCaseReferences.flatMap { HmctsCase(it.caseReference).caseReferences() }.distinct()

    val correctCaseReferences: Set<String> = document.courtDocumentCases.filter { !it.caseReference.contains(",") }.map { it.caseReference }.toSet()

    val courDocumentCasesToAdd: List<String> = correctedConcatenatedCases.filter { !correctCaseReferences.contains(it) }

    courDocumentCasesToAdd.forEach {
      document.courtDocumentCases.add(
        CourtDocumentCaseEntity(
          id = UUID.randomUUID(),
          caseReference = it,
          courtDocument = document,
        ),
      )
    }

    concatenatedCaseReferences.forEach {
      document.courtDocumentCases.remove(it)
    }

    courtDocumentRepository.save(document)

    // TODO (CDIA-327): Update Document's metadata
  }
}
