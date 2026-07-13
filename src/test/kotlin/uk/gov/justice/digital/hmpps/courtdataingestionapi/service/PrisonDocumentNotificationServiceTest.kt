package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
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
  @MethodSource("getUnreadDocumentDateFromTestParameters")
  fun `Given prisoner {prisonerId}, when found prisonId {prisonId} has newDocDateFrom {newDocDateFrom} configured , should return {expected}`(prisonerId: String, prisonId: String?, newDocDateFrom: LocalDateTime?, expected: LocalDateTime) {
    log.debug("Given prisonerId [{}], when found prisonId [{}] has newDocDateFrom [{}] configured, should return [{}]", prisonerId, prisonId, newDocDateFrom, expected)
    setupPrisonNewDocNotification(prisonId, newDocDateFrom)
    val result = prisonDocumentNotificationService.getUnreadDocumentDateFrom(prisonerId)

    assertThat(result).isEqualTo(expected)
  }

  @ParameterizedTest
  @MethodSource("isUnreadTestParameters")
  fun `When court documents have {views} views and their linked prison {prisonId} has newDocDateFrom of {newDocDateFrom}, should return {expected}`(views: Int, prisonId: String?, newDocDateFrom: LocalDateTime, expected: Boolean) {
    log.debug("When court documents have [{}] views and their linked prison [{}] has newDocDateFrom of [{}], should return [{}]", views, prisonId, newDocDateFrom, expected)
    setupPrisonNewDocNotification(prisonId, newDocDateFrom)
    val dbCourtDocument = buildTestCourtDocument(views)

    val result = prisonDocumentNotificationService.isUnread(dbCourtDocument, newDocDateFrom)

    if (expected) {
      assertThat(result).isTrue
    } else {
      assertThat(result).isFalse
    }
  }

  private fun setupPrisonNewDocNotification(prisonId: String?, newDocDateFrom: LocalDateTime?) {
    if (prisonId == null) return

    notificationConfigRepository.save(
      PrisonDocNotificationConfigEntity(prisonId, newDocDateFrom ?: LocalDateTime.MIN),
    )
  }

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

    const val PRISONER_ID_WITH_MATCHING_PRISON: String = MATCHING_PRISONER_NUMBER
    const val PRISONER_ID_WITH_NO_MATCHING_PRISON: String = "XYZ789"
    const val MATCHING_PRISON_ID: String = "Mock01"
    const val NO_MATCHING_PRISON_ID: String = "Mock02"

    private fun getNewDocNotificationDateFrom(newDocDateFromAdj: Int): LocalDateTime = LocalDate.now().atStartOfDay().plusDays(newDocDateFromAdj.toLong())

    @JvmStatic
    fun getUnreadDocumentDateFromTestParameters() = listOf(
      Arguments.of(PRISONER_ID_WITH_NO_MATCHING_PRISON, null, null, LocalDateTime.MIN),
      Arguments.of(PRISONER_ID_WITH_MATCHING_PRISON, MATCHING_PRISON_ID, this.getNewDocNotificationDateFrom(1), this.getNewDocNotificationDateFrom(1)),
      Arguments.of(PRISONER_ID_WITH_MATCHING_PRISON, null, null, LocalDateTime.MIN),
      Arguments.of(PRISONER_ID_WITH_MATCHING_PRISON, NO_MATCHING_PRISON_ID, this.getNewDocNotificationDateFrom(1), LocalDateTime.MIN),
    )

    @JvmStatic
    fun isUnreadTestParameters() = listOf(
      Arguments.of(0, null, LocalDateTime.MIN, true),
      Arguments.of(0, MATCHING_PRISON_ID, this.getNewDocNotificationDateFrom(-1), true),
      Arguments.of(0, MATCHING_PRISON_ID, this.getNewDocNotificationDateFrom(1), false),
      Arguments.of(1, null, LocalDateTime.MIN, false),
      Arguments.of(1, MATCHING_PRISON_ID, this.getNewDocNotificationDateFrom(-1), false),
      Arguments.of(1, MATCHING_PRISON_ID, this.getNewDocNotificationDateFrom(1), false),
    )
  }
}
