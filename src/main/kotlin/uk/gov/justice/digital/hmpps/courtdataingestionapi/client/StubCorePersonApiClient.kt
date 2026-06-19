package uk.gov.justice.digital.hmpps.courtdataingestionapi.client

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.coreperson.CorePersonCanonicalIdentifiers
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.coreperson.CorePersonCanonicalRecord
import java.util.UUID

@Component
@ConditionalOnProperty(
  name = ["core.person.api.stub.enabled"],
  havingValue = "true",
)
class StubCorePersonApiClient(
  @Value("\${core.person.api.stub.prisonernumbers}") private val prisonerNumbersProperty: String,
) : CorePersonProvider {

  override fun getPersonByCommonPlatformId(defendantId: UUID): CorePersonCanonicalRecord {
    val prisonerNumbers = prisonerNumbersProperty.split(",")
    val prisonerNumber = prisonerNumbers.random()
    return CorePersonCanonicalRecord(
      identifiers = CorePersonCanonicalIdentifiers(
        prisonNumbers = listOf(prisonerNumber),
      ),
    )
  }

  override fun getPersonByPrisonerNumber(prisonerNumber: String): CorePersonCanonicalRecord? = null

  private companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
