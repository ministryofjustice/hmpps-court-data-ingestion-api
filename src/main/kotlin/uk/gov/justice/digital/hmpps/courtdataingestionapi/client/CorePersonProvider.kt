package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.service.annotation.GetExchange
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.coreperson.CorePersonCanonicalRecord
import java.util.UUID

interface CorePersonProvider {

  @GetExchange(value = "/person/commonplatform/{defendantId}")
  fun getPersonByCommonPlatformId(@PathVariable defendantId: UUID): CorePersonCanonicalRecord

  @GetExchange(value = "/person/prison/{prisonerNumber}")
  fun getPersonByPrisonerNumber(@PathVariable prisonerNumber: String): CorePersonCanonicalRecord?
}
