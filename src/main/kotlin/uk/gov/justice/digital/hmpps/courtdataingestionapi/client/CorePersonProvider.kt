package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.coreperson.CorePersonCanonicalRecord
import java.util.UUID

interface CorePersonProvider {

  fun getPersonByCommonPlatformId(defendantId: UUID): CorePersonCanonicalRecord

  fun getPersonByPrisonerNumber(prisonerNumber: String): CorePersonCanonicalRecord?
}
