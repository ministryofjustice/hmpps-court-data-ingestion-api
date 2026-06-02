package uk.gov.justice.digital.hmpps.courtdataingestionapi.prisonemail

object PrisonEmailNormaliser {
  fun normalise(email: String?): String? = email
    ?.trim()
    ?.lowercase()
    ?.removePrefix("mailto:")
    ?.replace("\u00A0", "")
    ?.takeIf { it.isNotBlank() }
}
