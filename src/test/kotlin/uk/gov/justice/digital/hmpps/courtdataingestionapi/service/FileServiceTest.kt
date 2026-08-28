package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsSubscriptionApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentCaseEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtHearingEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.ingestion.DestinationType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.integration.IntegrationTestBase.Companion.CASE_REFERENCE
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi.HmctsEventType
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.SubscriptionRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.emptyArray

@ExtendWith(MockitoExtension::class)
class FileServiceTest {
  @Mock
  lateinit var hmctsSubscriptionApiClient: HmctsSubscriptionApiClient

  @Mock
  lateinit var subscriptionRepository: SubscriptionRepository

  @Mock
  lateinit var hmppsDocumentManagementApi: HmppsDocumentManagementApi

  @Mock
  lateinit var documentNotificationService: PrisonDocumentNotificationService

  lateinit var fileService: FileService

  @BeforeEach
  fun setUp() {
    fileService = FileService(
      hmctsSubscriptionApiClient,
      subscriptionRepository,
      hmppsDocumentManagementApi,
      documentNotificationService,
      ENV_NAME,
    )
  }

  @ParameterizedTest
  @MethodSource("getBuildMirrorEnrichmentMetadataTestParameters")
  fun buildMirrorEnrichmentMetadata(
    deliverySource: DestinationType?,
    courtDocumentType: CourtDocumentType,
    courtCode: String?,
    caseReference: String?,
    expectedSource: String,
    expectedSubType: String,
    expectedCourtCode: String,
    expectedCaseReferences: Array<String>,
  ) {
    val document = sampleWarrant(deliverySource, courtDocumentType, courtCode, caseReference)

    val result = fileService.buildMirrorEnrichmentMetadata(document)

    assertThat(result).isNotEmpty()
    assertThat(result.getOrDefault("deliverySource", "NOT FOUND")).isEqualTo(expectedSource)
    assertThat(result["documentSubType"]).isEqualTo(expectedSubType)
    assertThat(result.getOrDefault("courtCode", "NOT FOUND")).isEqualTo(expectedCourtCode)

    assertThat(result["caseReferences"]).hasSameClassAs(expectedCaseReferences)
    val resultCaseReferences = result["caseReferences"] as Array<String>
    assertThat(resultCaseReferences).hasSize(expectedCaseReferences.size)
    assertThat(resultCaseReferences).isEqualTo(expectedCaseReferences)
  }

  companion object {
    const val ENV_NAME = "test"
    val COURT_HEARING_ID: UUID = UUID.fromString("509b295e-22d1-4cc0-9925-d5690503ce3c")
    val COURT_ID: UUID = UUID.fromString("d569ce3c-4cc0-9925-22d1-509b295e0503")
    const val CASE_REFERENCE_2 = "CASE789012"
    const val COURT_CODE = "LND001"

    @JvmStatic
    private fun sampleWarrant(deliverySource: DestinationType?, courtDocumentType: CourtDocumentType, courtCode: String?, caseReference: String?): CourtDocumentEntity {
      val document = CourtDocumentEntity(
        deliverySource = deliverySource,
        courtDocumentType = courtDocumentType,
        masterDefendantId = UUID.randomUUID(),
        hmctsCourtDocumentId = UUID.randomUUID(),
        prisonDocumentId = UUID.randomUUID(),
        hmctsCourtHearingId = COURT_HEARING_ID,
        prisonEmailAddress = "OMU.HolmeHouse@justice.gov.uk",
        eventType = HmctsEventType.WEE_SendingToCrownCourtForTrial,
        documentGeneratedTimestamp = LocalDateTime.now(),
        addressedPrison = "HHI",
        downloadedFileSha256 = "1e8c08ae751bcfb0fd81b3f3abb32659a98a2171c30bc5c8e153791bc7060040",
        extractedTextSha256 = "1e8c08ae751bcfb0fd81b3f3abb32659a98a2171c30bc5c8e153791bc7060040",
      )

      caseReference?.split(",")?.forEach { reference ->
        document.courtDocumentCases.add(CourtDocumentCaseEntity(UUID.randomUUID(), reference, document))
      }

      if (courtCode != null) {
        document.courtHearing = CourtHearingEntity(
          hmctsCourtId = COURT_ID,
          hmppsCourtId = courtCode,
          courtName = "Central London County Court",
          hearingType = "First hearing",
          hearingDate = LocalDate.of(2026, 6, 4),
          hmctsCourtHearingId = COURT_HEARING_ID,
          courtDocuments = mutableListOf(document),
          nextCourtHearings = mutableListOf(),
          courtCharges = mutableListOf(),
        )
      }
      return document
    }

    @JvmStatic
    fun getBuildMirrorEnrichmentMetadataTestParameters() = listOf(
      Arguments.of(DestinationType.PRISON, CourtDocumentType.REMAND_WARRANT, COURT_CODE, CASE_REFERENCE, "PRISON", "REMAND_WARRANT", COURT_CODE, arrayOf(CASE_REFERENCE)),
      Arguments.of(DestinationType.PRISON, CourtDocumentType.PRISON_COURT_REGISTER, null, CASE_REFERENCE, "PRISON", "PRISON_COURT_REGISTER", "NOT FOUND", arrayOf(CASE_REFERENCE)),
      Arguments.of(null, CourtDocumentType.PRISON_COURT_REGISTER, COURT_CODE, CASE_REFERENCE, "NOT FOUND", "PRISON_COURT_REGISTER", COURT_CODE, arrayOf(CASE_REFERENCE)),
      Arguments.of(null, CourtDocumentType.REMAND_WARRANT, null, CASE_REFERENCE, "NOT FOUND", "REMAND_WARRANT", "NOT FOUND", arrayOf(CASE_REFERENCE)),
      Arguments.of(DestinationType.PRISON, CourtDocumentType.REMAND_WARRANT, COURT_CODE, null, "PRISON", "REMAND_WARRANT", COURT_CODE, emptyArray<String>()),
      Arguments.of(DestinationType.PRISON, CourtDocumentType.PRISON_COURT_REGISTER, null, null, "PRISON", "PRISON_COURT_REGISTER", "NOT FOUND", emptyArray<String>()),
      Arguments.of(null, CourtDocumentType.PRISON_COURT_REGISTER, COURT_CODE, null, "NOT FOUND", "PRISON_COURT_REGISTER", COURT_CODE, emptyArray<String>()),
      Arguments.of(null, CourtDocumentType.REMAND_WARRANT, null, null, "NOT FOUND", "REMAND_WARRANT", "NOT FOUND", emptyArray<String>()),
      Arguments.of(DestinationType.PRISON, CourtDocumentType.REMAND_WARRANT, COURT_CODE, "${CASE_REFERENCE},${CASE_REFERENCE_2}", "PRISON", "REMAND_WARRANT", COURT_CODE, arrayOf(CASE_REFERENCE, CASE_REFERENCE_2)),
    )
  }
}
