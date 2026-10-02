# Ultimate Frisbee Training Analytics — POC Specification

Oct 1, 2026 · @Anatolii

## 1. Overview and goals

A single-user web application that stores Ultimate Frisbee training sessions recorded on a Garmin Forerunner watch and computes sprint-oriented analytics that Garmin Connect does not provide. Sessions enter the system only by manual upload of FIT files exported from Garmin Connect.

Goals of the POC:

- Store every uploaded session with its raw FIT file and a parsed 1 Hz time series.
- Classify each session by surface (grass or sand) using saved geozones, with manual fallback.
- Split a session into segments (drills, games, rest) using watch laps plus manual editing in the UI.
- Detect individual sprints ("efforts") and compute sprint metrics per session, per segment, per drill type and for any arbitrary time window.
- Visualise a session on synchronised speed / acceleration / heart-rate charts with a map, where selecting a window recomputes metrics.

Guiding principles:

- **Raw data is immutable.** The original FIT file is stored unchanged; everything else can be recomputed from it.
- **Analysis is pure.** All algorithms live in a framework-free Kotlin module that takes data in and returns results, so it can be unit-tested and used from Kotlin Notebook.
- **Honest metrics.** Every metric is defined in terms of what 1 Hz GPS can actually measure (see section 4).
- **Configurable thresholds.** Every detection threshold is a named parameter with a default, never a magic number.
- **Kotlin only on the backend.** No Python in the runtime or the analysis tooling.

## 2. Scope

The POC is a locally run, single-user tool: upload FIT, analyse, explore. Anything that depends on external services or on-watch software is out.

| Area | In scope (POC) | Out of scope |
| --- | --- | --- |
| Data ingestion | Manual upload of one or many `.fit` files via the UI (drag-and-drop), SHA-256 deduplication | Garmin Connect sync, intervals.icu, Strava, any API-based import |
| Data source | Standard FIT messages recorded by the watch (`session`, `lap`, `record`, `event`) | Connect IQ apps, raw accelerometer, developer fields, external sensors beyond HR |
| Surface detection | Geozone matching (circle or polygon), manual assignment for unknown locations | Heuristic or ML surface detection from motion data |
| Segmentation | Segments from watch laps, manual create / edit / merge / split / delete in the UI | Automatic segment suggestion (future), automatic drill-type classification |
| Analytics | Pause detection, effort (sprint) detection, metrics per session / segment / drill type / arbitrary window | Fatigue models, training load, ML |
| UI | 5 desktop screens (section 10) | Mobile layout, multi-user, sharing |
| Users and security | Single user, `local` profile with open access; Spring Security wired in so login can be added without refactoring | Login, accounts, roles, multi-tenancy (future `server` profile) |
| Infrastructure | `infra` module: DB migrations, Dockerfile, local docker compose, configs, scripts; Helm chart skeleton validated by `helm lint` | Running on a server, TLS, ingress, production monitoring |

## 3. Glossary

These terms are used with exactly these meanings throughout the spec and the code.

| Term | Meaning |
| --- | --- |
| **Session** | One uploaded training: one FIT file, one recorded activity. |
| **Sample** | One row of the 1 Hz time series: timestamp, position, speed, heart rate, distance, altitude. |
| **Lap** | A lap message recorded by the watch when the athlete presses the Lap button (or auto-lap). |
| **Segment** | A named, typed time interval inside a session (e.g. "Cutting drill 1v1", "Game 7v7", "Warm-up", "Rest"). Created from laps or manually. |
| **Drill type** | A user-defined category assigned to segments (e.g. `CUTTING_1V1`, `GAME`, `WARMUP`, `REST`). Metrics are aggregated across sessions by drill type. |
| **Effort** | One detected sprint: a contiguous interval of high acceleration and/or high speed (section 6). In Ultimate terms most efforts are cuts. |
| **Cut** | Ultimate term for a sprint to get open for a pass. In this system a cut is an effort; no separate entity. |
| **Pause** | An interval where the athlete is standing or walking slowly long enough to be excluded from "active" metrics. |
| **Active time** | Session (or window) duration minus pauses. |
| **Window** | Any arbitrary `[from, to]` time range inside a session, typically selected with the chart brush. |
| **Geozone** | A saved area (circle or polygon) with a name and a surface type, used to classify sessions. |
| **Surface** | `GRASS`, `SAND` or `UNKNOWN`. |
| **Analysis version** | Integer identifying the algorithm + parameter set used to compute stored results. |

## 4. Input data: Garmin FIT files

The only input is a FIT file exported from Garmin Connect ("Export Original", a `.zip` containing one `.fit`) or copied from the watch. It is parsed with the official Garmin FIT SDK for Java (`com.garmin:fit` on Maven Central).

### 4.1 FIT messages and fields used

| Message | Fields used | Purpose |
| --- | --- | --- |
| `file_id` | `type`, `manufacturer`, `product`, `serial_number`, `time_created` | Validation (must be an activity file), device info |
| `session` | `sport`, `sub_sport`, `start_time`, `total_elapsed_time`, `total_timer_time`, `total_distance`, `start_position_lat/long` | Session header, sanity checks |
| `lap` | `start_time`, `timestamp` (lap end), `total_elapsed_time`, `lap_trigger` | Initial segments |
| `record` | `timestamp`, `position_lat`, `position_long`, `enhanced_speed` (fallback `speed`), `heart_rate`, `distance`, `enhanced_altitude` | The 1 Hz time series |
| `event` | `event`, `event_type` (timer start / stop) | Detect timer pauses and recording gaps |

Conversions: positions are stored in semicircles, degrees = semicircles × 180 / 2^31. Speed is m/s. Timestamps are FIT epoch (1989-12-31 UTC) and must be converted to `Instant`.

The exact `sport` / `sub_sport` values written for the Ultimate Disc profile must be confirmed on real files in milestone M0. The parser must not reject other sports; it logs a warning instead.

### 4.2 Watch recording requirements (user-side setup)

- Data recording interval set to **Every Second** (not Smart recording).
- Best available GNSS mode (multi-band / all systems if the model supports it).
- **Auto Pause off**, so standing still is recorded and handled by pause detection, not by gaps.
- Lap button pressed at every drill change (laps become the initial segments).
- Heart rate from wrist or chest strap; HR is optional for all sprint metrics.

### 4.3 Limitations of 1 Hz GPS (binding constraints for metric design)

- One speed sample per second. A 3-second acceleration phase is 3–4 samples.
- `enhanced_speed` is Doppler-derived and already filtered by the watch. It lags the true speed by roughly 1 s at sprint onset and underestimates peak acceleration.
- Acceleration is a derivative of 1 Hz speed. Absolute values in m/s² are **comparative indicators**, valid for comparing sessions recorded the same way, not lab-grade measurements.
- Sharp changes of direction are partially smoothed out; direction-change counts are approximate.
- Positional noise is a few metres; distances of short efforts (< 15 m) carry high relative error.
- Samples can be missing (GPS dropouts, timer stops). The parser must handle gaps (section 6.2).

All metric names in the UI and API must reflect these limits (e.g. "GPS acceleration", not "acceleration").

## 5. Domain model

A session owns its samples, laps, segments and efforts; geozones and drill types are global reference data. Time inside a session is always an integer offset in seconds from the first sample (`t = 0`), which keeps 1 Hz data index-addressable.

| Entity | Owned by | Key fields | Notes |
| --- | --- | --- | --- |
| `Session` | root | id, fileSha256, fileName, uploadedAt, startTime (UTC), localTimezoneOffset, elapsedSec, timerSec, distanceM, device, sport, subSport, geozoneId?, surface, surfaceSource, notes, analysisVersion, status | One per FIT file. `fileSha256` is unique. |
| `Sample` | Session | t, timestamp, lat?, lon?, speedRaw?, speed, accel, hr?, distanceM?, altitudeM?, interpolated, inPause | `speed` and `accel` are derived (smoothed) values. |
| `Lap` | Session | index, startT, endT, trigger | Read-only copy of watch laps. |
| `Segment` | Session | id, startT, endT, drillTypeId?, label?, source | `source` = `LAP` or `MANUAL`. |
| `Effort` | Session | id, startT, peakT, endT, segmentId?, metric fields (6.4) | Recomputed on every analysis run. |
| `DrillType` | global | id, code, name, color, kind | `kind` = `DRILL`, `GAME`, `WARMUP`, `REST`. |
| `Geozone` | global | id, name, surface, shape | `shape` = circle (centre + radius m) or polygon (GeoJSON). |
| `MetricsSnapshot` | Session | scope (`SESSION`, `SEGMENT`), scopeId, analysisVersion, metrics (JSON) | Cache of computed metrics; window metrics are never stored. |

Kotlin sketch of the core types in the `domain` module:

```kotlin
enum class Surface { GRASS, SAND, UNKNOWN }
enum class SurfaceSource { GEOZONE, MANUAL, NONE }
enum class SegmentSource { LAP, MANUAL }
enum class DrillKind { DRILL, GAME, WARMUP, REST }

data class TimeRange(val fromT: Int, val toT: Int) {
    init { require(fromT in 0..toT) }
    val durationSec get() = toT - fromT
}

data class Sample(
    val t: Int,                 // seconds from session start
    val timestamp: Instant,
    val lat: Double?, val lon: Double?,
    val speedRaw: Double?,      // m/s from FIT
    val speed: Double,          // smoothed m/s
    val accel: Double,          // m/s^2, derived from smoothed speed
    val hr: Int?,
    val distanceM: Double?,
    val altitudeM: Double?,
    val interpolated: Boolean,
    val inPause: Boolean,
)

data class Segment(
    val id: UUID, val sessionId: UUID,
    val range: TimeRange,
    val drillTypeId: UUID?, val label: String?,
    val source: SegmentSource,
)

sealed interface GeozoneShape {
    data class Circle(val lat: Double, val lon: Double, val radiusM: Double) : GeozoneShape
    data class Polygon(val ring: List<Pair<Double, Double>>) : GeozoneShape
}
```

Invariants:

1. `Segment.range` lies inside `[0, lastT]` of its session and has a minimum duration of 10 s.
2. Segments of one session never overlap. Uncovered time is shown as "Unlabelled" and is still included in session-level metrics.
3. An effort is attributed to the segment that contains its `startT` (or none).
4. `Session.surface` with `surfaceSource = MANUAL` is never overwritten by geozone matching.
5. Editing segments never changes samples or efforts; it only invalidates the affected `MetricsSnapshot` rows.

## 6. Analysis pipeline and metric definitions

Analysis is a deterministic pure function `analyze(rawSession, params) -> AnalysisResult`, run on upload and on explicit recompute. All thresholds below are **initial defaults to be calibrated in milestone M0** on real files.

### 6.1 Pipeline steps

1. **Parse** the FIT file into raw records, laps, events and the session header (section 4).
2. **Build the 1 Hz grid** from the first record timestamp. Duplicate timestamps keep the last record.
3. **Handle gaps.** Gaps of at most `maxInterpolationGapSec` are filled by linear interpolation (`interpolated = true`). Longer gaps and timer-stop intervals are kept as gaps; no effort, pause or window statistic may span a gap.
4. **Remove outliers.** Speeds above `maxPlausibleSpeed` are set to missing and re-interpolated.
5. **Smooth speed** with a centred Savitzky–Golay filter (`sgWindow`, `sgOrder`). Negative results are clamped to 0.
6. **Derive acceleration** as the first derivative of the same Savitzky–Golay fit (m/s²).
7. **Detect pauses** (6.3) and set `inPause` on samples.
8. **Detect efforts** (6.4) and compute per-effort metrics.
9. **Match geozone** using the session start position, or the first valid position (6.6).
10. **Create segments from laps** if the session has none yet (upload only; recompute never touches segments).
11. **Compute and cache** session and segment metrics (6.5).

### 6.2 Parameters

All parameters live in one immutable `AnalysisParameters` data class with defaults. Changing any default increments `ANALYSIS_VERSION`.

| Parameter | Default | Unit | Used in |
| --- | --- | --- | --- |
| `maxInterpolationGapSec` | 3 | s | Gap handling |
| `maxPlausibleSpeed` | 11.0 | m/s | Outlier removal |
| `sgWindow` / `sgOrder` | 5 / 2 | samples / – | Smoothing |
| `walkSpeedThreshold` | 2.0 | m/s | Moving speed |
| `pauseSpeedThreshold` | 1.5 | m/s | Pause detection |
| `minPauseDurationSec` | 20 | s | Pause detection |
| `pauseSpikeToleranceSec` | 2 | s | Pause detection |
| `effortStartAccel` | 1.0 | m/s² | Effort detection |
| `effortMinPeakSpeed` | 4.5 | m/s | Effort detection |
| `effortMinSpeedGain` | 2.0 | m/s | Effort detection |
| `effortEndPeakRatio` | 0.7 | – | Effort detection |
| `effortEndMinSpeed` | 2.5 | m/s | Effort detection |
| `effortMinDurationSec` / `effortMaxDurationSec` | 2 / 15 | s | Effort detection |
| `decelThreshold` | −1.5 | m/s² | Deceleration count |
| `speedZones` | 1.0 / 2.0 / 4.0 / 5.5 / 7.0 | m/s (upper bounds) | Zone distribution |
| `hrMax` | 190 | bpm | HR zones (user setting) |

### 6.3 Pause detection

A pause is a maximal run of samples with `speed < pauseSpeedThreshold` lasting at least `minPauseDurationSec`. Excursions above the threshold of at most `pauseSpikeToleranceSec` inside the run do not break it. Pauses cover standing in line, walking back and breaks between drills.

- **Active time** = window duration − pause time − gap time.
- **Moving speed** = mean of `speed` over samples that are not in a pause, not interpolated and have `speed ≥ walkSpeedThreshold`. It is shown as km/h and as pace (min/km). This is the "pace without walking and standing" requested by the user.

### 6.4 Effort (sprint / cut) detection

An effort is detected as follows:

1. **Trigger** at the first sample `i` with `accel[i] ≥ effortStartAccel` after a sample below it.
2. **Start** `startT` = the local speed minimum within the 2 samples before `i` (or `i` itself).
3. **Peak** `peakT` = the sample with maximum speed reached before the end condition.
4. **End** `endT` = the first sample after the peak where `speed < max(peakSpeed × effortEndPeakRatio, effortEndMinSpeed)`, capped at `startT + effortMaxDurationSec`.
5. **Accept** if `peakSpeed ≥ effortMinPeakSpeed`, `peakSpeed − startSpeed ≥ effortMinSpeedGain`, duration ≥ `effortMinDurationSec`, and no gap inside. A new trigger is searched from `endT + 1`.

Per-effort metrics:

| Metric | Definition | Unit |
| --- | --- | --- |
| `startSpeed` | `speed[startT]` | m/s |
| `peakSpeed` | `speed[peakT]` | m/s |
| `timeToPeakSec` | `peakT − startT` | s |
| `durationSec` | `endT − startT` | s |
| `distanceM` | Σ `speed` over `[startT, endT)` × 1 s | m |
| `meanSpeed` | mean `speed` over `[startT, endT]` | m/s |
| `meanAccel` (GPS) | `(peakSpeed − startSpeed) / timeToPeakSec` | m/s² |
| `peakAccel` (GPS) | max `accel` over `[startT, peakT]` | m/s² |
| `speedAt1s`, `speedAt2s`, `speedAt3s` | `speed[startT + k]`, k = 1, 2, 3 (null past `endT`) | m/s |
| `meanSpeedFirst3s` | mean `speed` over `[startT + 1, startT + 3]` | m/s |
| `distanceFirst3s` | Σ `speed` over `[startT, startT + 3)` × 1 s | m |
| `timeTo80PctPeakSec` | first time `speed ≥ 0.8 × peakSpeed`, linearly interpolated between samples, minus `startT` | s |
| `maxDecelAfter` | min `accel` over `[peakT, endT + 3]` | m/s² |
| `hrStart`, `hrMax` | HR at `startT`; max HR over `[startT, endT + 10]` | bpm |

`meanSpeedFirst3s`, `distanceFirst3s` and `timeTo80PctPeakSec` together implement the requested "speed of the first 3 seconds of a cut" in a form that 1 Hz data supports.

### 6.5 Window metrics

The same function `windowMetrics(analysis, range)` serves a whole session, a segment, a brushed window and drill-type aggregation. An effort belongs to a window when its `startT` is inside the range.

| Group | Metrics |
| --- | --- |
| Time | `elapsedSec`, `activeSec`, `pausedSec`, `gapSec`, `workRestRatio` = active / paused |
| Distance and speed | `distanceM`, `activeDistanceM`, `movingSpeed`, `movingPace`, `maxSpeed` |
| Speed zones | time (s) and distance (m) in zones STAND, WALK, JOG, RUN, HIGH\_SPEED, SPRINT |
| Efforts | `effortCount`, `effortsPerActiveMin`, mean and best of `peakSpeed`, `meanSpeed`, `meanAccel`, `peakAccel`, `meanSpeedFirst3s`, `timeTo80PctPeakSec` |
| Decelerations | `decelCount` = number of runs of `accel ≤ decelThreshold` lasting at least 1 s |
| Fatigue | `peakSpeedDropPct` = (mean `peakSpeed` of first third of efforts − mean of last third) / first-third mean × 100; needs ≥ 6 efforts |
| Heart rate | `hrAvg`, `hrMax`, time in 5 zones as % of `hrMax` (50/60/70/80/90) |

**Drill-type aggregation:** across all segments of one drill type (optionally filtered by surface and date range), counts and durations are summed. Means are weighted by effort count (effort metrics) or by active seconds (speed metrics).

### 6.6 Geozone matching

The session reference point is `session.start_position`, else the first valid sample position. Matching uses the haversine distance for circles and point-in-polygon for polygons. If several geozones match, the smallest one by area wins. No match gives `surface = UNKNOWN` and `surfaceSource = NONE`, and the UI asks the user to pick a surface or create a geozone. Creating or editing a geozone re-runs matching for sessions whose `surfaceSource` is not `MANUAL`.

## 7. Architecture and module structure

A single Spring Boot application with a React SPA, backed by PostgreSQL, organised as a Gradle multi-module build where analysis code has no framework dependencies. Processing is synchronous: a FIT file is small (100–300 KB) and analysis takes milliseconds, so no queues or async workers are needed.

&#91;embedded content: system architecture · SPA, Spring Boot app, storage, library modules\]

Only the app touches the database and the file system; `fit-parser` and `analysis` are plain libraries used by the app and by the calibration notebooks.

### 7.1 Technology stack

| Layer | Choice |
| --- | --- |
| Language / runtime | Kotlin (current stable 2.x), JVM 21 |
| Backend framework | Spring Boot (current stable), Spring Web MVC, Spring Security (present from M2, open in the `local` profile) |
| Persistence | PostgreSQL 16+, Flyway migrations from `infra:db-migrations`, Spring `JdbcClient` with batch inserts (no JPA: samples are bulk time-series rows) |
| Raw file storage | `RawFileStore` interface; filesystem implementation in the POC, S3-compatible object storage later |
| FIT parsing | Garmin FIT SDK for Java (`com.garmin:fit`) |
| Math | Hand-written Savitzky–Golay and geometry; Apache Commons Math only if needed |
| API docs / contract | springdoc-openapi, OpenAPI 3 JSON exposed at `/v3/api-docs` |
| Exploration | Kotlin Notebook (IntelliJ) with Kotlin DataFrame and Kandy, using the `fit-parser` and `analysis` modules |
| Frontend | React + TypeScript + Vite, ECharts, MapLibre GL JS, TanStack Query, React Router, openapi-typescript for generated types |
| Build | Gradle (Kotlin DSL), version catalog; frontend built by Gradle via a Node plugin and served as static resources |
| Packaging | One OCI image from `infra/docker/Dockerfile` (multi-stage: Gradle build, then JRE runtime) |
| Run | POC: docker compose from `infra/docker`. Future server: Helm chart from `infra/helm` |

### 7.2 Gradle modules and dependency rules

| Module | Depends on | Contents | Must not |
| --- | --- | --- | --- |
| `domain` | – | Core types (section 5), `AnalysisParameters`, `TimeRange`, value classes | Depend on anything |
| `fit-parser` | `domain`, `com.garmin:fit` | `FitParser.parse(InputStream): RawSession`, unit conversion, validation | Use Spring, touch the DB |
| `analysis` | `domain` | Grid building, gap handling, smoothing, pauses, efforts, window metrics, geozone matching, drill-type aggregation | Use Spring, I/O, FIT SDK |
| `infra:db-migrations` | – | Flyway SQL files in `src/main/resources/db/migration`, packaged as a resources-only jar | Contain Kotlin code or test data |
| `app` | `domain`, `fit-parser`, `analysis`; `infra:db-migrations` at runtime | Spring Boot app: REST controllers, services, repositories, `RawFileStore`, security, static frontend | Contain analysis algorithms, SQL migrations or environment-specific config |
| `infra` (non-Gradle parts) | – | Dockerfile, docker compose, Helm chart, profile configs, operational scripts (7.5) | Be needed to compile or unit-test the code |
| `frontend` (not a JVM module) | OpenAPI spec from `app` | React SPA | Re-implement metric calculations |
| `notebooks` (not a build module) | built jars of `fit-parser`, `analysis` | `.ipynb` Kotlin notebooks for calibration | Be required by the build |

The rule that matters most: **all numbers shown in the UI come from `analysis`**, including brushed-window metrics, which the frontend requests from the API.

### 7.3 Repository layout

```text
ultimate-analytics/
├── settings.gradle.kts              # includes :infra:db-migrations
├── gradle/libs.versions.toml
├── domain/src/main/kotlin/.../domain/
├── fit-parser/src/{main,test}/kotlin/.../fit/
│   └── src/test/resources/fit/          # golden FIT files
├── analysis/src/{main,test}/kotlin/.../analysis/
│   ├── grid/        # 1 Hz grid, gaps, outliers
│   ├── smoothing/   # Savitzky–Golay
│   ├── pause/
│   ├── effort/
│   ├── metrics/     # windowMetrics, aggregation
│   └── geo/         # haversine, point-in-polygon
├── app/src/main/kotlin/.../app/
│   ├── ingestion/   # upload, dedup
│   ├── storage/     # RawFileStore + filesystem implementation
│   ├── security/    # SecurityFilterChain per profile
│   ├── session/
│   ├── segment/
│   ├── drilltype/
│   ├── geozone/
│   ├── metrics/     # snapshot cache, recompute
│   └── web/         # controllers, DTOs, error handling
├── app/src/main/resources/application.yml   # defaults only, no environment values
├── frontend/
│   ├── src/api/          # generated types + query hooks
│   ├── src/pages/        # 5 screens
│   ├── src/components/charts/  # ECharts wrappers
│   └── src/components/map/     # MapLibre wrappers
├── notebooks/
└── infra/
    ├── db-migrations/                 # Gradle subproject :infra:db-migrations
    │   ├── build.gradle.kts
    │   └── src/main/resources/db/migration/V1__init.sql
    ├── docker/
    │   ├── Dockerfile                 # multi-stage build of the app image
    │   ├── docker-compose.yml         # local: postgres + app
    │   └── .env.example
    ├── helm/ultimate-analytics/
    │   ├── Chart.yaml
    │   ├── values.yaml
    │   ├── values-server.example.yaml
    │   └── templates/                 # deployment, service, ingress, configmap,
    │                                  # secret, PVC, migration job
    ├── config/
    │   ├── application-local.yml
    │   └── application-server.yml
    └── scripts/
        ├── dev-up.sh, dev-down.sh     # wrap docker compose
        ├── backup.sh, restore.sh      # pg_dump + raw files
        └── recompute-outdated.sh      # calls the recompute endpoint
```

### 7.4 Upload processing flow

1. `POST /api/sessions/upload` receives one or more files; `.zip` archives are unpacked and each `.fit` is processed independently.
2. Compute SHA-256; if a session with the same hash exists, return `DUPLICATE` for that file.
3. Store the raw file through `RawFileStore` (POC: filesystem, `{raw-dir}/{sha256}.fit`).
4. Parse, then analyse with current `AnalysisParameters`.
5. In one DB transaction, insert the session, samples, laps, efforts, lap-based segments and metric snapshots.
6. Return a per-file result: `CREATED` (with session id and surface status), `DUPLICATE` or `FAILED` (with a reason). One failing file does not fail the batch.

### 7.5 Infrastructure module and deployment readiness

Everything that describes where and how the app runs lives under `infra/`; the application code holds no hostnames, credentials or environment-specific values. The POC runs only locally, but the code follows the rules below so that a server deployment with login later is an `infra` and `security` change, not a refactoring.

| Concern | `local` profile (POC) | `server` profile (future) |
| --- | --- | --- |
| How it runs | `infra/scripts/dev-up.sh` → docker compose (`postgres` + `app`) | Helm chart in `infra/helm`, image from the same Dockerfile |
| Config source | `application.yml` defaults + `infra/config/application-local.yml` mounted via `SPRING_CONFIG_ADDITIONAL_LOCATION` | Same defaults + `application-server.yml` from a ConfigMap |
| Secrets | `.env` file (only `.env.example` is committed) | Kubernetes Secret, injected as env vars |
| DB migrations | Flyway runs on app startup from the `infra:db-migrations` jar | Helm pre-install / pre-upgrade Job runs the same migrations; app startup migration disabled |
| Raw FIT files | `RawFileStore` filesystem impl on a compose volume | PersistentVolumeClaim or S3-compatible `RawFileStore` impl |
| Security | Spring Security filter chain permits all; bound to `localhost` | Login required (target: OIDC with a session cookie for the SPA); access limited to allowed users |
| Health | Actuator `health` | Liveness and readiness probes on a separate management port |
| Logs | Plain text to stdout | JSON to stdout |

Code rules that keep the server path open:

1. **Stateless app.** No local state outside `RawFileStore` and the database, so more than one replica is possible later.
2. **All config through Spring properties**, overridable by environment variables; no `if (profile == …)` logic outside `security` and `storage` configuration classes.
3. **One security entry point.** Spring Security is on the classpath from M2 with a `SecurityFilterChain` bean per profile in `app/security`. All API routes are under `/api` and the SPA under `/`, so adding login means adding the `server` chain, not touching controllers.
4. **Graceful shutdown** enabled; upload processing finishes or rolls back within the shutdown timeout.
5. **Migrations are additive and forward-only**, so a migration Job and a rolling app update can run in sequence safely.

POC deliverables of `infra`: `db-migrations`, Dockerfile, docker compose, `application-local.yml`, scripts. The Helm chart is a skeleton that must pass `helm lint` and `helm template` in CI; it is not deployed during the POC.

## 8. Persistence

PostgreSQL holds everything except raw FIT files, which live on disk keyed by SHA-256. All derived data (samples, efforts, snapshots) can be rebuilt from the raw files; user-entered data (segments, drill types, geozones, manual surfaces, notes) cannot and must be backed up.

### 8.1 Schema (`infra/db-migrations/.../V1__init.sql`, sketch)

```sql
create table drill_type (
  id uuid primary key,
  code text not null unique,
  name text not null,
  kind text not null check (kind in ('DRILL','GAME','WARMUP','REST')),
  color text not null
);

create table geozone (
  id uuid primary key,
  name text not null,
  surface text not null check (surface in ('GRASS','SAND')),
  shape jsonb not null,          -- {"type":"circle",lat,lon,radiusM} | GeoJSON Polygon
  created_at timestamptz not null default now()
);

create table session (
  id uuid primary key,
  file_sha256 char(64) not null unique,
  file_name text not null,
  uploaded_at timestamptz not null default now(),
  start_time timestamptz not null,
  local_tz_offset_sec int,
  elapsed_sec int not null,
  timer_sec int not null,
  distance_m double precision,
  device text, sport text, sub_sport text,
  start_lat double precision, start_lon double precision,
  geozone_id uuid references geozone(id) on delete set null,
  surface text not null default 'UNKNOWN',
  surface_source text not null default 'NONE',
  notes text,
  analysis_version int not null
);

create table sample (
  session_id uuid not null references session(id) on delete cascade,
  t int not null,
  lat double precision, lon double precision,
  speed_raw real, speed real not null, accel real not null,
  hr smallint, distance_m real, altitude_m real,
  interpolated boolean not null, in_pause boolean not null,
  primary key (session_id, t)
);

create table lap (
  session_id uuid not null references session(id) on delete cascade,
  idx int not null, start_t int not null, end_t int not null, trigger text,
  primary key (session_id, idx)
);

create table segment (
  id uuid primary key,
  session_id uuid not null references session(id) on delete cascade,
  start_t int not null, end_t int not null,
  drill_type_id uuid references drill_type(id) on delete set null,
  label text,
  source text not null check (source in ('LAP','MANUAL'))
);

create table effort (
  id uuid primary key,
  session_id uuid not null references session(id) on delete cascade,
  start_t int not null, peak_t int not null, end_t int not null,
  segment_id uuid references segment(id) on delete set null,
  metrics jsonb not null         -- per-effort metrics from 6.4
);

create table metrics_snapshot (
  session_id uuid not null references session(id) on delete cascade,
  scope text not null check (scope in ('SESSION','SEGMENT')),
  scope_id uuid not null,        -- session id or segment id
  analysis_version int not null,
  metrics jsonb not null,
  primary key (scope, scope_id)
);
```

Segment non-overlap (invariant 2) is enforced in the service layer. A Postgres exclusion constraint on `int4range` is an optional hardening step.

### 8.2 Recomputation and versioning

- `ANALYSIS_VERSION` is a constant in `analysis`. A session is **outdated** when `session.analysis_version < ANALYSIS_VERSION`.
- Recompute of a session: re-read the raw file, parse, analyse, then in one transaction replace `sample`, `lap`, `effort` and `metrics_snapshot` rows and update `analysis_version`. Segments, drill types, geozones, notes and a manual surface are preserved.
- After recompute, `effort.segment_id` is re-attributed against the existing segments.
- Editing segments deletes the affected segment snapshots and the session snapshot; they are recomputed on next read (lazy) or immediately (implementation choice, either is acceptable).

### 8.3 File storage and backup

- Raw files are written once through `RawFileStore` and never modified. The POC filesystem implementation stores `${app.storage.raw-dir}/{sha256}.fit`; an S3-compatible implementation can replace it without changes outside `app/storage`.
- Backup = `infra/scripts/backup.sh` (`pg_dump` + a copy of the raw files); `infra/scripts/restore.sh` reverses it.

## 9. REST API

JSON over HTTP under `/api`, documented by springdoc-openapi; the frontend uses only types generated from that spec. Time inside a session is always `t` in integer seconds; absolute times are ISO-8601 UTC. Errors use RFC 7807 `application/problem+json`.

### 9.1 Endpoints

| Method | Path | Purpose | Notes |
| --- | --- | --- | --- |
| POST | `/api/sessions/upload` | Upload one or more `.fit` / `.zip` files | multipart `files[]`; returns `UploadResult[]` |
| GET | `/api/sessions` | List sessions | query: `from`, `to`, `surface`, `page`, `size`; returns `Page<SessionSummary>` |
| GET | `/api/sessions/{id}` | Session detail | header, laps, segments, session metrics, `outdated` flag |
| PATCH | `/api/sessions/{id}` | Set surface and/or notes | setting `surface` sets `surfaceSource = MANUAL` |
| DELETE | `/api/sessions/{id}` | Delete session and its raw file |  |
| GET | `/api/sessions/{id}/series` | Full 1 Hz series, columnar | for charts and map |
| GET | `/api/sessions/{id}/efforts` | Efforts with per-effort metrics |  |
| GET | `/api/sessions/{id}/metrics` | Metrics for a window | query: `from`, `to` (t, inclusive); computed live, not cached |
| GET | `/api/sessions/{id}/file` | Download the raw FIT file |  |
| POST | `/api/sessions/{id}/recompute` | Recompute one session |  |
| POST | `/api/sessions/recompute-outdated` | Recompute all outdated sessions | returns count |
| POST | `/api/sessions/{id}/segments` | Create segment | body: `startT`, `endT`, `drillTypeId?`, `label?`; 409 on overlap |
| PATCH | `/api/sessions/{id}/segments/{segId}` | Edit bounds, type or label | 409 on overlap |
| DELETE | `/api/sessions/{id}/segments/{segId}` | Delete segment |  |
| POST | `/api/sessions/{id}/segments/{segId}/split` | Split at `atT` | both parts keep type and label |
| POST | `/api/sessions/{id}/segments/merge` | Merge adjacent segments | body: `segmentIds[]`; type of first wins |
| POST | `/api/sessions/{id}/segments/reset-from-laps` | Replace all segments with lap-based ones | requires `confirm=true` |
| GET | `/api/sessions/{id}/segments/{segId}/metrics` | Segment metrics | cached snapshot |
| GET, POST | `/api/drill-types` | List / create drill types |  |
| PATCH, DELETE | `/api/drill-types/{id}` | Edit / delete | delete leaves segments untyped |
| GET | `/api/drill-types/{id}/stats` | Aggregated metrics across sessions | query: `from`, `to`, `surface`; returns totals + per-session rows |
| GET, POST | `/api/geozones` | List / create geozones | create re-runs surface matching |
| PATCH, DELETE | `/api/geozones/{id}` | Edit / delete | edit re-runs matching; returns affected session count |
| GET | `/api/analysis/parameters` | Current parameters and `analysisVersion` | read-only in POC |

### 9.2 Key payloads

`GET /api/sessions/{id}/series` is columnar so ECharts can consume it directly. Arrays have equal length; `null` marks gaps.

```json
{
  "sessionId": "3d1e...",
  "startTime": "2026-09-28T16:02:11Z",
  "t":       [0, 1, 2, 3],
  "speed":   [0.4, 0.6, 2.9, 4.8],
  "speedRaw":[0.5, 0.6, 3.1, 4.7],
  "accel":   [0.1, 1.2, 2.1, 1.6],
  "hr":      [112, 113, 118, 125],
  "lat":     [34.68, 34.68, 34.68, 34.68],
  "lon":     [33.04, 33.04, 33.04, 33.04],
  "inPause": [true, false, false, false],
  "interpolated": [false, false, false, false]
}
```

`GET /api/sessions/{id}/metrics?from=600&to=1200` returns `WindowMetrics`, the same shape used for sessions, segments and drill-type totals:

```json
{
  "range": {"fromT": 600, "toT": 1200},
  "time": {"elapsedSec": 600, "activeSec": 410, "pausedSec": 190, "gapSec": 0, "workRestRatio": 2.16},
  "distance": {"distanceM": 1320, "activeDistanceM": 1190, "movingSpeed": 3.9, "movingPaceSecPerKm": 256, "maxSpeed": 7.6},
  "zones": [{"zone": "SPRINT", "timeSec": 21, "distanceM": 152}],
  "efforts": {"count": 14, "perActiveMin": 2.05,
    "peakSpeed": {"mean": 6.4, "best": 7.6},
    "meanAccel": {"mean": 2.1, "best": 2.9},
    "meanSpeedFirst3s": {"mean": 4.9, "best": 5.8},
    "timeTo80PctPeakSec": {"mean": 2.3, "best": 1.6}},
  "decelCount": 11,
  "fatigue": {"peakSpeedDropPct": 6.8},
  "heartRate": {"avg": 151, "max": 182, "zonesPct": [5, 18, 31, 32, 14]}
}
```

`UploadResult`: `{ fileName, status: CREATED | DUPLICATE | FAILED, sessionId?, surface?, needsSurface: boolean, error? }`.

Units in all payloads: m, s, m/s, m/s², bpm. Formatting to km/h and min/km is done in the frontend.

## 10. Frontend

A desktop-first React SPA with five screens; the Session screen is the core of the product and gets most of the effort. The frontend never computes metrics: it renders series from `/series` and asks `/metrics` for any window.

### 10.1 Screens

| # | Screen | Route | Content | Main actions |
| --- | --- | --- | --- | --- |
| 1 | Sessions list | `/` | Table: date, location / geozone, surface, duration, active time, effort count, max speed, moving pace; outdated badge | Filter by surface and date range, open session, open upload dialog |
| 2 | Upload (dialog) | over `/` | Drag-and-drop zone for `.fit` / `.zip`, per-file status rows (created / duplicate / failed) | For `needsSurface` results: pick surface or "create geozone from this session" |
| 3 | Session | `/sessions/:id` | Header metrics, segment strip, synchronised charts, map, metrics panel, effort drawer | Brush a window, create / edit segments, inspect efforts, set surface, recompute |
| 4 | Geozones | `/geozones` | Map with all geozones; list with name and surface | Create circle (click + radius) or polygon (draw), edit, delete |
| 5 | Drill types | `/drill-types` and `/drill-types/:id` | List of types with colour and kind; detail page with aggregated stats and a per-session trend chart | CRUD types; filter stats by surface and date range |

### 10.2 Session screen behaviour

Layout, top to bottom: header (date, location, surface chip, key metrics), segment strip, three stacked charts on the left (about two thirds of the width), map and metrics panel on the right.

Charts (ECharts):

- Three line charts share the x axis (`t`, formatted mm:ss): **Speed** (smoothed, with raw speed as a faint second series), **GPS acceleration**, **Heart rate**.
- All three are joined with `echarts.connect(group)`, so tooltip, axis pointer and `dataZoom` are synchronised. An inside zoom (wheel / drag) plus a slider under the bottom chart.
- Pauses are shaded grey with `markArea`; gaps render as line breaks (`connectNulls: false`).
- Segments are coloured `markArea` bands by drill type on the speed chart, mirrored in the segment strip above.
- Efforts are `markPoint` markers at `peakT` on the speed chart; hover shows peak speed and `meanSpeedFirst3s`.
- `sampling: 'lttb'` on line series; full-resolution data is sent by the API.

Window selection:

1. The user drags a `brush` (lineX) on any chart.
2. On `brushEnd`, the selected `[from, to]` is stored in the URL (`?from=600&to=1200`) and in component state.
3. The metrics panel requests `/metrics?from&to` (debounced 200 ms, cached by TanStack Query) and shows a "Window" column next to the "Session" column.
4. The map highlights the window part of the track.
5. A "Save as segment" button opens a small form (drill type, label) and calls `POST /segments`; a 409 overlap error is shown inline.
6. Clicking a segment in the strip selects its range as the window; Escape clears the selection.

Map (MapLibre GL JS):

- Layers: full track (GeoJSON LineString, neutral colour), window highlight (thicker, accent colour), effort start points, a cursor point following the chart axis pointer, the matched geozone outline.
- Base map is configurable: a vector style (default) and a satellite raster style, switchable. Tile provider URLs and keys come from frontend config; provider terms must be checked by the implementer.
- Fit bounds to the track on load.

Effort drawer: clicking an effort marker opens a side drawer with a small speed + acceleration chart from `startT − 3` to `endT + 3`, the effort's path on a mini map, and the full per-effort metrics table (6.4). Next / previous effort buttons.

Segment editing: on the segment strip, drag edges to resize, context menu for split at cursor, merge with next, change type, delete, and "Reset from laps" (with confirmation).

### 10.3 Frontend conventions

- API types are generated with openapi-typescript from `/v3/api-docs`; no hand-written DTO types.
- One query hook per endpoint in `src/api/`; mutations invalidate the affected session, metrics and segment queries.
- Units: speed in km/h and pace in min/km in the UI, acceleration in m/s², distances in m. GPS-derived acceleration metrics carry a "GPS" label and a tooltip explaining the 1 Hz limitation.
- Drill-type colours come from the API; surface chips: grass green, sand amber, unknown grey.
- Desktop only (min width 1280 px) for the POC.

## 11. Non-functional requirements, configuration and deployment

The POC runs locally with `docker compose up`, for one user, with no authentication. The targets below are sized for that.

| Area | Requirement |
| --- | --- |
| Performance | Upload + analysis of a 2-hour session (≈ 7 200 samples) < 2 s; `/series` < 300 ms; `/metrics` for any window < 150 ms |
| Data volume | Designed for ≥ 1 000 sessions (≈ 7 M sample rows) without schema changes |
| Determinism | Same raw file + same `AnalysisParameters` → byte-identical analysis results |
| Robustness | A malformed or non-activity FIT file returns `FAILED` with a reason and stores nothing |
| Security | `local` profile: bound to `localhost`, open filter chain. Server path: login via the `server` filter chain (7.5); no auth logic outside `app/security` |
| Portability | Same image runs under docker compose and the Helm chart; all environment differences come from `infra` configs and env vars |
| Observability | Structured logs per upload (file name, hash, duration, result); Spring Boot Actuator `health`, with liveness / readiness groups |
| Time zones | Store UTC; display in the session's local offset from FIT (`local_tz_offset_sec`) |

Configuration (`application.yml`, overridable by environment variables):

| Key | Default | Meaning |
| --- | --- | --- |
| `spring.profiles.active` | `local` | `local` for the POC, `server` later |
| `app.storage.type` | `filesystem` | `RawFileStore` implementation |
| `app.storage.raw-dir` | `./storage/raw` | Raw FIT directory (filesystem store) |
| `app.upload.max-file-size` | `20MB` | Per-file limit (zip included) |
| `app.analysis.hr-max` | `190` | Max HR for zones |
| `spring.flyway.enabled` | `true` | Run migrations on startup (`false` when the Helm Job runs them) |
| `spring.datasource.*` | from env | PostgreSQL connection, never committed with real credentials |

Deployment: everything lives in `infra` (section 7.5). Locally, `infra/docker/docker-compose.yml` runs `postgres` (named volume) and `app` (image from `infra/docker/Dockerfile`, raw storage as a volume, `infra/config/application-local.yml` mounted). In development, Vite runs on its own port with a proxy to `/api`.

## 12. Testing strategy

The `analysis` module carries most of the risk and gets the most tests; golden FIT files with known ground truth are the backbone of regression testing.

| Level | Scope | Tools | What is checked |
| --- | --- | --- | --- |
| Unit | `analysis` | JUnit 5, Kotest assertions | Savitzky–Golay coefficients vs reference values, gap interpolation, pause hysteresis, effort start / peak / end on hand-built series, every metric formula in 6.4–6.5, haversine and point-in-polygon |
| Property-based | `analysis` | Kotest property testing | Invariants on random series: efforts never overlap or span gaps; active + paused + gap = elapsed; window metrics of adjacent windows sum correctly for additive metrics; adding noise below a threshold never creates efforts on a flat series |
| Golden files | `fit-parser` + `analysis` | JUnit + approval snapshots | Real FIT files in `src/test/resources/fit/` with an expected JSON result; any change in output fails the test until the snapshot is consciously updated together with `ANALYSIS_VERSION` |
| Integration | `app` | Spring Boot Test, Testcontainers (PostgreSQL) | Upload flow, dedup, recompute keeps segments and manual surface, segment overlap → 409, geozone edit re-matches sessions |
| API contract | `app` + `frontend` | OpenAPI generation in CI | Generated TS types compile; breaking DTO changes fail the frontend build |
| E2E (smoke) | whole app | Playwright | Upload a golden file → session opens → brush a window → metrics panel shows values → save as segment |

Golden file set to record during M0 (each with a written protocol of what was done):

1. **Calibration sprints:** 10 sprints from standstill over a measured 20 m and 40 m, a lap pressed before each, 60 s rest.
2. **Pause check:** 3 min standing, 3 min walking, 3 min jogging, each as its own lap.
3. **Typical grass training** with laps per drill.
4. **Typical sand training** with laps per drill.
5. **Edge cases:** a file with GPS dropout (e.g. under a roof), a file with timer stop / start, a non-Ultimate activity.

Ground truth for file 1 (sprint count = 10, known distances) makes effort-detection accuracy measurable: target ≥ 95 % recall and no false positives on file 2.

## 13. Implementation milestones and acceptance criteria

The POC is built in five sequential milestones; each ends with a gate that is checked by tests, not by judgement. Calibration (M0) comes first because the metric thresholds and the golden test files are inputs to everything after it.

&#91;embedded content: POC roadmap · 5 milestones, 5 gates\]

M1 can start with provisional defaults while M0 data is collected, but M1 is not closed until the golden files from M0 pass.

### Acceptance criteria

**M0 — Data audit and calibration**

- [ ] Golden files 1–5 (section 12) are recorded, each with a written protocol, and committed to `fit-parser/src/test/resources/fit/`.
- [ ] A Kotlin Notebook shows the detected efforts on file 1 lining up with the 10 real sprints.
- [ ] `AnalysisParameters` defaults are updated from calibration and the Ultimate Disc `sport` / `sub_sport` values are documented.

**M1 — Core modules**

- [ ] `fit-parser` parses all golden files; invalid files produce typed errors.
- [ ] Every metric in sections 6.4 and 6.5 is implemented with unit tests; property-based tests pass.
- [ ] Effort recall ≥ 95 % on file 1 and no efforts inside the pause laps of file 2.

**M2 — Backend**

- [ ] All endpoints in 9.1 are implemented and present in the OpenAPI spec.
- [ ] Uploading the five golden files in one request returns correct per-file statuses; re-upload returns `DUPLICATE`.
- [ ] Recompute preserves segments, drill types, notes and a manual surface.
- [ ] Performance targets in section 11 are met on the longest golden file.
- [ ] `infra/scripts/dev-up.sh` starts Postgres and the app from a clean checkout; migrations come only from `infra:db-migrations`.
- [ ] `app/src/main/resources` holds no environment-specific values or secrets; Spring Security runs with the open `local` chain.
- [ ] The Helm chart skeleton passes `helm lint` and `helm template` in CI.

**M3 — Sessions list and Session screen**

- [ ] Sessions list with filters and the upload dialog work end-to-end.
- [ ] Session screen shows synchronised charts, pause shading, effort markers and a map with a cursor point.
- [ ] Brushing a window updates the metrics panel and the map highlight; saving it creates a segment.
- [ ] Segment strip supports resize, split, merge, delete and reset from laps.

**M4 — Geozones and drill types**

- [ ] Geozones can be created, edited and deleted on a map; creating one re-classifies matching sessions.
- [ ] Drill-type stats page works with surface and date filters and shows a per-session trend chart.
- [ ] The Playwright smoke test passes.

## 14. Open questions and future extensions

Open questions, to be answered during M0 or before the related milestone:

- [ ] Which `sport` / `sub_sport` values does the Ultimate Disc profile write, and does the watch record laps and `enhanced_speed` as expected?
- [ ] Do the default effort thresholds work on sand, where peak speeds are lower, or does the system need per-surface parameter sets?
- [ ] Is a 5-sample Savitzky–Golay window the right trade-off between noise and sprint-onset lag, or is 3 better?
- [ ] Should a brushed window be savable as a lightweight bookmark without becoming a segment?
- [ ] Is a fixed `hrMax` enough, or are custom HR zone bounds needed?
- [ ] On a server, will the app stay single-user? If more users are possible, add `owner_id` to `session`, `geozone` and `drill_type` in `V1` rather than in a later migration.

Future extensions (explicitly not in the POC; the design should not block them):

- **Trends dashboard:** weekly effort count, sprint volume, best `meanSpeedFirst3s` over time.
- **Grass vs sand comparison** screen for the same drill type.
- **Automatic segment suggestions** from pause boundaries, confirmed by the user.
- **Automatic drill-type classification** trained on the user's own labelled segments.
- **Change-of-direction count** from heading changes (approximate at 1 Hz).
- **Server deployment** with the Helm chart and the `server` profile: OIDC login, migration Job, persistent or object storage for raw files, TLS at the ingress, and a responsive layout for viewing on a phone after training.
