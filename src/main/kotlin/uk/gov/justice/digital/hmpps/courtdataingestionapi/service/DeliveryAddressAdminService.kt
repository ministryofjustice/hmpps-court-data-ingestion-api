package uk.gov.justice.digital.hmpps.courtdataingestionapi.service

import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.DeliveryCategory
import uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail.PrisonEmailNormaliser
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.DeliveryCategoryRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.PrisonEmailMappingRepository
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.UnclassifiedAddress
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.UnclassifiedAddressRepository

data class ClassifyAddressPreview(
  val emailAddress: String,
  val category: DeliveryCategory,
  val prisonCode: String?,
  val replacesExisting: Boolean,
)

data class ClassifyAddressResult(
  val mappingId: String,
  val backfillOutcome: String = BACKFILL_PENDING,
)

const val BACKFILL_PENDING = "pending"

class UnknownCategoryException(code: String) : IllegalArgumentException("No category with code $code")

@Service
class DeliveryAddressAdminService(
  private val addressRepository: UnclassifiedAddressRepository,
  private val mappingRepository: PrisonEmailMappingRepository,
  private val categoryRepository: DeliveryCategoryRepository,
) {

  fun addresses(classified: Boolean, categoryCode: String?): List<UnclassifiedAddress> = if (classified) {
    addressRepository.findClassified(categoryCode)
  } else {
    addressRepository.findUnclassified()
  }

  fun categories(): List<DeliveryCategory> = categoryRepository.findAll(Sort.by("name"))

  fun createCategory(category: DeliveryCategory, createdBy: String?): DeliveryCategory? = if (categoryRepository.existsById(category.code)) {
    null
  } else {
    categoryRepository.save(category.copy(createdBy = createdBy))
  }

  fun preview(emailAddress: String, categoryCode: String, prisonCode: String?): ClassifyAddressPreview {
    val normalised = PrisonEmailNormaliser.normalise(emailAddress) ?: emailAddress
    val category = categoryRepository.findByCode(categoryCode) ?: throw UnknownCategoryException(categoryCode)

    return ClassifyAddressPreview(
      emailAddress = normalised,
      category = category,
      prisonCode = prisonCode,
      replacesExisting = mappingRepository.findMappingByEmail(normalised) != null,
    )
  }

  @Transactional
  fun classify(
    emailAddress: String,
    categoryCode: String,
    prisonCode: String?,
    triggeredBy: String?,
  ): ClassifyAddressResult {
    val normalised = PrisonEmailNormaliser.normalise(emailAddress) ?: emailAddress
    val category = categoryRepository.findById(categoryCode).orElse(null) ?: throw UnknownCategoryException(categoryCode)
    require(!category.requiresPrisonCode || !prisonCode.isNullOrBlank()) {
      "Category ${category.code} requires a prison code"
    }

    val mapping = mappingRepository.upsert(
      normalisedEmail = normalised,
      categoryCode = category.code,
      prisonCode = prisonCode?.takeIf { category.requiresPrisonCode },
      createdBy = triggeredBy,
    )

    return ClassifyAddressResult(mappingId = mapping.id.toString())
  }
}
