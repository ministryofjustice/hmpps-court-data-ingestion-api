package uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourtDefendantApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.DefendantDetails
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentCaseRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtCaseDefendantStore
import java.time.LocalDate
import java.util.UUID

class DefendantDetailsBackfillTest {

  private val caseRepository = mock<CourtDocumentCaseRepository>()
  private val client = mock<HmctsCourtDefendantApiClient>()
  private val store = mock<CourtCaseDefendantStore>()

  private val dob = LocalDate.of(1990, 6, 1)

  @Test
  fun `selectBatch advances the cursor to the last case reference`() {
    val backfill = DefendantDetailsDryRunBackfill(caseRepository, client)
    whenever(caseRepository.findDistinctCaseReferencesAfter("", 2)).thenReturn(listOf("URN-A", "URN-B"))

    val batch = backfill.selectBatch("", 2)

    assertThat(batch.items).containsExactly("URN-A", "URN-B")
    assertThat(batch.nextCursor).isEqualTo("URN-B")
  }

  @Test
  fun `selectBatch holds the cursor when a batch is empty`() {
    val backfill = DefendantDetailsDryRunBackfill(caseRepository, client)
    whenever(caseRepository.findDistinctCaseReferencesAfter("URN-Z", 2)).thenReturn(emptyList())

    val batch = backfill.selectBatch("URN-Z", 2)

    assertThat(batch.items).isEmpty()
    assertThat(batch.nextCursor).isEqualTo("URN-Z")
  }

  @Test
  fun `dry run writes nothing`() {
    val backfill = DefendantDetailsDryRunBackfill(caseRepository, client)
    whenever(client.getDefendants("URN-A"))
      .thenReturn(listOf(DefendantDetails(UUID.randomUUID(), UUID.randomUUID(), "A", dob)))

    backfill.process("URN-A")

    verify(store, never()).upsert(any(), any(), any(), any(), any())
  }

  @Test
  fun `apply upserts one row per returned defendant against the case reference`() {
    val backfill = DefendantDetailsApplyBackfill(caseRepository, client, store)
    val masterOne = UUID.randomUUID()
    val masterTwo = UUID.randomUUID()
    val d1 = UUID.randomUUID()
    val d2 = UUID.randomUUID()
    whenever(client.getDefendants("URN-A")).thenReturn(
      listOf(
        DefendantDetails(d1, masterOne, "One", dob),
        DefendantDetails(d2, masterTwo, "Two", dob),
      ),
    )

    backfill.process("URN-A")

    verify(store).upsert(eq(d1), eq("URN-A"), eq(masterOne), eq("One"), eq(dob))
    verify(store).upsert(eq(d2), eq("URN-A"), eq(masterTwo), eq("Two"), eq(dob))
  }

  @Test
  fun `apply skips cases with no defendants`() {
    val backfill = DefendantDetailsApplyBackfill(caseRepository, client, store)
    whenever(client.getDefendants("URN-EMPTY")).thenReturn(emptyList())

    backfill.process("URN-EMPTY")

    verify(store, never()).upsert(any(), any(), any(), any(), any())
  }
}