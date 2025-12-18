package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.CorePersonApiClient
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.IdentifiedWarrantFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.WarrantFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.CourtDataIngestionEvent
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.IdentifiedWarrantFileRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.WarrantFileRepository

@Service
class CourtDataIngestionService(
  val warrantFileRepository: WarrantFileRepository,
  val identifiedWarrantFileRepository: IdentifiedWarrantFileRepository,
  val corePersonApiClient: CorePersonApiClient,
) {

  @Transactional
  fun receiveMessage(message: CourtDataIngestionEvent) {
    val warrantFile = warrantFileRepository.save(
      WarrantFile(
        defendantId = message.defendantId,
        externalFileId = message.fileId,
      ),
    )
    val person = try {
      corePersonApiClient.getPerson(message.defendantId)
    } catch (e: WebClientResponseException) {
      if (HttpStatus.NOT_FOUND.isSameCodeAs(e.statusCode)) {
        return
      } else {
        throw e
      }
    }

    person.identifiers.prisonNumbers.forEach {
      identifiedWarrantFileRepository.save(
        IdentifiedWarrantFile(
          warrantFile = warrantFile,
          prisonerNumber = it,
        ),
      )
      // TODO Raise event for identified document.
    }
  }
}
