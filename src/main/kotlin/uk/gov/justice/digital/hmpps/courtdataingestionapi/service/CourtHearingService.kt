package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import jakarta.persistence.EntityNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import tools.jackson.databind.ObjectMapper
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.CourtRegisterApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourtScheduleApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsCourthouseApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmctsPcrApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.courtdataingestionapi.config.TimezoneConfig
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtChargeEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtChargeResultEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtHearingEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtNextHearingEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.courtregister.CourtRegister
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtCaseDefendantRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtHearingRepository
import java.time.LocalDateTime
import java.util.UUID

@Service
@Transactional(readOnly = true)
class CourtHearingService(
  private val featureToggles: FeatureToggles,
  private val courtHearingRepository: CourtHearingRepository,
  private val hmctsCourtScheduleApiClient: HmctsCourtScheduleApiClient,
  private val hmctsCourthouseApiClient: HmctsCourthouseApiClient,
  private val courtRegisterApiClient: CourtRegisterApiClient,
  private val pcrApiClient: HmctsPcrApiClient,
  private val defendantRepository: CourtCaseDefendantRepository,
  private val objectMapper: ObjectMapper,
) {

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  @Transactional
  fun fetchAndCreateHearingData(
    courtDocumentEntity: CourtDocumentEntity,
  ) {
    if (courtDocumentEntity.hmctsCourtHearingId == null) return
    var courtHearing = runCatching {
      if (featureToggles.offenceDataEnabled) {
        fetchHearingAndOffenceData(courtDocumentEntity)
      } else {
        fetchHearingData(courtDocumentEntity)
      }
    }.onFailure {
      log.error("Error while fetching hearing data", it)
      return
    }.getOrNull()

    if (courtHearing == null) {
      return
    }

    val existing = courtHearingRepository.findFirstByHmctsCourtHearingId(courtDocumentEntity.hmctsCourtHearingId!!)
    if (existing != null) {
      courtHearing = courtHearing.copy(
        id = existing.id,
        createdAt = existing.createdAt,
      )
    }
    courtHearing.updatedAt = LocalDateTime.now()
    courtDocumentEntity.courtHearing = courtHearingRepository.save(courtHearing)
  }

  private fun fetchHearingAndOffenceData(courtDocumentEntity: CourtDocumentEntity): CourtHearingEntity? {
    val caseReferences = courtDocumentEntity.courtDocumentCases.map { it.caseReference }
    val defendants = defendantRepository.findAllByMasterDefendantId(courtDocumentEntity.masterDefendantId).filter { caseReferences.contains(it.caseReference) }

    if (defendants.isEmpty()) {
      log.error("No CourtCaseDefendantEntity could be found for CourtDocumentEntity ${courtDocumentEntity.id}")
      return null
    }

    val results = defendants.map {
      pcrApiClient.get(it.caseReference, courtDocumentEntity.hmctsCourtHearingId!!, it.defendantId) to it
    }

    val json = objectMapper.createArrayNode()
    results.forEach { (result, _) ->
      json.addAll(result.raw)
    }

    if (results.isEmpty()) {
      log.error("No hearing data found from HMCTS PCR API ${courtDocumentEntity.id}")
      return null
    }

    val pcrs = results.flatMap { (result, defendant) -> result.data.map { pcr -> pcr to defendant } }
    val hearing = pcrs.first().first.hearing
    val courtId = hearing.courtDetails.court.courtHouseId
    val courtRegister = getCourtRegister(courtId)
    return CourtHearingEntity(
      hmctsCourtId = courtId,
      courtName = getCourtName(courtId, courtRegister),
      hmppsCourtId = courtRegister?.courtId,
      hearingType = hearing.hearingType,
      hearingDate = hearing.hearingDate,
      hmctsCourtHearingId = courtDocumentEntity.hmctsCourtHearingId!!,
      apiResponse = objectMapper.writeValueAsString(json),
      courtDocuments = mutableListOf(courtDocumentEntity),
      courtCharges = pcrs.flatMap { (pcr, defendant) ->
        pcr.offences.map { offence ->
          CourtChargeEntity(
            defendantId = defendant.defendantId,
            masterDefendantId = defendant.masterDefendantId,
            listingNumber = offence.listingNumber,
            offenceLegislation = offence.offenceLegislation,
            pleaDate = offence.pleaDate,
            pleaValue = offence.pleaValue,
            startDate = offence.startDate,
            endDate = offence.endDate,
            title = offence.title,
            wording = offence.title,
            code = offence.code,
            results = offence.results.map {
              val (code, description) = it.resultDescription.split(" - ", limit = 2)
              CourtChargeResultEntity(
                resultCode = code,
                resultDescription = description,
              )
            },
          )
        }
      }.toMutableList(),
      nextCourtHearings = pcrs.map { (pcr, defendant) ->
        val nextHearing = pcr.hearing.nextHearing
        val courtId = nextHearing.court.courtHouseId
        val courtRegister = getCourtRegister(courtId)
        CourtNextHearingEntity(
          defendantId = defendant.defendantId,
          masterDefendantId = defendant.masterDefendantId,
          hmctsCourtId = courtId,
          courtName = getCourtName(courtId, courtRegister),
          hmppsCourtId = courtRegister?.courtId,
          dateTime = nextHearing.dateTime.withZoneSameInstant(TimezoneConfig.TIMEZONE).toLocalDateTime(),
          hearingId = nextHearing.hearingId,
        )
      }.toMutableList(),
    )
  }

  private fun fetchHearingData(courtDocumentEntity: CourtDocumentEntity): CourtHearingEntity? {
    val caseReferences = courtDocumentEntity.courtDocumentCases.map { it.caseReference }
    val hearings = caseReferences.flatMap { hmctsCourtScheduleApiClient.getCourtSchedule(it).courtSchedule.flatMap { schedule -> schedule.hearings } }
    val hearing = hearings.find { it.hearingId == courtDocumentEntity.hmctsCourtHearingId }

    if (hearing == null || hearing.courtSittings.isEmpty()) {
      log.error("Could not find hearing with sittings from id: ${courtDocumentEntity.hmctsCourtHearingId}")
      return null
    }
    val courtId = hearing.courtSittings[0].courtHouse
    val hearingDate = hearing.courtSittings[0].sittingStart
    val courtRegister = getCourtRegister(courtId)

    return CourtHearingEntity(
      hmctsCourtId = courtId,
      courtName = getCourtName(courtId, courtRegister),
      hmppsCourtId = courtRegister?.courtId,
      hearingType = hearing.hearingType,
      hearingDate = hearingDate.toLocalDate(),
      hmctsCourtHearingId = courtDocumentEntity.hmctsCourtHearingId!!,
      courtDocuments = mutableListOf(courtDocumentEntity),
      courtCharges = mutableListOf(),
      nextCourtHearings = mutableListOf(),
    )
  }

  private fun getCourtRegister(hmctsCourtId: UUID): CourtRegister? {
    runCatching {
      return courtRegisterApiClient.getCourtRegisterByHmctsId(hmctsCourtId)
    }.onFailure {
      if (it is WebClientResponseException && it.statusCode.value() == 404) {
        log.warn("Court register missing mapping from hmcts court id $hmctsCourtId")
      } else {
        log.error("Unable to get court register API data from HMCTS courtId", it)
      }
    }
    return null
  }

  private fun getCourtName(hmctsCourtId: UUID, courtRegister: CourtRegister?): String {
    if (courtRegister != null) {
      return courtRegister.courtName
    }

    val courthouse = hmctsCourthouseApiClient.getCourthouse(hmctsCourtId)
    return courthouse.courtHouseName
  }

  fun getCourtHearing(courtHearingId: UUID, prisonerNumber: String): CourtHearing {
    val courtHearing = courtHearingRepository.findFirstByHmctsCourtHearingId(courtHearingId)
      ?: throw EntityNotFoundException("Hearing not found $courtHearingId")
    return courtHearing.toCourtHearing(prisonerNumber)
  }

  fun getCourtHearingsByPrisoner(prisonerNumber: String): List<CourtHearing> = courtHearingRepository.findByCourtDocumentsPrisonerNumber(prisonerNumber).map { it.toCourtHearing(prisonerNumber) }
}
