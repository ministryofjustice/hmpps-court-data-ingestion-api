package uk.gov.justice.digital.hmpps.courtdataingestionapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ingestion.DuplicateReconciliationService
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.ingestion.DuplicateResolutionService
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DuplicateResolutionIntegrationTest : IntegrationTestBase() {

  @Autowired
  private lateinit var duplicateResolutionService: DuplicateResolutionService

  @Autowired
  private lateinit var duplicateReconciliationService: DuplicateReconciliationService

  @Autowired
  private lateinit var transactionManager: PlatformTransactionManager

  private val transactionTemplate by lazy { TransactionTemplate(transactionManager) }

  private val sharedExtractedHash = "58ed0c987864be01771eb171a24f369a664e0c5440c97b0c8f917ed5e5d63dae"

  private fun persistDocument(
    extractedTextSha256: String?,
    downloadedFileSha256: String?,
    ingestionAt: LocalDateTime,
    duplicateOf: UUID? = null,
  ): CourtDocumentEntity = courtDocumentRepository.save(
    CourtDocumentEntity(
      defendantId = UUID.randomUUID(),
      courtDocumentId = UUID.randomUUID(),
      prisonDocumentId = UUID.randomUUID(),
      courtHearingId = UUID.randomUUID(),
      prisonEmailAddress = "prison.email@example.com",
      eventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
      courtDocumentType = CourtDocumentType.PRISON_COURT_REGISTER,
      documentGeneratedTimestamp = LocalDateTime.now().minusDays(1),
      ingestionAt = ingestionAt,
      downloadedFileSha256 = downloadedFileSha256,
      extractedTextSha256 = extractedTextSha256,
      duplicateOf = duplicateOf,
    ),
  )

  @Test
  fun `a later document with the same extracted text hash resolves to the earlier one`() {
    val first = persistDocument(
      extractedTextSha256 = sharedExtractedHash,
      downloadedFileSha256 = "fffac8f1a93fabc8ad1629d255527c6ae12abfc5cc0921def588bfa2ce00b024",
      ingestionAt = LocalDateTime.now().minusMinutes(5),
    )

    val secondPrisonDocumentId = UUID.randomUUID()
    val outcome = transactionTemplate.execute {
      duplicateResolutionService.resolve(
        currentDocumentId = secondPrisonDocumentId,
        downloadedFileSha256 = "de818a84889d8bb9f5ed0de862a4bcebf25a2999ef53911bd452bff59759cc4e",
        extractedTextSha256 = sharedExtractedHash,
      )
    }

    assertThat(outcome).isNotNull
    assertThat(outcome!!.duplicateOf).isEqualTo(first.prisonDocumentId)
  }

  @Test
  fun `resolution points at the canonical head when several copies already share a hash`() {
    val head = persistDocument(
      extractedTextSha256 = sharedExtractedHash,
      downloadedFileSha256 = "aaa1",
      ingestionAt = LocalDateTime.now().minusMinutes(10),
    )
    // An already-linked duplicate of the head. A third copy must point at the head, not at this row.
    persistDocument(
      extractedTextSha256 = sharedExtractedHash,
      downloadedFileSha256 = "bbb2",
      ingestionAt = LocalDateTime.now().minusMinutes(8),
      duplicateOf = head.prisonDocumentId,
    )

    val outcome = transactionTemplate.execute {
      duplicateResolutionService.resolve(
        currentDocumentId = UUID.randomUUID(),
        downloadedFileSha256 = "ccc3",
        extractedTextSha256 = sharedExtractedHash,
      )
    }

    assertThat(outcome!!.duplicateOf).isEqualTo(head.prisonDocumentId)
  }

  @Test
  fun `reconciliation links an unlinked pair that shares an extracted text hash`() {
    // Reproduces the observed defect: two heads, same extracted text hash, neither linked.
    val earlier = persistDocument(
      extractedTextSha256 = sharedExtractedHash,
      downloadedFileSha256 = "fffac8f1a93fabc8ad1629d255527c6ae12abfc5cc0921def588bfa2ce00b024",
      ingestionAt = LocalDateTime.parse("2026-06-05T10:51:07.602595"),
    )
    val later = persistDocument(
      extractedTextSha256 = sharedExtractedHash,
      downloadedFileSha256 = "de818a84889d8bb9f5ed0de862a4bcebf25a2999ef53911bd452bff59759cc4e",
      ingestionAt = LocalDateTime.parse("2026-06-05T10:51:08.000000"),
    )

    val summary = duplicateReconciliationService.reconcile()

    assertThat(summary.groups).isEqualTo(1)
    assertThat(summary.linked).isEqualTo(1)

    val earlierAfter = courtDocumentRepository.findById(earlier.id).orElseThrow()
    val laterAfter = courtDocumentRepository.findById(later.id).orElseThrow()

    assertThat(earlierAfter.duplicateOf).isNull()
    assertThat(laterAfter.duplicateOf).isEqualTo(earlier.prisonDocumentId)
  }

  @Test
  fun `reconciliation is idempotent`() {
    persistDocument(
      extractedTextSha256 = sharedExtractedHash,
      downloadedFileSha256 = "aaa1",
      ingestionAt = LocalDateTime.now().minusMinutes(2),
    )
    persistDocument(
      extractedTextSha256 = sharedExtractedHash,
      downloadedFileSha256 = "bbb2",
      ingestionAt = LocalDateTime.now().minusMinutes(1),
    )

    val first = duplicateReconciliationService.reconcile()
    val second = duplicateReconciliationService.reconcile()

    assertThat(first.linked).isEqualTo(1)
    assertThat(second.linked).isEqualTo(0)
  }

  @Test
  @Disabled("Run locally to demonstrate serialisation; excluded from CI to avoid timing flakiness")
  fun `two concurrent same-hash ingestions serialise via the advisory lock`() {
    val pool = Executors.newFixedThreadPool(2)
    val barrier = CyclicBarrier(2)

    fun ingest(downloadedHash: String) {
      val prisonDocumentId = UUID.randomUUID()
      transactionTemplate.execute {
        barrier.await(10, TimeUnit.SECONDS)
        val outcome = duplicateResolutionService.resolve(
          currentDocumentId = prisonDocumentId,
          downloadedFileSha256 = downloadedHash,
          extractedTextSha256 = sharedExtractedHash,
        )
        persistDocument(
          extractedTextSha256 = sharedExtractedHash,
          downloadedFileSha256 = downloadedHash,
          ingestionAt = LocalDateTime.now(),
          duplicateOf = outcome?.duplicateOf,
        )
      }
    }

    try {
      val a = pool.submit { ingest("aaa1") }
      val b = pool.submit { ingest("bbb2") }
      a.get(20, TimeUnit.SECONDS)
      b.get(20, TimeUnit.SECONDS)
    } finally {
      pool.shutdownNow()
    }

    val rows = courtDocumentRepository.findByExtractedTextSha256(sharedExtractedHash)
    assertThat(rows).hasSize(2)
    val heads = rows.filter { it.duplicateOf == null }
    val duplicates = rows.filter { it.duplicateOf != null }
    assertThat(heads).hasSize(1)
    assertThat(duplicates).hasSize(1)
    assertThat(duplicates.single().duplicateOf).isEqualTo(heads.single().prisonDocumentId)
  }
}
