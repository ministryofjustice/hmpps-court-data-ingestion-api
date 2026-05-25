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

    repository.findByDocumentIdAndFormatIdAndFormatVersionAndExtractorVersion(
      documentId,
      model.id,
      model.version,
      extractorVersion,
    )?.let { return it }

    val bytes = documentApi.downloadFile(documentId)
    val sha = sha256(bytes)

    val entity = if (!bytes.looksLikePdf()) {
      log.warn("Skipping non-PDF document {} (missing %PDF- signature)", documentId)
      ExtractionResultEntity(
        documentId = documentId,
        contentSha256 = sha,
        formatId = model.id,
        formatVersion = model.version,
        extractorVersion = extractorVersion,
        status = "SKIPPED_NON_PDF",
        result = objectMapper.writeValueAsString(mapOf("reason" to "missing %PDF- signature")),
      )
    } else {
      runCatching {
        val out = pipeline.extract(ByteArrayInputStream(bytes), documentId.toString(), model)
        ExtractionResultEntity(
          documentId = documentId,
          contentSha256 = sha,
          formatId = model.id,
          formatVersion = model.version,
          extractorVersion = extractorVersion,
          status = "OK",
          pageCount = out.pageCount,
          fieldCount = out.fieldCount,
          result = objectMapper.writeValueAsString(
            mapOf(
              "header" to out.headerFields,
              "offences" to out.offenceBlocks,
              "labelSignature" to out.labelSignature,
            ),
          ),
        )
      }.getOrElse { ex ->
        log.warn("Extraction failed for document {}", documentId, ex)
        ExtractionResultEntity(
          documentId = documentId,
          contentSha256 = sha,
          formatId = model.id,
          formatVersion = model.version,
          extractorVersion = extractorVersion,
          status = "FAILED",
          result = objectMapper.writeValueAsString(mapOf("error" to (ex.message ?: ex.javaClass.simpleName))),
        )
      }
    }

    return try {
      repository.save(entity)
    } catch (race: DataIntegrityViolationException) {
      repository.findByDocumentIdAndFormatIdAndFormatVersionAndExtractorVersion(
        documentId,
        model.id,
        model.version,
        extractorVersion,
      ) ?: throw race
    }
  }

  private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}
