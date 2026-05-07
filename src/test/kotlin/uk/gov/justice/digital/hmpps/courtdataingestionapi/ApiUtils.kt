package uk.gov.justice.digital.hmpps.courtdataingestionapi

import org.springframework.core.ParameterizedTypeReference

inline fun <reified T : Any> typeReference() = object : ParameterizedTypeReference<T>() {}
