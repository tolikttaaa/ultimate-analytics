package com.ttaaa.ultimate.app.ingestion

import com.ttaaa.ultimate.analysis.ANALYSIS_VERSION
import com.ttaaa.ultimate.analysis.analyze
import com.ttaaa.ultimate.analysis.metrics.windowMetrics
import com.ttaaa.ultimate.app.IntegrationTest
import com.ttaaa.ultimate.app.geozone.GeozoneRepository
import com.ttaaa.ultimate.app.metrics.MetricsSnapshotRepository
import com.ttaaa.ultimate.app.segment.SegmentRepository
import com.ttaaa.ultimate.app.segment.segmentOf
import com.ttaaa.ultimate.app.session.EffortRepository
import com.ttaaa.ultimate.app.session.LapRepository
import com.ttaaa.ultimate.app.session.SampleRepository
import com.ttaaa.ultimate.app.session.SessionRepository
import com.ttaaa.ultimate.app.session.asStored
import com.ttaaa.ultimate.app.storage.RawFileStore
import com.ttaaa.ultimate.domain.AnalysisParameters
import com.ttaaa.ultimate.domain.Geozone
import com.ttaaa.ultimate.domain.GeozoneShape
import com.ttaaa.ultimate.domain.MetricsScope
import com.ttaaa.ultimate.domain.RecordingMode
import com.ttaaa.ultimate.domain.Surface
import com.ttaaa.ultimate.domain.SurfaceSource
import com.ttaaa.ultimate.domain.TimeRange
import com.ttaaa.ultimate.fit.FitParser
import com.ttaaa.ultimate.fit.Golden
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.measureTimeMillis

class UploadServiceTest : IntegrationTest() {

    @Autowired private lateinit var uploads: UploadService
    @Autowired private lateinit var sessions: SessionRepository
    @Autowired private lateinit var samples: SampleRepository
    @Autowired private lateinit var laps: LapRepository
    @Autowired private lateinit var segments: SegmentRepository
    @Autowired private lateinit var efforts: EffortRepository
    @Autowired private lateinit var snapshots: MetricsSnapshotRepository
    @Autowired private lateinit var geozones: GeozoneRepository
    @Autowired private lateinit var rawFiles: RawFileStore
    @Autowired private lateinit var params: AnalysisParameters

    private val twoLaps = "22296100401_ACTIVITY.fit"
    private val other = "24557963847_ACTIVITY.fit"

    private fun golden(name: String) = UploadedFile(name, Files.readAllBytes(Golden.fitFile(name)))

    private fun analysisOf(file: UploadedFile) = analyze(FitParser.parse(file.content.inputStream()), params)

    private fun zipOf(name: String, vararg entries: Pair<String, ByteArray>): UploadedFile {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            entries.forEach { (entryName, content) ->
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return UploadedFile(name, bytes.toByteArray())
    }

    @Test
    fun `stores a new session with everything derived from it`() {
        val file = golden(twoLaps)
        val expected = analysisOf(file)

        val result = uploads.upload(listOf(file)).single()

        result.status shouldBe UploadStatus.CREATED
        result.surface shouldBe Surface.UNKNOWN
        result.needsSurface shouldBe true
        val session = sessions.findById(result.sessionId.shouldNotBeNull()).shouldNotBeNull()
        session.fileSha256 shouldBe MessageDigest.getInstance("SHA-256").digest(file.content).toHexString()
        session.fileName shouldBe twoLaps
        session.startTime shouldBe expected.startTime
        session.elapsedSec shouldBe expected.lastT
        session.recordingMode shouldBe RecordingMode.SMART
        session.analysisVersion shouldBe ANALYSIS_VERSION
        session.sport shouldBe "disc_golf"
        rawFiles.read(session.fileSha256) shouldBe file.content

        val storedSamples = samples.findBySession(session.id, session.startTime)
        storedSamples shouldBe expected.samples.map { it.asStored() }
        laps.findBySession(session.id) shouldBe expected.laps
        val lapSegments = segments.findBySession(session.id)
        lapSegments.map { it.label to it.range } shouldBe listOf("Lap 1" to TimeRange(0, 3994), "Lap 2" to TimeRange(3994, 7800))
        val storedEfforts = efforts.findBySession(session.id)
        storedEfforts.map { it.effort } shouldBe expected.efforts
        storedEfforts.forEach { it.segmentId shouldBe segmentOf(it.effort, lapSegments)?.id }

        // The cache equals the metrics computed from what is stored.
        val effortsAsStored = storedEfforts.map { it.effort }
        snapshots.find(MetricsScope.SESSION, session.id).shouldNotBeNull().metrics shouldBe
            windowMetrics(storedSamples, effortsAsStored, TimeRange(0, session.elapsedSec), params)
        lapSegments.forEach { segment ->
            snapshots.find(MetricsScope.SEGMENT, segment.id).shouldNotBeNull().metrics shouldBe
                windowMetrics(storedSamples, effortsAsStored, segment.range, params)
        }
    }

    @Test
    fun `a file uploaded again is a duplicate and stores nothing new`() {
        val first = uploads.upload(listOf(golden(twoLaps))).single()

        val again = uploads.upload(listOf(golden(twoLaps))).single()

        again.status shouldBe UploadStatus.DUPLICATE
        again.sessionId shouldBe first.sessionId
        sessions.count() shouldBe 1
    }

    @Test
    fun `unpacks zip archives and imports every FIT file in them`() {
        val archive = zipOf(
            "export.zip",
            "activities/$twoLaps" to golden(twoLaps).content,
            "__MACOSX/activities/._$twoLaps" to byteArrayOf(0, 5, 22, 7),
            "readme.txt" to "not a FIT file".toByteArray(),
            other to golden(other).content,
        )

        val results = uploads.upload(listOf(archive, zipOf("empty.zip", "readme.txt" to "nothing".toByteArray())))

        results.map { it.fileName to it.status } shouldBe listOf(
            "export.zip/activities/$twoLaps" to UploadStatus.CREATED,
            "export.zip/$other" to UploadStatus.CREATED,
            "empty.zip" to UploadStatus.FAILED,
        )
        results.last().error shouldBe "No .fit file in the archive"
        sessions.findById(results.first().sessionId!!)!!.fileName shouldBe twoLaps
    }

    @Test
    fun `a failing file fails on its own and stores nothing`() {
        val results = uploads.upload(listOf(UploadedFile("broken.fit", "not a FIT file".toByteArray()), golden(other)))

        results.map { it.status } shouldBe listOf(UploadStatus.FAILED, UploadStatus.CREATED)
        results.first().error shouldBe "Not a FIT file"
        sessions.count() shouldBe 1
        Files.list(RAW_DIR).use { it.count() } shouldBe 1
    }

    @Test
    fun `classifies the surface by the geozone at the session start`() {
        val file = golden(other)
        val start = analysisOf(file).referencePosition.shouldNotBeNull()
        val beach = Geozone(UUID.randomUUID(), "Beach courts", Surface.SAND, GeozoneShape.Circle(start, radiusM = 200.0))
        geozones.insert(beach)

        val result = uploads.upload(listOf(file)).single()

        result.surface shouldBe Surface.SAND
        result.needsSurface shouldBe false
        with(sessions.findById(result.sessionId!!)!!) {
            geozoneId shouldBe beach.id
            surfaceSource shouldBe SurfaceSource.GEOZONE
        }
    }

    @Test
    fun `uploads the longest golden file within 2 seconds`() {
        uploads.upload(listOf(golden(other))) // warm-up
        val longest = golden("24249547764_ACTIVITY.fit") // 157 minutes

        val millis = measureTimeMillis { uploads.upload(listOf(longest)).single().status shouldBe UploadStatus.CREATED }

        println("Upload of the longest golden file took $millis ms")
        millis shouldBeLessThan 2_000
    }
}
