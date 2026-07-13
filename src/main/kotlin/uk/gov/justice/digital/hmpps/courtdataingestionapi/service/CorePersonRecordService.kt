package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.CorePersonProvider
import java.util.UUID

enum class PrisonerLookupResult {
  NO_CORE_PERSON,
  NO_PRISON_NUMBER,
  MATCHED,
  MULTIPLE_PRISON_NUMBERS,
}

data class PrisonerLookup(
  val result: PrisonerLookupResult,
  val prisonerNumber: String? = null,
  val prisonerNumbers: List<String> = emptyList(),
)

@Service
class CorePersonRecordService(
  private val corePersonApiClient: CorePersonProvider,
) {

  fun findPrisonerByCommonPlatformId(commonPlatformId: UUID): PrisonerLookup {
    val person = try {
      corePersonApiClient.getPersonByCommonPlatformId(commonPlatformId)
    } catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
        return PrisonerLookup(PrisonerLookupResult.NO_CORE_PERSON)
      } else {
        throw e
      }
    }

    val prisonNumbers = person.identifiers.prisonNumbers
    return when {
      prisonNumbers.size == 1 -> PrisonerLookup(PrisonerLookupResult.MATCHED, prisonerNumber = prisonNumbers.first())
      prisonNumbers.size > 1 -> PrisonerLookup(PrisonerLookupResult.MULTIPLE_PRISON_NUMBERS, prisonerNumbers = prisonNumbers)
      else -> PrisonerLookup(PrisonerLookupResult.NO_PRISON_NUMBER)
    }
  }

  fun findDefendantIdsByPrisonerNumber(prisonerNumber: String): List<UUID> = corePersonApiClient.getPersonByPrisonerNumber(prisonerNumber)
    ?.identifiers
    ?.defendantIds
    ?.map { UUID.fromString(it) }
    .orEmpty()
}
