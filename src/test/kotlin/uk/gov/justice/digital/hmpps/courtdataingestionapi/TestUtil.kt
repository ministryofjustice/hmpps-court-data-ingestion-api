package uk.gov.justice.digital.hmpps.courtdataingestionapi

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.text.SimpleDateFormat

class TestUtil private constructor() {
  companion object {

    fun objectMapper(): ObjectMapper = jacksonObjectMapper().apply {
      registerModule(JavaTimeModule())
      dateFormat = SimpleDateFormat("yyyy-MM-dd")
      configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }
  }
}
