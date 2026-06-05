package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.document

import java.util.UUID

fun interface PrisonDocumentContentService {
  fun loadBytes(prisonDocumentId: UUID): ByteArray?
}
