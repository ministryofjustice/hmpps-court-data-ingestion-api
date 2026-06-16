package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import tools.jackson.databind.JsonNode
import uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.DocumentSearchRequest
import kotlin.reflect.KClass

class DocumentTypeOrMetadataCriteriaRequiredValidator : ConstraintValidator<DocumentTypeOrMetadataCriteriaRequired, DocumentSearchRequest> {
  override fun isValid(value: DocumentSearchRequest, context: ConstraintValidatorContext): Boolean {
    if (value.documentTypes != null) {
      return true
    }

    if (value.metadata.isNotNullWithAtLeastOneProperty()) {
      return true
    }

    return false
  }

  private fun JsonNode?.isNotNullWithAtLeastOneProperty() = this != null && this.isObject && this.size() > 0
}

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [DocumentTypeOrMetadataCriteriaRequiredValidator::class])
annotation class DocumentTypeOrMetadataCriteriaRequired(
  val message: String = "Document type or metadata criteria must be supplied.",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)
