package com.ttaaa.ultimate.app.ingestion

import com.ttaaa.ultimate.analysis.analyze
import com.ttaaa.ultimate.app.session.SessionRepository
import com.ttaaa.ultimate.app.storage.RawFileStore
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Session
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.fit.FitParseException
import com.ttaaa.ultimate.fit.FitParser
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.util.unit.DataSize
import java.security.MessageDigest
import java.util.UUID

/** `app.upload.*` (spec 11). */
@ConfigurationProperties("app.upload")
data class UploadProperties(
    /** Per-file limit, also for each `.fit` inside a `.zip`. */
    val maxFileSize: DataSize = DataSize.ofMegabytes(20),
)

/**
 * The upload flow of spec 7.4. Every FIT file is handled on its own: one failing file does not fail the others.
 * A file is parsed and analysed before anything is stored, so a file that fails stores nothing (spec 11).
 */
@Service
class UploadService(
    private val sessions: SessionRepository,
    private val importer: SessionImporter,
    private val rawFiles: RawFileStore,
    private val params: AnalysisParameters,
    private val properties: UploadProperties,
) {

    fun upload(files: List<UploadedFile>): List<UploadResult> =
        unpack(files, properties.maxFileSize.toBytes()).map { item ->
            when (item) {
                is UploadItem.Rejected -> failed(item.displayName, item.reason)
                is UploadItem.Fit -> importLogged(item)
            }
        }

    private fun importLogged(fit: UploadItem.Fit): UploadResult {
        val started = System.nanoTime()
        val sha256 = sha256Hex(fit.content)
        val result = try {
            import(fit, sha256)
        } catch (e: FitParseException) {
            failed(fit.displayName, e.message ?: "Invalid FIT file")
        } catch (e: Exception) {
            log.error("Upload of {} (sha256 {}) failed", fit.displayName, sha256, e)
            failed(fit.displayName, "Unexpected error: ${e.message}")
        }
        log.info(
            "Upload file={} sha256={} status={} sessionId={} durationMs={}{}",
            fit.displayName, sha256, result.status, result.sessionId, (System.nanoTime() - started) / 1_000_000,
            result.error?.let { " error=$it" } ?: "",
        )
        return result
    }

    private fun import(fit: UploadItem.Fit, sha256: String): UploadResult {
        sessions.findIdBySha256(sha256)?.let { return duplicate(fit.displayName, it) }
        val raw = FitParser.parse(fit.content.inputStream())
        val analysis = analyze(raw, params)

        val newlyStored = rawFiles.store(sha256, fit.content)
        val session = try {
            importer.import(sha256, fit.fileName, raw, analysis)
        } catch (_: DuplicateKeyException) {
            // A concurrent upload of the same file won; its session owns the raw file.
            return duplicate(fit.displayName, sessions.findIdBySha256(sha256))
        } catch (e: Exception) {
            if (newlyStored) rawFiles.delete(sha256)
            throw e
        }
        return created(fit.displayName, session)
    }

    private fun created(fileName: String, session: Session) = UploadResult(
        fileName = fileName,
        status = UploadStatus.CREATED,
        sessionId = session.id,
        surface = session.surface,
        needsSurface = session.surface == Surface.UNKNOWN,
    )

    private fun duplicate(fileName: String, sessionId: UUID?) =
        UploadResult(fileName = fileName, status = UploadStatus.DUPLICATE, sessionId = sessionId)

    private fun failed(fileName: String, error: String) =
        UploadResult(fileName = fileName, status = UploadStatus.FAILED, error = error)

    private companion object {
        val log = LoggerFactory.getLogger(UploadService::class.java)

        fun sha256Hex(content: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(content).toHexString()
    }
}
