package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill.AddressedPrisonReresolveBackfill
import uk.gov.justice.digital.hmpps.courtdataingestionapi.backfill.BackfillRunner
import uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail.PrisonEmailNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.DeliveryCategory
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.DeliveryCategoryRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.UnclassifiedAddress
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.UnclassifiedAddressRepository

data class LocationCount(val prisonCode: String, val people: Int)

data class ClassifyAddressPreview(
  val emailAddress: String,
  val category: DeliveryCategory,
  val prisonCode: String?,
  val documentsAffected: Int,
  val peopleAffected: Int,
  val currentLocations: List<LocationCount>,
  /** True when the distribution is drawn from a sample rather than every person. */
  val locationsSampled: Boolean,
  val replacesExisting: Boolean,
)

data class ClassifyAddressResult(
  val mappingId: String,
  val documentsQueued: Int,
  val backfillRunId: String?,
  val backfillOutcome: String,
)

class UnknownCategoryException(code: String) : IllegalArgumentException("No category with code $code")

@Service
class DeliveryAddressAdminService(
  private val addressRepository: UnclassifiedAddressRepository,
  private val mappingRepository: PrisonEmailMappingRepository,
  private val categoryRepository: DeliveryCategoryRepository,
  private val prisonerSearchService: PrisonerSearchService,
  private val runner: BackfillRunner,
  private val reresolveBackfill: AddressedPrisonReresolveBackfill,
) {

  fun unclassifiedAddresses(): List<UnclassifiedAddress> = addressRepository.findUnclassified()

  fun categories(): List<DeliveryCategory> = categoryRepository.findAll()

  fun createCategory(category: DeliveryCategory, createdBy: String?): DeliveryCategory? = categoryRepository.create(category, createdBy)

  /**
   * Dry run for the confirm page. Deliberately reports where the matched people are now: if they
   * are spread across the estate rather than held at the prison being mapped, the delivery address serves
   * a hub and mapping it to one establishment would put other prisons' paperwork in their view.
   */
  fun preview(emailAddress: String, categoryCode: String, prisonCode: String?): ClassifyAddressPreview {
    val normalised = PrisonEmailNormaliser.normalise(emailAddress) ?: emailAddress
    val category = categoryRepository.findByCode(categoryCode) ?: throw UnknownCategoryException(categoryCode)

    val peopleAffected = addressRepository.countDistinctPeopleFor(normalised)
    val sample = if (category.requiresPrisonCode) {
      addressRepository.prisonerNumbersFor(normalised, LOCATION_SAMPLE_LIMIT)
    } else {
      emptyList()
    }

    val locations = sample
      .mapNotNull { prisonerSearchService.getPrison(it) }
      .groupingBy { it }
      .eachCount()
      .map { LocationCount(it.key, it.value) }
      .sortedByDescending { it.people }

    return ClassifyAddressPreview(
      emailAddress = normalised,
      category = category,
      prisonCode = prisonCode,
      documentsAffected = addressRepository.countDocumentsFor(normalised),
      peopleAffected = peopleAffected,
      currentLocations = locations,
      locationsSampled = peopleAffected > sample.size,
      replacesExisting = mappingRepository.findMappingByEmail(normalised) != null,
    )
  }

  /**
   * Creates the mapping, then asks the runner for the re-resolution sweep. The sweep is a
   * registered backfill with the usual database lock and heartbeat, so a second classification
   * while one is in flight reports that rather than starting a duplicate run: the sweep is
   * declarative, so the run already going will pick up this mapping too.
   */
  @Transactional
  fun classify(
    emailAddress: String,
    categoryCode: String,
    prisonCode: String?,
    triggeredBy: String?,
  ): ClassifyAddressResult {
    val normalised = PrisonEmailNormaliser.normalise(emailAddress) ?: emailAddress
    val category = categoryRepository.findByCode(categoryCode) ?: throw UnknownCategoryException(categoryCode)
    require(!category.requiresPrisonCode || !prisonCode.isNullOrBlank()) {
      "Category ${category.code} requires a prison code"
    }

    val documentsQueued = addressRepository.countDocumentsFor(normalised)
    val mapping = mappingRepository.upsert(
      normalisedEmail = normalised,
      categoryCode = category.code,
      prisonCode = prisonCode?.takeIf { category.requiresPrisonCode },
      createdBy = triggeredBy,
    )

    val run = runner.acquireLock(reresolveBackfill.id, triggeredBy)
    if (run != null) runner.runAsync(run.runId, reresolveBackfill)

    return ClassifyAddressResult(
      mappingId = mapping.id.toString(),
      documentsQueued = documentsQueued,
      backfillRunId = run?.runId?.toString(),
      backfillOutcome = if (run != null) "started" else "already-running",
    )
  }

  private companion object {
    /** Each sampled person is one prisoner-search lookup, so the preview stays interactive. */
    const val LOCATION_SAMPLE_LIMIT = 200
  }
}
