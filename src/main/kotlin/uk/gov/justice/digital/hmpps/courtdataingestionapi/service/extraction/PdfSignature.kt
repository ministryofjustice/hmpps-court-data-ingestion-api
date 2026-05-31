package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

private val PDF_MAGIC = "%PDF-".toByteArray(Charsets.US_ASCII)

/** True when the bytes begin with the PDF signature, regardless of declared extension or mime type. */
internal fun ByteArray.looksLikePdf(): Boolean = size >= PDF_MAGIC.size && PDF_MAGIC.indices.all { this[it] == PDF_MAGIC[it] }
