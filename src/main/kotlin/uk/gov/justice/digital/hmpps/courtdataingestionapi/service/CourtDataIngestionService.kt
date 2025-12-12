package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.WarrantFile
import uk.gov.justice.digital.hmpps.courtdataingestionapi.listener.CourtDataIngestionEvent
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.WarrantFileRepository

@Service
class CourtDataIngestionService(
  val warrantFileRepository: WarrantFileRepository,
) {

  @Transactional
  fun receiveMessage(message: CourtDataIngestionEvent) {
    warrantFileRepository.save(
      WarrantFile(
        defendantId = message.defendantId,
        externalFileId = message.fileId,
      ),
    )
  }
}
