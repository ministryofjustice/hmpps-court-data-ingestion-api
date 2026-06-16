package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.documents.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

class BetweenValidator : ConstraintValidator<Between, Int> {
  private var min: Int = 0
  private var max: Int = 0

  override fun initialize(constraintAnnotation: Between) {
    min = constraintAnnotation.min
    max = constraintAnnotation.max
  }

  override fun isValid(value: Int, context: ConstraintValidatorContext): Boolean = value in min..max
}

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [BetweenValidator::class])
annotation class Between(
  val message: String,
  val groups: Array<KClass<*>> = [],
  val payload: Array<KClass<out Payload>> = [],
  val min: Int,
  val max: Int,
)
