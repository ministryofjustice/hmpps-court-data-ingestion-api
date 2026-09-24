package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import jakarta.validation.ValidationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.CourtDocumentEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentDay
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentDayCount
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentWeek
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtPerson
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.CourtDocumentRepository
import java.time.DayOfWeek
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class PrisonCourtDocumentService(
  private val courtDocumentRepository: CourtDocumentRepository,
  private val prisonerSearchService: PrisonerSearchService,
) {

  fun week(prisonCode: String, date: LocalDate): PrisonCourtDocumentWeek {
    val from = date.with(DayOfWeek.MONDAY)
    val thisWeek = LocalDate.now().with(DayOfWeek.MONDAY)
    if (from.isAfter(thisWeek)) throw ValidationException("That week has not happened yet")

    val roll = prisonerSearchService.getPrisonersInPrison(prisonCode)
    val documents = received(roll.map { it.prisonerNumber }, from, from.plusDays(7))
    val byDay = documents.groupBy { it.ingestionAt.toLocalDate() }

    return PrisonCourtDocumentWeek(
      prisonCode = prisonCode,
      from = from,
      to = from.plusDays(6),
      rollSize = roll.size,
      days = (0L..6L).map { offset ->
        val day = from.plusDays(offset)
        val onDay = byDay[day].orEmpty()
        PrisonCourtDocumentDayCount(day, onDay.size, onDay.mapNotNull { it.prisonerNumber }.distinct().size)
      },
      totalDocuments = documents.size,
      documents = documents.takeIf { it.size <= WEEK_LIST_LIMIT }?.map { it.toApi() },
      previousWeek = from.minusWeeks(1),
      nextWeek = from.plusWeeks(1).takeUnless { it.isAfter(thisWeek) },
    )
  }

  fun day(prisonCode: String, date: LocalDate): PrisonCourtDocumentDay {
    if (date.isAfter(LocalDate.now())) throw ValidationException("That day has not happened yet")

    val roll = prisonerSearchService.getPrisonersInPrison(prisonCode)
    val documents = received(roll.map { it.prisonerNumber }, date, date.plusDays(1))
    val (withHearing, withoutHearing) = documents.partition { it.courtHearing != null }

    return PrisonCourtDocumentDay(
      prisonCode = prisonCode,
      date = date,
      rollSize = roll.size,
      hearings = withHearing
        .groupBy { it.courtHearing!!.id }
        .map { (hearingId, onHearing) ->
          val hearing = onHearing.first().courtHearing!!
          PrisonCourtHearing(
            courtHearingId = hearingId,
            prisonerNumber = onHearing.first().prisonerNumber!!,
            hearingDate = hearing.hearingDate,
            hearingType = hearing.hearingType,
            courtName = hearing.courtName,
            caseReferences = onHearing.flatMap { it.caseReferences() }.distinct(),
            receivedAt = onHearing.first().ingestionAt,
            documents = onHearing.map { it.toApi() },
          )
        },
      documentsWithoutAHearing = withoutHearing.map { it.toApi() },
      people = documents.mapNotNull { it.prisonerNumber }.distinct().map { prisonerNumber ->
        val prisoner = roll.first { it.prisonerNumber == prisonerNumber }
        PrisonCourtPerson(prisonerNumber, prisoner.firstName, prisoner.lastName)
      },
    )
  }

  private fun received(roll: List<String>, from: LocalDate, to: LocalDate): List<CourtDocumentEntity> = if (roll.isEmpty()) {
    emptyList()
  } else {
    withoutDuplicates(
      courtDocumentRepository.findByPrisonerNumberInAndIngestionAtGreaterThanEqualAndIngestionAtLessThanOrderByIngestionAtDesc(
        roll,
        from.atStartOfDay(),
        to.atStartOfDay(),
      ),
    )
  }

  private fun withoutDuplicates(documents: List<CourtDocumentEntity>): List<CourtDocumentEntity> {
    val seen = mutableSetOf<String>()
    return documents
      .sortedBy { it.ingestionAt }
      .filter { document ->
        val hashes = listOfNotNull(document.extractedTextSha256, document.downloadedFileSha256).filter { it.isNotBlank() }
        val repeat = hashes.any { it in seen }
        seen += hashes
        !repeat
      }
      .sortedByDescending { it.ingestionAt }
  }

  private fun CourtDocumentEntity.caseReferences() = courtDocumentCases.map { it.caseReference }

  private fun CourtDocumentEntity.toApi() = PrisonCourtDocument(
    prisonDocumentId = prisonDocumentId,
    prisonerNumber = prisonerNumber!!,
    documentType = courtDocumentType,
    caseReferences = caseReferences(),
    addressedPrison = addressedPrison,
    receivedAt = ingestionAt,
  )

  private companion object {
    const val WEEK_LIST_LIMIT = 100
  }
}
