package com.ttaaa.ultimate.app.web

import com.ttaaa.ultimate.app.IntegrationTest
import com.ttaaa.ultimate.app.session.SampleRepository
import com.ttaaa.ultimate.app.session.SessionRepository
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import kotlin.system.measureTimeMillis

class SeriesApiTest : IntegrationTest() {

    @Autowired private lateinit var sessions: SessionRepository
    @Autowired private lateinit var samples: SampleRepository

    private val march = "22296100401_ACTIVITY.fit" // gaps at t = 0..5 and 7429..7437, laps end at 3994 and 7800

    @Test
    fun `the series has one entry per second with nulls in the gaps`() {
        val (id) = uploadGolden(march)
        val stored = samples.findBySession(id, sessions.findById(id)!!.startTime).associateBy { it.t }

        val series = get("/api/sessions/$id/series").json

        series["t"].size() shouldBe 7801
        listOf("speed", "speedRaw", "accel", "hr", "lat", "lon", "inPause", "interpolated").forEach {
            series[it].size() shouldBe 7801
        }
        series["t"][7800].asInt() shouldBe 7800
        series["speed"][5].isNull shouldBe true
        series["inPause"][7430].isNull shouldBe true
        series["speed"][6].floatValue() shouldBe stored.getValue(6).speed.toFloat()
        series["hr"][1000].asInt() shouldBe stored.getValue(1000).hr
        series["lat"][1000].asDouble() shouldBe stored.getValue(1000).position!!.lat
    }

    @Test
    fun `lists the efforts with their segments`() {
        val (id) = uploadGolden(march)
        val segments = get("/api/sessions/$id").json["segments"].values().associate { it["id"].asString() to it["endT"].asInt() }

        val efforts = get("/api/sessions/$id/efforts").json.values().toList()

        efforts.size shouldBe 26
        efforts.map { it["startT"].asInt() } shouldBe efforts.map { it["startT"].asInt() }.sorted()
        efforts.forEach { effort ->
            val segmentEnd = segments.getValue(effort["segmentId"].asString())
            (effort["startT"].asInt() < segmentEnd) shouldBe true
        }
        efforts.first()["metrics"].size() shouldBe 17 // all per-effort metrics of spec 6.4
        efforts.first()["metrics"]["peakSpeed"].asDouble() shouldBeGreaterThanOrEqual 4.5
    }

    @Test
    fun `computes the metrics of any window live`() {
        val (id) = uploadGolden(march)
        fun window(from: Int, to: Int) = get("/api/sessions/$id/metrics?from=$from&to=$to")

        window(600, 1200).json["time"]["elapsedSec"].asInt() shouldBe 600
        window(0, 3994).json["efforts"]["count"].asInt() + window(3994, 7800).json["efforts"]["count"].asInt() shouldBe 26
        // Live metrics of the whole session equal the cached session metrics.
        window(0, 7800).json shouldBe get("/api/sessions/$id").json["metrics"]

        window(1200, 600).statusCode shouldBe HttpStatus.BAD_REQUEST
        window(0, 7801).statusCode shouldBe HttpStatus.BAD_REQUEST
        get("/api/sessions/$id/metrics?from=0").statusCode shouldBe HttpStatus.BAD_REQUEST
    }

    @Test
    fun `meets the response time targets on the longest golden file`() {
        val (id) = uploadGolden("24249547764_ACTIVITY.fit") // 157 minutes
        repeat(3) { get("/api/sessions/$id/series"); get("/api/sessions/$id/metrics?from=0&to=4000") } // warm-up

        val seriesMs = measureTimeMillis { get("/api/sessions/$id/series").statusCode shouldBe HttpStatus.OK }
        val metricsMs = measureTimeMillis { get("/api/sessions/$id/metrics?from=600&to=6000").statusCode shouldBe HttpStatus.OK }

        println("series: $seriesMs ms, window metrics: $metricsMs ms")
        seriesMs shouldBeLessThan 300
        metricsMs shouldBeLessThan 150
    }
}
