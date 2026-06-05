package uk.gov.justice.digital.hmpps.courtdataingestionapi.util

import java.security.MessageDigest

object Sha256 {
  fun hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }
}
