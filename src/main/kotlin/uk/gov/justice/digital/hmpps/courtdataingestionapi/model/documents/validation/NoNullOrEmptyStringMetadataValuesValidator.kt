package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import tools.jackson.databind.JsonNode
import kotlin.reflect.KClass

class NoNullOrEmptyStringMetadataValuesValidator : ConstraintValidator<NoNullOrEmptyStringMetadataValues, JsonNode> {
  override fun isValid(value: JsonNode?, context: ConstraintValidatorContext): Boolean {
    if (value == null || !value.isObject) {
      return true
    }

    for (fieldName in value.propertyNames()) {
      if (value[fieldName].isNullOrEmpty()) {
        return false
      }
    }

    return true
  }

  private fun JsonNode.isNullOrEmpty() = !isTextual || asText().isEmpty()
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NoNullOrEmptyStringMetadataValuesValidator::class])
annotation class NoNullOrEmptyStringMetadataValues(
  val message: String = "Metadata property values must be non null or empty strings.",
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
)
