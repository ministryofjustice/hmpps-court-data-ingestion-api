package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import jakarta.validation.ValidationException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.CourtDocumentDayCount
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocument
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentDay
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentHearing
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.api.PrisonCourtDocumentWeek
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonCourtDocumentRepository
import java.time.DayOfWeek
import java.time.LocalDate

@Service
class PrisonCourtDocumentService(
  private val repository: PrisonCourtDocumentRepository,
  private val rollService: PrisonRollService,
  @Value("\${prison-court-documents.week-row-threshold:100}") private val weekRowThreshold: Int,
  @Value("\${prison-court-documents.day-row-cap:500}") private val dayRowCap: Int,
) {

  fun weekFor(prison: String, date: LocalDate, refreshRoll: Boolean = false): PrisonCourtDocumentWeek {
    val prisonCode = prison.uppercase()
    val from = weekContaining(date)
    val to = from.plusDays(6)
    val roll = roll(prisonCode, refreshRoll)

    val counted = repository.countByDay(roll.prisonerNumbers, from, to).associateBy { it.date }
    val days = (0..6).map { offset ->
      val date = from.plusDays(offset.toLong())
      counted[date] ?: CourtDocumentDayCount(date = date, documents = 0, people = 0)
    }
    val totalDocuments = days.sumOf { it.documents }

    return PrisonCourtDocumentWeek(
      prisonCode = prisonCode,
      from = from,
      to = to,
      rollTakenAt = roll.takenAt,
      rollSize = roll.prisonerNumbers.size,
      days = days,
      totalDocuments = totalDocuments,
      rows = if (totalDocuments <= weekRowThreshold) {
        repository.findRows(roll.prisonerNumbers, from, to, weekRowThreshold)
      } else {
        null
      },
      rowThreshold = weekRowThreshold,
      previousWeek = from.minusWeeks(1),
      nextWeek = from.plusWeeks(1).takeIf { it <= LocalDate.now().with(DayOfWeek.MONDAY) },
    )
  }

  fun dayFor(prison: String, date: LocalDate, refreshRoll: Boolean = false): PrisonCourtDocumentDay {
    val prisonCode = prison.uppercase()

    if (date.isAfter(LocalDate.now())) {
      throw ValidationException("That day has not happened yet")
    }

    val roll = roll(prisonCode, refreshRoll)

    val rows = repository.findRows(roll.prisonerNumbers, date, date, dayRowCap)
    val totalDocuments = repository.countDocuments(roll.prisonerNumbers, date, date)

    return PrisonCourtDocumentDay(
      prisonCode = prisonCode,
      date = date,
      rollTakenAt = roll.takenAt,
      rollSize = roll.prisonerNumbers.size,
      hearings = groupByHearing(rows),
      documentsWithoutAHearing = rows.filter { it.courtHearingId == null },
      totalDocuments = totalDocuments,
      truncated = totalDocuments > rows.size,
      prisonerNumbers = rows.map { it.prisonerNumber }.distinct(),
    )
  }

  private fun groupByHearing(rows: List<PrisonCourtDocument>) = rows
    .filter { it.courtHearingId != null }
    .groupBy { it.courtHearingId!! }
    .map { (hearingId, documents) ->
      val newest = documents.first()

      PrisonCourtDocumentHearing(
        courtHearingId = hearingId,
        prisonerNumber = newest.prisonerNumber,
        hearingDate = newest.hearingDate,
        hearingType = newest.hearingType,
        courtName = newest.courtName,
        caseReferences = documents.flatMap { it.caseReferences }.distinct(),
        receivedAt = newest.ingestedAt,
        documents = documents,
      )
    }
    .sortedByDescending { it.receivedAt }

  private fun weekContaining(date: LocalDate): LocalDate {
    val weekStart = date.with(DayOfWeek.MONDAY)

    if (weekStart.isAfter(LocalDate.now().with(DayOfWeek.MONDAY))) {
      throw ValidationException("That week has not happened yet")
    }

    return weekStart
  }

  private fun roll(prisonCode: String, refreshRoll: Boolean): PrisonRoll {
    if (refreshRoll) rollService.refresh(prisonCode)
    return rollService.rollFor(prisonCode)
  }

  init {
    if (weekRowThreshold < 1 || dayRowCap < 1) {
      throw ValidationException("Row limits must be positive")
    }
  }
}
