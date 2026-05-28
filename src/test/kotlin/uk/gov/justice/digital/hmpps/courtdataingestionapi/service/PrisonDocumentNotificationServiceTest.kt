package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.TestUtil
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.PrisonDocNotificationConfigEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentView
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonDocNotificationConfigRepository
import java.time.LocalDate
import java.time.LocalDateTime

@Transactional
class PrisonDocumentNotificationServiceTest : IntegrationTestBase() {
  @Autowired
  lateinit var notificationConfigRepository: PrisonDocNotificationConfigRepository

  @Autowired
  lateinit var prisonerSearchService: PrisonerSearchService

  @Autowired
  lateinit var prisonDocumentNotificationService: PrisonDocumentNotificationService

  @BeforeEach
  fun setUp() {
    notificationConfigRepository.deleteAll()
  }

  @ParameterizedTest
  @CsvSource(
    "0,,0,true",
    "0,Mock01,1,false",
    "0,Mock01,-1,true",
    "1,,0,false",
    "1,Mock01,1,false",
    "1,Mock01,-1,false",
  )
  fun `When court documents have 0 views and based on {prisonId} newDocDateFrom configuration, should return {expected}`(views: Int, prisonId: String?, newDocDateFromAdj: Int, expected: Boolean) {
    log.debug("When court documents have [{}] views and their linked prison=[{}] has newDocDateFrom=[{}] configured{}, should return [{}]", views, prisonId, if (prisonId == null) "" else getNewDocNotificationDateFrom(newDocDateFromAdj), if (prisonId == null) "" else (if (newDocDateFromAdj < 0) " in the past" else " in the future"), expected)
    setupPrisonNewDocNotification(prisonId, newDocDateFromAdj)
    val dbCourtDocument = buildTestCourtDocument(views)

    val result = prisonDocumentNotificationService.isUnread(dbCourtDocument)

    if (expected) {
      assertThat(result).isTrue
    } else {
      assertThat(result).isFalse
    }
  }

  private fun setupPrisonNewDocNotification(prisonId: String?, newDocDateFromAdj: Int) {
    if (prisonId == null) return

    notificationConfigRepository.save(
      PrisonDocNotificationConfigEntity(
        prisonId,
        getNewDocNotificationDateFrom(newDocDateFromAdj),
      ),
    )
  }

  private fun getNewDocNotificationDateFrom(newDocDateFromAdj: Int): LocalDateTime = LocalDate.now().atStartOfDay().plusDays(newDocDateFromAdj.toLong())

  private fun buildTestCourtDocument(views: Int): CourtDocumentEntity {
    sendSubscriptionNotification(MATCHING_CORE_PERSON)
    val dbCourtDocument = courtDocumentRepository.findAll()[0]

    if (views <= 0) {
      dbCourtDocument.courtDocumentViews = mutableListOf()
      return dbCourtDocument
    }

    sendCourtDocumentViewNotification(dbCourtDocument)
    return courtDocumentRepository.findAll()[0]
  }

  private fun sendCourtDocumentViewNotification(courtDocument: CourtDocumentEntity) {
    webTestClient
      .post()
      .uri("/court-document/${courtDocument.prisonDocumentId}/view")
      .headers {
        it.contentType = MediaType.APPLICATION_JSON
      }
      .headers(setAuthorisation(roles = listOf("COURT_DATA_INGESTION__COURT_DATA_RW")))
      .bodyValue(
        TestUtil.objectMapper().writeValueAsString(
          CourtDocumentView(
            username = TEST_USERNAME,
          ),
        ),
      )
      .exchange()
      .expectStatus()
      .isOk
  }

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
