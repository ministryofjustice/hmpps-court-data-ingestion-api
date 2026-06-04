package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.ExtractionResultEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.ExtractionResultRepository
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID

class ExtractionThresholdIntTest : IntegrationTestBase() {

  @Autowired
  lateinit var extractionResultRepository: ExtractionResultRepository

  @BeforeEach
  fun cleanExtractionTables() {
    extractionResultRepository.deleteAll()
    courtDocumentRepository.deleteAll()
  }

  @Test
  fun `retries errored documents after the cooldown, not within it, and never retries terminal ones`() {
    val ingestedFrom = Instant.parse("2020-01-01T00:00:00Z") // threshold not under test here
    val retryErrorsBefore = Instant.now().minus(Duration.ofHours(3)) // 3h cooldown

    val staleError = courtDocument(LocalDateTime.now())
    val freshError = courtDocument(LocalDateTime.now())
    val terminal = courtDocument(LocalDateTime.now())
    courtDocumentRepository.saveAll(listOf(staleError, freshError, terminal))

    extractionResultRepository.saveAll(
      listOf(
        extractionResult(staleError.prisonDocumentId, "ERROR", LocalDateTime.now().minusHours(4)),
        extractionResult(freshError.prisonDocumentId, "ERROR", LocalDateTime.now().minusHours(1)),
        extractionResult(terminal.prisonDocumentId, "FAILED", LocalDateTime.now().minusHours(4)),
      ),
    )

    val ids = extractionResultRepository.findDocumentIdsMissingExtraction(
      formatId = "fmt",
      formatVersion = 1,
      extractorVersion = "test",
      ingestedFrom = ingestedFrom,
      retryErrorsBefore = retryErrorsBefore,
      limit = 100,
    )

    assertThat(ids)
      .contains(staleError.prisonDocumentId)
      .doesNotContain(freshError.prisonDocumentId, terminal.prisonDocumentId)
  }

  private fun extractionResult(documentId: UUID, status: String, extractedAt: LocalDateTime) = ExtractionResultEntity(
    documentId = documentId,
    contentSha256 = "0".repeat(64),
    formatId = "fmt",
    formatVersion = 1,
    extractorVersion = "test",
    status = status,
    result = "{}",
    extractedAt = extractedAt,
  )

  private fun courtDocument(ingestionAt: LocalDateTime) = CourtDocumentEntity(
    defendantId = UUID.randomUUID(),
    courtDocumentId = UUID.randomUUID(),
    prisonDocumentId = UUID.randomUUID(),
    prisonEmailAddress = "court@example.gov.uk",
    eventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
    courtDocumentType = CourtDocumentType.PRISON_COURT_REGISTER,
    documentGeneratedTimestamp = ingestionAt,
    ingestionAt = ingestionAt,
    courtHearingId = UUID.randomUUID(),
  )
}
