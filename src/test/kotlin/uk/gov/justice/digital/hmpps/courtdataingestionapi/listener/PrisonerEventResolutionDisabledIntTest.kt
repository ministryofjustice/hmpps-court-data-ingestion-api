package uk.gov.justice.digital.hmpps.courtdataingestionapi.listener

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.wiremock.CorePersonApiExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.service.CourtCaseDefendantService
import uk.gov.justice.hmpps.sqs.countMessagesOnQueue
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@TestPropertySource(properties = ["feature-toggles.defendant-resolution=false"])
class PrisonerEventResolutionDisabledIntTest : IntegrationTestBase() {

  @Autowired
  private lateinit var courtCaseDefendantService: CourtCaseDefendantService

  private val dob = LocalDate.of(1990, 6, 1)

  @Test
  fun `prisoner created does not resolve a co-defendant, but still matches a direct id`() {
    val fixture = twoUnmatchedDocumentsForOnePrisoner()

    sendPrisonerCreatedMessage(PRISONER_NUMBER)

    assertDirectMatchedAndCoDefendantUntouched(fixture)
  }

  @Test
  fun `prisoner updated (personal details) does not resolve a co-defendant, but still matches a direct id`() {
    val fixture = twoUnmatchedDocumentsForOnePrisoner()

    sendPrisonerUpdatedMessage(PRISONER_NUMBER, listOf("PERSONAL_DETAILS"))

    assertDirectMatchedAndCoDefendantUntouched(fixture)
  }

  private fun twoUnmatchedDocumentsForOnePrisoner(): Fixture {
    val coDefendantMaster = UUID.randomUUID()
    val coDefendantId = UUID.randomUUID()
    val directMaster = UUID.randomUUID()

    saveUnmatchedDocument(coDefendantMaster)
    saveUnmatchedDocument(directMaster)
    courtCaseDefendantService.upsert(coDefendantId, CASE_REFERENCE, coDefendantMaster, "Co Defendant", dob)

    CorePersonApiExtension.corePersonApi.stubPrisonerCorePerson(PRISONER_NUMBER, listOf(coDefendantId, directMaster))

    return Fixture(coDefendantMaster, directMaster)
  }

  private fun saveUnmatchedDocument(master: UUID) {
    courtDocumentRepository.save(
      CourtDocumentEntity(
        masterDefendantId = master,
        hmctsCourtDocumentId = UUID.randomUUID(),
        prisonDocumentId = PRISON_DOCUMENT_ID,
        hmctsCourtHearingId = null,
        prisonEmailAddress = PRISON_EMAIL,
        eventType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED,
        courtDocumentType = HmctsEventType.PRISON_COURT_REGISTER_GENERATED.documentType,
        documentGeneratedTimestamp = LocalDateTime.now(),
      ),
    )
  }

  private fun assertDirectMatchedAndCoDefendantUntouched(fixture: Fixture) {
    awaitAtMost30Secs untilCallTo {
      courtWarrantTestQueue.sqsClient.countMessagesOnQueue(courtWarrantTestQueue.queueUrl).get()
    } matches { it == 1 }

    val directDocument = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(fixture.directMaster)!!
    assertThat(directDocument.prisonerNumber).isEqualTo(PRISONER_NUMBER)

    val coDefendantDocument = courtDocumentRepository.findFirstByMasterDefendantIdOrderByIngestionAtDesc(fixture.coDefendantMaster)!!
    assertThat(coDefendantDocument.prisonerNumber).isNull()
  }

  private data class Fixture(
    val coDefendantMaster: UUID,
    val directMaster: UUID,
  )

  companion object {
    const val PRISONER_NUMBER = "OFF900"
  }
}
