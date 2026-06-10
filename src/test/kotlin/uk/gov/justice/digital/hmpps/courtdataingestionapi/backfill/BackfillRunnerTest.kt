package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.dao.DataIntegrityViolationException
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRun
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.BackfillRunStatus
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.BackfillRunRepository
import java.util.Optional
import java.util.UUID

class BackfillRunnerTest {

  private val repository: BackfillRunRepository = mock()
  private val runner = BackfillRunner(repository, defaultBatchSize = 50, staleThresholdIso = "PT5M")

  @Test
  fun `acquireLock returns the saved run when no concurrent run exists`() {
    val captured = argumentCaptor<BackfillRun>()
    whenever(repository.saveAndFlush(captured.capture())).thenAnswer { captured.firstValue }

    val run = runner.acquireLock("mirror", triggeredBy = "test")

    assertThat(run).isNotNull
    assertThat(run!!.backfillId).isEqualTo("mirror")
    assertThat(run.status).isEqualTo(BackfillRunStatus.RUNNING)
    assertThat(run.triggeredBy).isEqualTo("test")
  }

  @Test
  fun `acquireLock returns null when the unique partial index rejects a concurrent run`() {
    whenever(repository.saveAndFlush(any<BackfillRun>()))
      .thenThrow(DataIntegrityViolationException("duplicate key value violates unique constraint"))

    val run = runner.acquireLock("mirror", triggeredBy = null)

    assertThat(run).isNull()
  }

  @Test
  fun `runAsync processes batches, advances cursor and marks COMPLETED on empty batch`() {
    val backfill = StubBackfill(
      batches = listOf(
        BackfillBatch(listOf("a", "b", "c"), "cursor-1"),
        BackfillBatch(listOf("d"), "cursor-2"),
        BackfillBatch(emptyList(), "cursor-2"),
      ),
    )
    val runId = UUID.randomUUID()
    val runRow = BackfillRun(
      runId = runId,
      backfillId = backfill.id,
      status = BackfillRunStatus.RUNNING,
    )
    whenever(repository.findById(runId)).thenReturn(Optional.of(runRow))
    whenever(repository.save(any<BackfillRun>())).thenAnswer { it.arguments[0] as BackfillRun }

    runner.runAsync(runId, backfill, batchSize = 10)

    assertThat(backfill.processedItems).containsExactlyInAnyOrder("a", "b", "c", "d")
    assertThat(runRow.status).isEqualTo(BackfillRunStatus.COMPLETED)
    assertThat(runRow.processed).isEqualTo(4)
    assertThat(runRow.failed).isEqualTo(0)
    assertThat(runRow.cursor).isEqualTo("cursor-2")
    assertThat(runRow.completedAt).isNotNull
  }

  @Test
  fun `runAsync isolates per-item failures and counts them without aborting the run`() {
    val backfill = StubBackfill(
      batches = listOf(
        BackfillBatch(listOf("ok", "fail", "ok"), "c1"),
        BackfillBatch(emptyList(), "c1"),
      ),
      failOn = setOf("fail"),
    )
    val runId = UUID.randomUUID()
    val runRow = BackfillRun(runId = runId, backfillId = backfill.id, status = BackfillRunStatus.RUNNING)
    whenever(repository.findById(runId)).thenReturn(Optional.of(runRow))
    whenever(repository.save(any<BackfillRun>())).thenAnswer { it.arguments[0] as BackfillRun }

    runner.runAsync(runId, backfill, batchSize = 10)

    assertThat(runRow.status).isEqualTo(BackfillRunStatus.COMPLETED)
    assertThat(runRow.processed).isEqualTo(2)
    assertThat(runRow.failed).isEqualTo(1)
  }

  @Test
  fun `runAsync marks the run FAILED when selectBatch throws`() {
    val backfill = StubBackfill(batches = emptyList(), throwOnSelect = IllegalStateException("DB down"))
    val runId = UUID.randomUUID()
    val runRow = BackfillRun(runId = runId, backfillId = backfill.id, status = BackfillRunStatus.RUNNING)
    whenever(repository.findById(runId)).thenReturn(Optional.of(runRow))
    whenever(repository.save(any<BackfillRun>())).thenAnswer { it.arguments[0] as BackfillRun }

    runner.runAsync(runId, backfill, batchSize = 10)

    assertThat(runRow.status).isEqualTo(BackfillRunStatus.FAILED)
    assertThat(runRow.failureReason).contains("DB down")
  }

  @Test
  fun `sweepStaleRuns delegates to repository with computed threshold`() {
    whenever(repository.reclaimStaleRunning(any(), any())).thenReturn(2)

    val swept = runner.sweepStaleRuns()

    assertThat(swept).isEqualTo(2)
    verify(repository).reclaimStaleRunning(any(), any())
  }

  @Test
  fun `sweepStaleRuns returns zero when nothing is stale`() {
    whenever(repository.reclaimStaleRunning(any(), any())).thenReturn(0)
    assertThat(runner.sweepStaleRuns()).isEqualTo(0)
  }

  /**
   * Stub backfill returning a scripted sequence of batches and optionally failing on selected
   * items or the select call itself. Kept inside the test file because no production code needs
   * a test double of Backfill.
   */
  private class StubBackfill(
    private val batches: List<BackfillBatch<String>>,
    private val failOn: Set<String> = emptySet(),
    private val throwOnSelect: Throwable? = null,
  ) : Backfill<String> {
    override val id = "stub"
    override val concurrency = 2
    val processedItems = mutableListOf<String>()
    private var batchIndex = 0

    override fun selectBatch(cursor: String, batchSize: Int): BackfillBatch<String> {
      throwOnSelect?.let { throw it }
      val batch = batches.getOrNull(batchIndex++) ?: BackfillBatch(emptyList(), cursor)
      return batch
    }

    override fun process(item: String) {
      if (item in failOn) throw RuntimeException("scripted failure for $item")
      synchronized(processedItems) { processedItems.add(item) }
    }
  }
}
