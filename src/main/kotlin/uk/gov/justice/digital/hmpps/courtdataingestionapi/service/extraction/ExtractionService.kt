package uk.gov.justice.digital.hmpps.courtdataingestionapi.service.extraction

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.courtdataingestionapi.client.HmppsDocumentManagementApi
import uk.gov.justice.digital.hmpps.courtdataingestionapi.entity.ExtractionResultEntity
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModel
import uk.gov.justice.digital.hmpps.courtdataingestionapi.extraction.format.FormatModelRegistry
import uk.gov.justice.digital.hmpps.courtdataingestionapi.repository.ExtractionResultRepository
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.UUID

@Service
class ExtractionService(
  private val documentApi: HmppsDocumentManagementApi,
  private val repository: ExtractionResultRepository,
  private val formatModels: FormatModelRegistry,
  private val pipeline: ExtractionPipeline,
  private val objectMapper: ObjectMapper,
  @Value("\${extraction.extractor-version:dev}") private val extractorVersion: String,
) {
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  fun extractAndStore(documentId: UUID): ExtractionResultEntity {
    val model = formatModels.active()

    val existing = repository.findByDocumentIdAndFormatIdAndFormatVersionAndExtractorVersion(
      documentId,
      model.id,
      model.version,
      extractorVersion,
    )
    if (existing != null && existing.status != STATUS_ERROR) return existing

    val entity = try {
      val bytes = documentApi.downloadFile(documentId)
      val sha = sha256(bytes)
      if (!bytes.looksLikePdf()) {
        result(documentId, model, sha, STATUS_SKIPPED, mapOf("reason" to "missing %PDF- signature"))
      } else {
        runCatching {
          val out = pipeline.extract(ByteArrayInputStream(bytes), documentId.toString(), model)
          result(
            documentId,
            model,
            sha,
            STATUS_OK,
            mapOf("header" to out.headerFields, "offences" to out.offenceBlocks, "labelSignature" to out.labelSignature),
            out.pageCount,
            out.fieldCount,
          )
        }.getOrElse { ex ->
          log.warn("Extraction failed for document {}", documentId, ex)
          result(documentId, model, sha, STATUS_FAILED, mapOf("error" to (ex.message ?: ex.javaClass.simpleName)))
        }
      }
    } catch (ex: Exception) {
      log.warn("Transient error fetching document {}, will retry after cooldown", documentId, ex)
      result(documentId, model, EMPTY_SHA, STATUS_ERROR, mapOf("error" to (ex.message ?: ex.javaClass.simpleName)))
    }

    return persist(existing, entity)
  }

  private fun result(
    documentId: UUID,
    model: FormatModel,
    sha: String,
    status: String,
    payload: Map<String, Any?>,
    pageCount: Int? = null,
    fieldCount: Int? = null,
  ) = ExtractionResultEntity(
    documentId = documentId,
    contentSha256 = sha,
    formatId = model.id,
    formatVersion = model.version,
    extractorVersion = extractorVersion,
    status = status,
    pageCount = pageCount,
    fieldCount = fieldCount,
    result = objectMapper.writeValueAsString(payload),
  )

  private fun persist(existing: ExtractionResultEntity?, entity: ExtractionResultEntity): ExtractionResultEntity = if (existing != null) {
    repository.save(existing.updateFrom(entity))
  } else {
    try {
      repository.save(entity)
    } catch (race: DataIntegrityViolationException) {
      repository.findByDocumentIdAndFormatIdAndFormatVersionAndExtractorVersion(
        entity.documentId,
        entity.formatId,
        entity.formatVersion,
        entity.extractorVersion,
      )?.let { repository.save(it.updateFrom(entity)) } ?: throw race
    }
  }

  private fun ExtractionResultEntity.updateFrom(source: ExtractionResultEntity) = apply {
    status = source.status
    contentSha256 = source.contentSha256
    pageCount = source.pageCount
    fieldCount = source.fieldCount
    result = source.result
    extractedAt = source.extractedAt
  }

  private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
    private const val STATUS_OK = "OK"
    private const val STATUS_FAILED = "FAILED"
    private const val STATUS_SKIPPED = "SKIPPED_NON_PDF"
    private const val STATUS_ERROR = "ERROR"
    private const val EMPTY_SHA = "0000000000000000000000000000000000000000000000000000000000000000"
  }
}
