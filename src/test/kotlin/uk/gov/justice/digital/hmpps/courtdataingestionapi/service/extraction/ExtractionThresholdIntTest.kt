package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.ExtractionResultRepository
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class ExtractionThresholdIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var extractionResultRepository: ExtractionResultRepository

  @Test
  fun `only returns documents ingested at or after the threshold`() {
    val cutoff = LocalDateTime.of(2026, 5, 18, 16, 38, 15)
    val before = courtDocument(cutoff.minusDays(1))
    val after = courtDocument(cutoff.plusDays(1))
    courtDocumentRepository.saveAll(listOf(before, after))

    val ids = extractionResultRepository.findDocumentIdsMissingExtraction(
      formatId = "any",
      formatVersion = 1,
      extractorVersion = "test",
      ingestedFrom = cutoff.toInstant(ZoneOffset.UTC),
      limit = 100,
    )

    assertThat(ids).contains(after.prisonDocumentId).doesNotContain(before.prisonDocumentId)
  }

  private fun courtDocument(ingestionAt: LocalDateTime) = CourtDocumentEntity(
    defendantId = UUID.randomUUID(),
    courtDocumentId = UUID.randomUUID(),
    prisonDocumentId = UUID.randomUUID(),
    prisonEmailAddress = "court@example.gov.uk",
    eventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
    courtDocumentType = CourtDocumentType.PRISON_COURT_REGISTER,
    documentGeneratedTimestamp = ingestionAt,
    ingestionAt = ingestionAt,
  )
}
