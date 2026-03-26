package uk.gov.justice.digital.hmpps.courtdataingestionapi.model.hmctsapi

import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.io.InputStream

class HmctsFile(
  private val bytes: ByteArray,
  private val name: String,
  private val originalFilename: String,
  private val contentType: String?,
) : MultipartFile {

  override fun getName() = name

  override fun getOriginalFilename() = originalFilename

  override fun getContentType() = contentType

  override fun isEmpty() = bytes.isEmpty()

  override fun getSize() = bytes.size.toLong()

  override fun getBytes() = bytes

  override fun getInputStream(): InputStream = ByteArrayInputStream(bytes)

  override fun transferTo(dest: java.io.File) {
    dest.writeBytes(bytes)
  }
}
