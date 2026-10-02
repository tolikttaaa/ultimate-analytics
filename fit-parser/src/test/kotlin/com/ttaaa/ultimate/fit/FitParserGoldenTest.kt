package com.ttaaa.ultimate.fit

import com.ttaaa.ultimate.domain.RawSession
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Duration
import kotlin.io.path.name

class FitParserGoldenTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("goldenFiles")
    fun `parses the golden file as approved`(fileName: String) {
        val raw = Files.newInputStream(Golden.dir.resolve(fileName)).use(FitParser::parse)

        Golden.verify(Golden.dir.resolve("expected/$fileName.parser.txt"), summary(raw))
    }

    /** Readable summary of the parsed session, plus a digest of every record so that any change is detected. */
    private fun summary(raw: RawSession): String = buildString {
        val records = raw.records
        appendLine("device: ${raw.device}")
        appendLine("sport: ${raw.sport} / ${raw.subSport}")
        appendLine("startTime: ${raw.startTime}")
        appendLine("localTzOffsetSec: ${raw.localTzOffsetSec}")
        appendLine("totalElapsedSec: ${raw.totalElapsedSec}")
        appendLine("totalTimerSec: ${raw.totalTimerSec}")
        appendLine("totalDistanceM: ${raw.totalDistanceM}")
        appendLine("startPosition: ${raw.startPosition}")
        appendLine("records: ${records.size}, ${records.first().timestamp} .. ${records.last().timestamp}")
        appendLine(
            "records without speed: ${records.count { it.speed == null }}, " +
                "position: ${records.count { it.position == null }}, " +
                "heart rate: ${records.count { it.heartRate == null }}, " +
                "distance: ${records.count { it.distanceM == null }}, " +
                "altitude: ${records.count { it.altitudeM == null }}",
        )
        val intervals = records.zipWithNext { a, b -> Duration.between(a.timestamp, b.timestamp).seconds }
        appendLine("record intervals (s=count): ${intervals.groupingBy { it }.eachCount().toSortedMap()}")
        appendLine("max speed: ${records.mapNotNull { it.speed }.maxOrNull()}")
        appendLine("laps:")
        raw.laps.forEach { appendLine("  ${it.startTime} .. ${it.endTime} ${it.trigger}") }
        appendLine("timer events:")
        raw.timerEvents.forEach { appendLine("  ${it.timestamp} ${it.type}") }
        appendLine("records sha256: ${sha256(records.joinToString("\n"))}")
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).toHexString()

    companion object {
        @JvmStatic
        fun goldenFiles(): List<String> = Golden.fitFiles.map { it.name }
    }
}
