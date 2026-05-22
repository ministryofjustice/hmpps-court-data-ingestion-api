package uk.gov.justice.digital.hmpps.courtdataingestionapi.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "extraction_result")
class ExtractionResultEntity(
  @Id
  var id: UUID = UUID.randomUUID(),
  var documentId: UUID,
  var contentSha256: String,
  var formatId: String,
  var formatVersion: Int,
  var extractorVersion: String,
  var status: String,
  var pageCount: Int? = null,
  var fieldCount: Int? = null,
  @JdbcTypeCode(SqlTypes.JSON)
  var result: String,
  var extractedAt: LocalDateTime = LocalDateTime.now(),
)
