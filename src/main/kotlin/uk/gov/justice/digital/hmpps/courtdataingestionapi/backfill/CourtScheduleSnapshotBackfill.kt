package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourtScheduleApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtScheduleSnapshotEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtScheduleSnapshotRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.util.Sha256
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CourtScheduleSnapshotBackfill(
  private val courtScheduleSnapshotRepository: CourtScheduleSnapshotRepository,
  private val hmctsCourtScheduleApiClient: HmctsCourtScheduleApiClient,
  @Value("\${court-schedule-snapshot.prisons:}") prisons: List<String>,
  @Value("\${court-schedule-snapshot.lookback-days:28}") private val lookbackDays: Long,
  @Value("\${court-schedule-snapshot.grace-days:7}") private val graceDays: Long,
) : Backfill<String> {

  private val prisons = prisons.map { it.trim() }.filter { it.isNotEmpty() }

  override val id = "court-schedule-snapshot"

  override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<String> {
    if (prisons.isEmpty()) {
      log.warn("court-schedule-snapshot has no prisons configured; nothing to do")
      return BackfillBatch(emptyList(), cursor)
    }
    val now = LocalDateTime.now()
    val items = courtScheduleSnapshotRepository.findCaseReferencesToSnapshot(
      prisons = prisons,
      ingestedSince = now.minusDays(lookbackDays),
      listedUntilAfter = now.minusDays(graceDays),
      afterCaseReference = cursor,
      fetchedSince = LocalDate.now().atStartOfDay(),
      limit = batchSize,
    )
    return BackfillBatch(items, items.lastOrNull() ?: cursor)
  }

  override fun process(item: String) {
    val response = hmctsCourtScheduleApiClient.getCourtScheduleRaw(item)

    if (response.status !in 200..299) {
      courtScheduleSnapshotRepository.save(
        CourtScheduleSnapshotEntity(
          caseReference = item,
          httpStatus = response.status,
          errorBody = response.body.take(MAX_ERROR_BODY),
          changed = false,
        ),
      )
      log.info("Court schedule for {} returned {}", item, response.status)
      return
    }

    val hash = Sha256.hex(response.body.toByteArray())
    val previous = courtScheduleSnapshotRepository.findFirstByCaseReferenceAndBodySha256IsNotNullOrderByFetchedAtDesc(item)
    val changed = previous?.bodySha256 != hash

    courtScheduleSnapshotRepository.save(
      CourtScheduleSnapshotEntity(
        caseReference = item,
        httpStatus = response.status,
        bodySha256 = hash,
        body = response.body.takeIf { changed && it.isNotBlank() },
        changed = changed,
      ),
    )
  }

  private companion object {
    private const val MAX_ERROR_BODY = 4000
    private val log = LoggerFactory.getLogger(CourtScheduleSnapshotBackfill::class.java)
  }
}
