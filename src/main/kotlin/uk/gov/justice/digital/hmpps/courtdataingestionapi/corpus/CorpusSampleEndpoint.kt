package uk.gov.justice.digital.hmpps.courtdataingestionapi.corpus

import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.lang.Nullable
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(prefix = "extraction.corpus-sampler", name = ["enabled"], havingValue = "true")
@Endpoint(id = "corpussample")
class CorpusSampleEndpoint(
  private val service: CorpusSampleService,
) {
  @ReadOperation
  fun sample(
    @Nullable eventType: String?,
    @Nullable courtDocumentType: String?,
    @Nullable size: Int?,
    @Nullable binSize: Int?,
    @Nullable seed: Double?,
  ): CorpusSample = service.sample(
    eventType = eventType,
    courtDocumentType = courtDocumentType,
    size = size ?: DEFAULT_SIZE,
    binSize = binSize ?: DEFAULT_BIN_SIZE,
    seed = seed,
  )

  companion object {
    private const val DEFAULT_SIZE = 10
    private const val DEFAULT_BIN_SIZE = 10
  }
}
