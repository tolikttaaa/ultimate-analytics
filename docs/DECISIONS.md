# Decisions

Choices made during implementation where `docs/SPEC.md` was ambiguous, contradictory or silent.
Newest entries at the bottom.

## 2026-10-02 — Spec file name

- **Context:** `.claude/CLAUDE.md` refers to `docs/SPEC.md`; the spec was saved as `docs/POC_specification.md`.
- **Decision:** renamed the file to `docs/SPEC.md`; content unchanged. Approved by the user.
- **Alternatives:** keep the old name and update CLAUDE.md.

## 2026-10-02 — Base package

- **Context:** CLAUDE.md asks for a base package (suggesting `com.<name>.ultimate`).
- **Decision:** `com.ttaaa.ultimate`, with sub-packages `domain`, `fit`, `analysis`, `app` (spec 7.3). Chosen by the user.
- **Alternatives:** `org.ttaaa.ultimate` (namespace of the IntelliJ project template).

## 2026-10-02 — No `owner_id` in V1

- **Context:** spec 14 asks whether to add `owner_id` to `session`, `geozone`, `drill_type` in V1.
- **Decision:** not added. The POC is single-user; a nullable column can be added later in an additive migration.
  Approved by the user.
- **Alternatives:** add `owner_id` now.

## 2026-10-02 — No `Session.status`

- **Context:** spec 5 lists `status` on `Session`, but the schema in 8.1 has no column and no states are defined.
  Uploads are synchronous, a failed file stores nothing (spec 11), and "outdated" is derived from
  `analysis_version` (spec 8.2).
- **Decision:** `status` is left out of the domain model and the schema. Approved by the user.
- **Alternatives:** a status enum such as `READY` / `OUTDATED`.

## 2026-10-02 — IntelliJ project files are not committed

- **Context:** the IntelliJ template staged `.idea/` files, including a machine-specific JDK name.
- **Decision:** `.idea/` is ignored entirely; the Gradle build is the single source of project configuration.
  Approved by the user.
- **Alternatives:** commit shared files such as code style settings.

## 2026-10-02 — Spring Boot 4 and JUnit 6

- **Context:** spec 7.1 asks for the current stable Spring Boot; CLAUDE.md and spec 12 say "JUnit 5".
  Spring Boot 4 brings Jackson 3 and manages JUnit 6, which keeps the JUnit Jupiter API.
- **Decision:** Spring Boot 4.x; tests use the JUnit Jupiter API on JUnit 6. "JUnit 5" is read as "JUnit Jupiter".
  Approved by the user.
- **Alternatives:** Spring Boot 3.5 with JUnit 5.

## 2026-10-02 — Testcontainers from step 2

- **Context:** once the app has a datasource (step 2), its startup test needs a database; spec 12 plans
  Testcontainers for the `app` integration tests.
- **Decision:** Testcontainers PostgreSQL is introduced in step 2. `./gradlew build` needs a running Docker from then on.
  Approved by the user.
- **Alternatives:** an in-memory database (diverges from Postgres), or no startup test until step 11.

## 2026-10-02 — Build setup taken over from the IntelliJ template

- **Context:** the repository started from an IntelliJ Kotlin multi-module template (`app`, `utils`, JVM toolchain 26,
  kotlinx libraries, Gradle 9.6.0).
- **Decision:** kept the `buildSrc` convention plugin, the version catalog, foojay and the build/configuration caches;
  removed `utils`, the `application` plugin and the kotlinx bundle (the domain uses `java.time` and `java.util.UUID`
  as in spec 5); set the toolchain to 21 (spec 7.1); updated the wrapper to the current Gradle 9.8.0. Plugins are put
  on the build classpath by `buildSrc` and applied by id without versions. Approved by the user.
- **Alternatives:** start the Gradle setup from scratch.

## 2026-10-02 — Local stack details

- **Context:** spec 7.5 and 11 describe the local compose stack but leave image versions, ports and script behaviour open.
- **Decision:**
  - Postgres image `postgres:17-alpine`, the same tag in compose and in the Testcontainers tests.
  - Postgres and the app are published on `127.0.0.1` only; host ports come from `POSTGRES_PORT` / `APP_PORT` in `.env`.
  - `dev-up.sh` creates `.env` from `.env.example` when it is missing, so a clean checkout starts (M2 criterion), and
    has a `--db-only` mode for running the app from the IDE. `dev-down.sh --volumes` deletes the data.
  - Credentials exist only as `POSTGRES_*` variables in `.env`; `application-local.yml` maps them to
    `spring.datasource.*`. `./gradlew :app:bootRun` reads the same `.env` and config directory as compose.
  - The image runs as a non-root user with the layered Spring Boot jar; the Docker ignore file sits next to the
    Dockerfile (`Dockerfile.dockerignore`) so everything about running the app stays under `infra/`.
  - V1 adds to the spec 8.1 sketch only check constraints (`session.surface`, `session.surface_source`, ordering of
    segment and effort times) and indexes on `session.start_time` and the foreign keys.
- **Alternatives:** Postgres 18; ports on all interfaces; failing when `.env` is missing.

## 2026-10-02 — Domain model details (step 3)

- **Context:** spec 5 gives a sketch of the domain types; some details are open or would conflict with other sections.
- **Decision:**
  - Derived data carries no identity: `Sample`, `Lap` and `Effort` have no ids. The analysis must produce
    byte-identical results for the same input (spec 11), so it cannot generate random effort ids; the app adds the id
    and the segment an effort belongs to when it stores it (spec 8.2). User data (`Session`, `Segment`, `DrillType`,
    `Geozone`) has ids.
  - Positions are a `GeoPoint(lat, lon)` value (`Sample.position`, `Session.startPosition`, circle centre, polygon
    vertices) instead of separate fields or `Pair<Double, Double>`, whose order is easy to confuse with GeoJSON's
    `[lon, lat]`. Polygon rings list each vertex once (not closed).
  - A gap is a missing `t`: samples exist only for recorded or interpolated seconds, matching the `sample` table.
  - `Session.startPosition` holds the resolved geozone reference point (FIT start position, else the first valid sample
    position, spec 6.6), so geozone edits can re-match sessions without loading samples.
  - `WindowMetrics` is a domain type because `MetricsSnapshot` stores it; its shape follows the JSON in spec 9.2
    (`movingPaceSecPerKm` rather than 6.5's `movingPace`). Values that need missing data are null.
  - `TimeRange.overlaps` means "share at least one second": ranges that only touch do not overlap, so split and
    adjacent segments are valid.
  - Constructors check local invariants only: segment minimum duration (10 s), effort time order, lowercase hex
    SHA-256, drill type code `[A-Z0-9_]+` and colour `#RRGGBB`, geozone surface GRASS/SAND, parameter consistency
    (odd SG window, order below window, five increasing speed-zone bounds, ...). Rules that need the whole session
    (segment inside the session, no overlap) stay in the service layer.
  - `ANALYSIS_VERSION` lives in `analysis` (spec 8.2). `AnalysisVersionTest` keeps the defaults of every version and
    fails when a default changes without a version bump.
- **Alternatives:** follow the sketch literally (ids on efforts, lat/lon pairs); define `WindowMetrics` in step 9.

## 2026-10-02 — FIT parsing (step 4)

- **Context:** spec 4.1 lists the FIT messages to read; the first real files (13 FR965 trainings provided by the user,
  see `fit-parser/src/test/resources/fit/README.md`) show details the spec does not cover.
- **Decision:**
  - The parser output (`RawSession`, `RawRecord`, `RawLap`, `TimerEvent`) lives in `domain`, so `analysis` can consume
    it without depending on `fit-parser` (spec 7.2).
  - The Ultimate Disc profile writes `sport = disc_golf`, `sub_sport = ultimate` (answers spec 14, first question).
    Other sports are parsed with a warning logged through `System.Logger`, which needs no logging dependency;
    Spring Boot routes it to its logging.
  - Lap end = `start_time + total_elapsed_time`: the FR965 writes the activity start into the lap `timestamp` field.
  - The local time offset comes from the `activity` message (`local_timestamp − timestamp`), not listed in spec 4.1.
  - Scaled values (speed, distance, altitude, totals) are read with `getFieldDoubleValue`, because the SDK's typed
    getters return `Float` and turn `1.11` into `1.1100000143051147`.
  - Typed errors (`FitParseException`): not a FIT file, corrupted (CRC), not an activity, no session, no records.
    Records without a timestamp and positions outside the valid range are dropped.
  - Golden tests compare a readable summary plus a SHA-256 of all records with approved snapshots in
    `src/test/resources/fit/expected/`; `-PupdateGolden` rewrites them. All 13 files are committed as golden files.
- **Alternatives:** raw types in `fit-parser` with a copy in `analysis`; SLF4J as a dependency of `fit-parser`;
  full JSON snapshots of every record.

## 2026-10-02 — Smart recording support (option C)

- **Context:** the user's existing files were recorded with Garmin Smart recording (records 1-6 s apart), while spec 4.2
  requires Every Second and spec 6.1 interpolates only gaps of up to 3 s. Followed literally, most of each existing
  session would be a gap.
- **Decision (chosen by the user):** detect the recording mode per session. A session is `EVERY_SECOND` when at least
  `everySecondRecordingMinShare` (0.9) of its record intervals are 1 s (intervals across a timer stop do not count),
  otherwise `SMART`. Every-second sessions follow the spec unchanged (`maxInterpolationGapSec` = 3); Smart sessions
  interpolate up to `smartRecordingMaxGapSec` (7) missing seconds. Timer stops are never interpolated in either mode.
  The mode is stored with the session (a `V2` migration with the persistence in step 11) so the UI can mark sprint and
  acceleration metrics of Smart sessions as low-confidence. New trainings should be recorded Every Second.
- **Alternatives:** follow the spec literally (A); raise the interpolation limit for every file (B).

## 2026-10-02 — Grid details (step 5)

- **Context:** spec 6.1 steps 2-4 leave some details open.
- **Decision:**
  - A gap's length is the number of missing seconds between two samples: with the limit 3, samples up to 4 s apart are
    joined.
  - Outliers (speed above `maxPlausibleSpeed`) are removed before interpolating rather than after, so no interpolated
    value is derived from an outlier; a run of missing and outlier seconds longer than the limit stays a gap.
  - A second needs a speed to become a sample. Seconds without a plausible speed that cannot be interpolated (for
    example the first records before the GPS fix) are gaps, while `t = 0` stays at the first record.
  - Interpolated seconds keep whatever was recorded for them (heart rate, position, the raw outlier speed in
    `speedRaw`) and interpolate the rest; recorded seconds are never changed.
  - The timer counts as stopped from a STOP event to the next START; nothing is interpolated across that interval,
    however short.
  - `ANALYSIS_VERSION` stays 1 until the first analysis results are stored (step 11): until then, new parameters extend
    the version-1 defaults in place.
  - The analysis golden tests use `fit-parser` and its test fixtures in test scope only; the analysis main code has no
    FIT SDK dependency.
- **Alternatives:** count gaps as the time between samples; spec order (interpolate, then remove outliers).

## 2026-10-02 — Smoothing details (step 6)

- **Context:** spec 6.1 steps 5-6 ask for a centred Savitzky–Golay filter but do not say what happens where a full
  centred window does not fit: at the ends of a run between gaps, and in runs shorter than the window.
- **Decision:**
  - Every run between gaps is filtered on its own.
  - Near the ends of a run, the polynomial fitted to the first or last full window is evaluated off-centre (the
    "interp" mode of SciPy's `savgol_filter`), so the window size and order stay the same up to the edges.
  - A run shorter than `sgWindow` is fitted as a whole with the order lowered to `run length − 1` if needed; a single
    isolated sample keeps its speed and gets acceleration 0.
  - Coefficients are computed by least squares for any window, order and position, and tested against the classic
    published tables (spec 12).
  - The smoothing step returns domain `Sample`s; `inPause` is set by the pause detection (step 7).
- **Alternatives:** shrink the window near the edges; mirror padding; leave edge samples unsmoothed.

## 2026-10-02 — Time accounting convention (step 7)

- **Context:** spec 9.1 calls the window bounds `from`, `to` inclusive, while the example in 9.2 gives `[600, 1200]` an
  `elapsedSec` of 600, spec 12 requires `active + paused + gap = elapsed`, and spec 6.4 sums over `[startT, endT)`.
  Counting both bounds as seconds would give 601 seconds for 600 elapsed.
- **Decision:** a sample at `t` stands for the second `[t, t+1)`. A range `[fromT, toT]` covers the seconds
  `fromT..toT−1` in every sum and count (time, distance, zones, heart rate, efforts by `startT`), so its duration is
  `toT − fromT` and adjacent ranges, such as the two parts of a split segment, never share a second or an effort.
  Both bounds stay valid `t` values in the API. A session `[0, lastT]` has `lastT` seconds, matching the FIT elapsed
  time.
- **Alternatives:** count both bounds, which breaks the spec 12 property and makes efforts at a segment boundary
  belong to two segments.

## 2026-10-02 — Pause details (step 7)

- **Context:** spec 6.3 defines pauses and moving speed; some details are open.
- **Decision:**
  - Pauses are detected on the smoothed speed and never span a gap; each run between gaps is checked on its own.
  - A pause covers its slow samples and the short excursions between them (`pauseSpikeToleranceSec`); excursions at
    its edges are not part of it. Its length, compared with `minPauseDurationSec`, is the number of seconds it covers.
  - Moving speed follows spec 6.3 literally, also for Smart-recorded sessions, where it then averages the recorded
    seconds only (about a third of the samples).
- **Alternatives:** detect pauses on the raw speed; let a pause bridge short gaps.

## 2026-10-02 — Effort detection details (step 8)

- **Context:** spec 6.4 defines effort detection, but the peak and the end are defined in terms of each other, the
  start look-back is a fixed number, and it does not say where to search after a rejected candidate.
- **Decision:**
  - The trigger is a rising edge: `accel ≥ effortStartAccel` right after a sample below it, in the same run.
  - The start is the lowest speed among the trigger and the `effortStartLookbackSec` (new parameter, default 2) samples
    before it; on ties the latest sample, just before the speed rises. It never lies before the end of the previous
    effort, so efforts never overlap (they may touch).
  - Peak and end use a running peak: walking forward from the start, the peak is the highest speed so far (the first
    one on ties), and the effort ends at the first sample below `max(peak × effortEndPeakRatio, effortEndMinSpeed)` of
    that peak, at the latest `effortMaxDurationSec` after the start (the peak may then be the last sample).
  - An effort that reaches a gap or the end of the data before it ends is rejected (spec: no gap inside).
  - After an accepted effort the next trigger is searched from its end + 1 (spec); after a rejected candidate from the
    sample after its trigger, so a real sprint right behind a false start is not skipped.
  - Per-effort metrics follow the intervals of spec 6.4 literally (for example `meanSpeed` over `[startT, endT]`,
    `distanceM` over `[startT, endT)`). Values after the end of an effort (`meanSpeedFirst3s`, `maxDecelAfter`, `hrMax`)
    use the samples of the same run only; `meanSpeedFirst3s` is null when the run ends before `startT + 3`.
  - The 80 %, 3 s, +3 s and +10 s of the metric definitions are named constants, not parameters: they define what a
    metric means, while parameters are detection thresholds.
- **Alternatives:** search after the end of rejected candidates too; let the look-back reach into the previous effort.

## 2026-10-02 — Window metrics and aggregation details (step 9)

- **Context:** spec 6.5 lists the window metrics but leaves several definitions open.
- **Decision:**
  - **Distance (chosen by the user):** `distanceM`, `activeDistanceM` and zone distances sum the smoothed speed per
    second, but seconds below the new parameter `minDistanceSpeed` (1.0 m/s) cover no distance. While standing, the
    watch's Doppler speed averages 0.45-0.55 m/s; summing it gave 30-60 % more distance than the watch. With the floor
    the golden sessions come out 2-15 % below the watch's distance (to calibrate in M0). Effort distances keep the
    definition of spec 6.4.
  - **Speed zones** count every second of the window, paused ones included; upper bounds are exclusive, so zone times
    add up to `elapsedSec − gapSec`.
  - **Decelerations** are runs of `accel ≤ decelThreshold` (at 1 Hz every run lasts at least 1 s), counted in the window
    where they start and ended by a gap, so the counts of adjacent windows add up.
  - **Fatigue:** the first and last third are ⌊n/3⌋ efforts each, ordered by start; at least 6 efforts.
  - **Heart-rate zones:** lower bounds are the new parameter `hrZoneLowerBoundsPct` (50/60/70/80/90 % of `hrMax`, also
    answering spec 14 on custom zones). `zonesPct` is the share of the seconds with heart rate; time below the first
    zone is in none, so the five values can add up to less than 100.
  - **Efforts per active minute** and the effort statistics use the efforts that start in the window; bests are the
    maximum, except `timeTo80PctPeakSec`, where lower is better.
  - **Aggregation** (drill types, per-session totals): counts, durations, distances, zones and decelerations are summed;
    effort means and fatigue are weighted by effort count, moving speed by active seconds and heart rate by recorded
    seconds (`elapsedSec − gapSec`); maxima and bests are the best of the windows. `WindowMetrics.range` is null for
    such totals. For adjacent windows of one session the totals equal the metrics of the whole window (property test).
- **Alternatives:** the watch's cumulative distance; the plain sum of speed; counting time below 50 % HR in zone 1;
  clipping deceleration runs to the window.

## 2026-10-02 — Pipeline entry point and geo details (step 10)

- **Context:** spec 6.1 describes `analyze(rawSession, params) -> AnalysisResult` including geozone matching and
  lap-based segments; spec 6.6 leaves the geometry details open.
- **Decision:**
  - `analyze` runs grid, smoothing, pauses and efforts, puts the laps on the time axis (rounded to the nearest second,
    clamped to `[0, lastT]`) and computes the session metrics. Geozone matching (`matchGeozone`, `applyGeozoneMatch`)
    and creating segments from laps stay separate calls for the app: geozones and segments are user data that change
    without re-analysing a session.
  - The reference point for matching is the FIT start position, else the first sample with a position.
  - Haversine uses the mean Earth radius 6 371 008.8 m. Polygons use the even-odd rule with straight edges in
    latitude/longitude and must not cross the antimeridian; areas use a local equirectangular projection scaled at the
    vertices' mean latitude. Several matches: the smallest area wins, ties by geozone id so the result does not depend
    on the order of the geozones.
  - Without a match a non-manual session is reset to `UNKNOWN` / `NONE` (also clearing an earlier geozone match);
    a `MANUAL` surface is never changed.
  - The property tests of spec 12 were added with the steps they belong to (7: time adds up; 8: efforts never overlap
    or span gaps, noise creates no efforts; 9: adjacent windows add up). Step 10 adds geo properties.
  - The Kotlin Notebook loads the built jars with `@file:DependsOn` paths relative to `notebooks/`; it has not been run
    in this environment (no Kotlin Notebook kernel available).
- **Alternatives:** let `analyze` take the geozones and return the match; a geodesic polygon test.

## 2026-10-02 — Persistence and upload details (step 11)

- **Context:** spec 7.4 stores the raw file before parsing, while spec 11 says a failed file stores nothing; several
  persistence details are open.
- **Decision:**
  - Upload order: parse and analyse in memory, then store the raw file, then insert everything in one transaction. A
    file that fails stores nothing; if the transaction fails, a raw file this upload wrote is deleted again. A
    concurrent upload of the same file is reported as `DUPLICATE` (unique `file_sha256`).
  - `session.recording_mode` is added by `V2` (forward-only migrations, spec 7.5). `Session.elapsedSec` is `lastT` (the
    time axis of all metrics); `timerSec` and `distanceM` are the FIT session totals.
  - Lap-based segments ("Lap n") are created for laps of at least 10 s that do not overlap an earlier lap; efforts are
    linked to the segment containing their start (half-open, so a shared boundary belongs to the later segment).
  - Snapshots of the session and of every segment are computed from samples rounded to the precision of the `sample`
    table (4-byte floats), so a cached metric always equals the same metric computed later from the database.
  - JSON columns are written with the application's Jackson mapper: effort metrics and window metrics as the domain
    data classes, geozone shapes as `{"type":"circle",...}` or GeoJSON Polygons with closed `[lon, lat]` rings.
  - Zip archives: every `.fit` entry (case-insensitive) is imported on its own and reported as `archive.zip/entry.fit`;
    macOS `__MACOSX/._*` entries are skipped; `app.upload.max-file-size` applies to each entry as well (protection
    against zip bombs). Files outside archives are always tried as FIT files.
  - "SQL lives only in infra:db-migrations" (CLAUDE.md) is read as schema SQL: the JdbcClient repositories required by
    spec 7.1 contain their queries.
  - Sample inserts use JDBC batches with the driver's `reWriteBatchedInserts`; the longest golden file (157 min) uploads
    in about 0.1 s, well under the 2 s target of spec 11.
  - The `.gitignore` entry for the local raw-file directory is anchored to the repository root (`/storage/`); it used
    to hide the `app/.../storage` source package.
- **Alternatives:** store the raw file first and clean up on failure; compute snapshots from unrounded samples.

## 2026-10-02 — Publishing on GitHub

- **Context:** the user asked to publish the project at https://github.com/tolikttaaa.
- **Decision (chosen by the user):** public repository `tolikttaaa/ultimate-analytics`, including the golden FIT files
  with GPS tracks and heart rate. Every step commit is pushed to `origin main`; `.claude/settings.json` allows exactly
  `git push origin main` / `git push -u origin main` and denies force pushes.
- **Alternatives:** private repository; removing the golden files from the history.

## 2026-10-02 — API details (step 12)

- **Context:** spec 9 lists the endpoints and payloads; some behaviour is open.
- **Decision:**
  - PATCH bodies distinguish an absent field (unchanged) from `null` (cleared) for clearable fields (session notes,
    segment drill type and label), using `Optional` fields; empty or blank text also clears.
  - The sessions list filters the start time with `from` (inclusive) and `to` (exclusive) ISO-8601 instants; pages are
    0-based, at most 100 items. List rows take active time, effort count, max speed and moving pace from the cached
    session metrics.
  - Snapshots missing from the cache are computed on read and stored with the session's analysis version, so metrics of
    an outdated session stay marked as outdated.
  - `/series` covers every second `0..lastT` with `null` in gaps; values are sent as 32-bit floats, their stored
    precision. `/metrics` needs both `from` and `to` within `[0, elapsedSec]`.
  - Segment edits lock the session row, so concurrent edits cannot create overlaps. Editing bounds, splitting and
    merging make a segment MANUAL; changing only type or label keeps its source. Merging needs consecutive segments
    (none between them) and covers the gaps between them. After every change efforts are attributed again and the
    snapshots of segments whose bounds changed are dropped (computed again when read); the session snapshot does not
    depend on segments and stays. An unknown drill type in a segment request is a 400.
  - Drill type codes are trimmed and stored in upper case; a duplicate code is a 409. Drill type stats aggregate the
    cached segment metrics per session and in total (weights of step 9); `from`/`to`/`surface` filter the sessions as
    in the sessions list; per-session rows are in time order for the trend chart.
  - Geozone shapes use the same JSON in the API as in the database (spec 8.1); polygons have exactly one ring (no
    holes), closed or not. Creating, editing and deleting a geozone all re-run the matching for every non-MANUAL
    session and return the number of sessions whose geozone or surface changed; a deleted geozone first leaves its
    sessions through the foreign key, then they are matched again.
  - Recompute (spec 8.2) replaces samples, laps, efforts and snapshots in one transaction and keeps segments (same
    ids), notes and a MANUAL surface; other surfaces are matched again because the reference point may change with the
    algorithm. `recompute-outdated` skips (and logs) sessions that fail, e.g. without a raw file, and returns how many
    were recomputed. A missing raw file is a 404.
- **Alternatives:** JSON Merge Patch documents; recomputing segment snapshots immediately.

## 2026-10-02 — Server deployment skeleton (step 13)

- **Context:** spec 7.5 describes the future `server` profile and the Helm chart in outline only.
- **Decision:**
  - `server` profile (`infra/config/application-server.yml`): no Flyway on startup, probes on management port 8081,
    ECS JSON logs, raw files under `/data/raw`. Database URL and credentials come from environment variables.
  - Until a login exists, the `server` SecurityFilterChain allows only the health probes and denies everything else,
    so a server deployment can never be open by accident; both profiles have an empty user store, so Spring Boot never
    creates a default user with a logged password. Security beans exist only in web applications.
  - The migration Job runs the app image with `--spring.main.web-application-type=none --spring.flyway.enabled=true`:
    the same migrations from the same image; the app migrates and exits (verified in a read-only, non-root container).
  - The chart renders `application-server.yml` into a ConfigMap from `--set-file`, so `infra/config` stays the single
    source. Credentials come from an existing Secret (`database.existingSecret`); without one the chart creates a
    Secret as a pre-install hook, because the migration Job needs it before regular resources exist.
  - One replica with the `Recreate` strategy, because the raw-file volume is ReadWriteOnce; the PVC is kept on
    uninstall (`helm.sh/resource-policy: keep`), as raw files are the source of all data. The container runs as the
    fixed non-root user 999 with a read-only root filesystem.
  - CI is GitHub Actions (chosen by the user): `./gradlew build` with Testcontainers on the runner's Docker, and
    `infra/scripts/helm-check.sh` (`helm lint --strict` and `helm template`) with Helm 4.3.
- **Alternatives:** a separate Flyway image for migrations; relying on Spring Boot's default security
  (form login with a generated password); copying the profile config into the chart.

## 2026-10-02 — Frontend scaffold and API contract (step 14)

- **Context:** spec 10.3 and 12 ask for TypeScript types generated from the OpenAPI description and for breaking API
  changes to fail the frontend build; spec 7.1 builds the frontend with Gradle into the app.
- **Decision:**
  - `frontend/openapi.json` is a committed snapshot of `/v3/api-docs`. `OpenApiSnapshotTest` fails when the API and
    the snapshot differ (`-PupdateGolden` updates it); the frontend build generates `src/api/schema.d.ts` from it with
    openapi-typescript (not committed) and type-checks against it. A breaking API change therefore fails the backend
    test first and, once the snapshot is updated, the frontend build wherever the change matters. The frontend build
    needs no running backend.
  - The OpenAPI document is made deterministic (sorted keys, relative server `/`), and every non-nullable property is
    marked required, so the generated types match the Kotlin types (`activeSec: number`, `maxSpeed?: number | null`).
  - Stack from the official Vite React-TS template: Vite 8, React 19, TypeScript 6.0 and oxlint; plus TanStack Query 5,
    React Router 8, openapi-fetch as the typed client (API errors become `ApiError` with the problem detail), Vitest
    with Testing Library. openapi-typescript declares `typescript ^5` as peer; an npm `overrides` entry lets it use
    the project's TypeScript 6.0, with which it works.
  - `:frontend` is a Gradle project with the node-gradle plugin (Node.js 24 LTS downloaded, `npm ci`); its npm build,
    test and lint scripts have up-to-date checks and run in `assemble` / `check`. The app copies the `dist` artifact
    into `static/`.
  - The app answers client-side routes (`/sessions/{id}`, ...) with `index.html`; `/api`, `/actuator`, `/v3`,
    `/swagger-ui` and file-like paths keep their 404.
- **Alternatives:** generating types from a running backend; committing the generated types; TypeScript 5.9.

## 2026-10-02 — Sessions list and upload dialog (step 15)

- **Context:** spec 10.1 screens 1-2 leave the interaction details open.
- **Decision:**
  - Filters (surface, date range) and the page live in the URL, so a reload or a shared link keeps them. Date inputs are
    calendar days in the browser's time zone, sent as instants (`from` = start of the first day, `to` = start of the day
    after the last one, exclusive). Start times are shown in the session's own local time (spec 11).
  - Rows show a *Smart recording* badge (decision on Smart recording support) and an *outdated* badge, each with an
    explanation on hover.
  - The upload dialog is a native `<dialog>`; files are sent in one multipart request with `fetch` (the typed client
    adds nothing for multipart). Each result row of a session at an unknown place offers Grass / Sand (a MANUAL
    surface) or a new circular geozone around the session's start (default radius 150 m). The rows follow the session
    queries, so a new geozone that also classifies other sessions shows up everywhere.
  - No UI component library: plain React and CSS.
- **Alternatives:** filters in component state only; one request per uploaded file.

## 2026-10-02 — Session screen charts (step 16)

- **Context:** spec 10.2 asks for three charts joined with `echarts.connect` and for `sampling: 'lttb'` on the lines.
  In ECharts 6.1 the two do not work together.
- **Decision:**
  - **No `sampling`.** On hover, ECharts puts the index of the hovered point *in the sampled data* into the event that
    `connect` forwards. The other charts sample their own values differently, so they find no point and draw no axis
    pointer or tooltip. Unsampled, all three charts share the same `t` array and the indices match. Measured on the
    longest golden session (9 430 s) in headless Chromium: load, hover and zoom take the same time with and without
    lttb. A comment at the line series records this.
  - The recorded speed is drawn through the recorded samples only and breaks in gaps of the series. With Smart
    recording, most seconds have no sample, and a line broken at every one of them would be invisible. Tooltips list
    values of the hovered second only.
  - Time axis labels use the app's duration format (`m:ss`, `h:mm:ss` from one hour) rather than plain `mm:ss`.
  - The segment strip follows the charts' zoom, so it stays aligned with the segment bands on the speed chart.
  - The session screen is loaded on demand (its chunk is about 560 kB, nearly all ECharts, so the Vite warning limit
    is 600 kB); only the ECharts parts in use are registered.
  - `/series` columns are lists of nullable numbers; springdoc drops element nullability, so `SessionSeries` states it
    with `@ArraySchema` for the generated types.
  - The header sets the surface by hand (MANUAL) and offers *Recompute* always, highlighted for outdated sessions.
    The map is a placeholder until step 17.
- **Alternatives:** one ECharts instance with three grids and `axisPointer.link` (keeps lttb, but the spec names
  `connect`); syncing the axis pointer by hand next to `connect`; `average` / `max` sampling (still differs by chart).

## 2026-10-02 — Session map and base maps (step 17)

- **Context:** spec 10.2 wants a vector and a satellite base map, with tile URLs and keys from frontend config and the
  provider terms checked by the implementer. Spec 11 wants one image for every environment.
- **Decision:**
  - **Vector base map: [OpenFreeMap](https://openfreemap.org/) (Liberty style)** by default: free, no key, no
    registration, no request limits; the required attribution is added by MapLibre.
  - **Satellite only when configured.** No satellite source is usable without an account: Esri World Imagery needs an
    ArcGIS Online / Enterprise licence (keyless use is allowed only for OpenStreetMap editing), MapTiler's free plan
    needs a key and covers non-commercial use only, and free Sentinel-2 mosaics (10 m pixels) are too coarse for a
    field. `app.map.satellite.tiles-url` (env `APP_MAP_SATELLITE_TILES_URL`) takes an XYZ URL with the user's own key;
    without it the switch is hidden. README, "Base maps", has an example.
  - **Config at runtime:** `GET /api/config` serves `app.map.*`, so compose (env vars) and Helm (values) configure the
    same image; a Vite build-time variable would bake keys into the jar. This endpoint is an addition to spec 9.1.
    A tile key reaches every browser anyway, so it should be restricted to the host at the provider.
  - Tile requests tell the provider which area is viewed (around the training places).
  - Layers, bottom to top: geozone (fill and dashed outline in its surface colour; circles drawn with 64 vertices),
    track with a light casing, window, effort starts, chart cursor. The view fits the track on load.
  - The window comes from the URL (`?from=&to=`), which the brush of step 18 will write; an invalid window is ignored.
  - The chart cursor reaches the map through a small store, not React state, so mouse moves do not re-render the
    screen.
  - MapLibre 6 finds its worker next to its own module, which bundling moves: Vite builds the worker
    (`?worker&url`, ES format) and `setWorkerUrl` points to it. MapLibre is a chunk of its own (about 1 MB), loaded
    after the charts; the chunk size warning limit is 1100 kB.
- **Alternatives:** Esri World Imagery without a key (not allowed by its terms); `VITE_*` build-time config; fitting
  the view to track and geozone together.

## 2026-10-02 — Session screen interactions (step 18)

- **Context:** spec 10.2 describes the window selection, segment editing and the effort drawer; the details of input
  handling are open.
- **Decision:**
  - **Brush:** every chart has a `lineX` brush, switched on with `takeGlobalCursor`, so dragging selects a window and
    no longer pans; the wheel zooms and the slider pans. The speed chart's `brushEnd` covers all charts, as
    `echarts.connect` repeats the brush there. Windows are whole seconds, at least one long. The URL is updated with
    `replace`, so brushing does not fill the browser history. Clicking the chart keeps the window (`removeOnClick`
    off); *Clear* or Escape removes it.
  - **Window metrics:** requested 200 ms after the window last changed, cached per [from, to] by TanStack Query.
  - **Save as segment:** drill type (optional) and label; a 409 names the segments in the way, found among the
    session's segments, instead of the server's segment id.
  - **Strip:** a click selects the segment's range; its edges drag (mouse events, kept between the neighbouring
    segments, one PATCH on release); a right click opens the menu: split at the clicked time, merge with the next
    segment (a gap between them joins it), drill type (or none), delete, and reset from laps after a browser
    confirmation.
  - **Escape:** closes an open menu or form first, then the effort drawer, then clears the window.
  - **Effort drawer:** a non-modal side panel opened by clicking an effort marker; efforts in start order for
    previous / next; the close-up chart shows speed and GPS acceleration on two axes; distances in m.
  - Segment changes refresh the session, its efforts (their segment can change) and the drill type statistics, not
    the series.
  - Map lines are cut every 2 000 positions: MapLibre draws one line from at most 65 535 vertices, and a 2-hour track
    with round joins needs more.
- **Alternatives:** a toolbox button to switch between brushing and panning; a custom confirmation dialog; the effort
  in the URL.

## 2026-10-02 — Geozones screen (step 19)

- **Context:** spec 10.1 screen 4 asks for "circle (click + radius) or polygon (draw), edit, delete"; the interaction
  details are open.
- **Decision:**
  - **Circle:** a click on the map sets the centre (each click moves it, also when editing); the radius is a number
    field, 150 m by default.
  - **Polygon:** a click per corner; a double-click on the last corner or *Finish* closes it (the double-click's
    repeated corner is dropped); *Undo corner* removes the last one. Editing a polygon means *Redraw*; dragging
    single corners is left out of the POC.
  - Surfaces offered are Grass and Sand, as the API documents for new geozones.
  - Every change reports how many sessions were classified again (`affectedSessionCount`). Deleting asks for a
    browser confirmation; Escape cancels drawing.
  - The map fits all geozones; without any it starts at the latest session's start, where the first field
    probably is. A click on an area selects it, a click in the list also brings it into view.
  - **Fix of step 17:** the API names polygons `Polygon` (as GeoJSON) but the session map looked for `polygon`, so
    polygon geozones were not outlined there; both screens now share one constant for the shape types.
- **Alternatives:** dragging corners and the centre; a radius set by dragging; MapLibre drawing plugins (another
  dependency, no MapLibre 6 support yet checked).

## 2026-10-02 — Drill types screens (step 20)

- **Context:** spec 10.1 screen 5 asks for a list with colour and kind, CRUD, and a detail page with aggregated stats
  and a per-session trend, filtered by surface and date range.
- **Decision:**
  - The list edits in place: name, code, kind and colour. The code follows the name (`Cutting 1v1` → `CUTTING_1V1`)
    until it is typed; colours come from an eight-colour palette or a colour picker, a new type gets the first
    unused palette colour. A taken code shows the server's 409 message; deleting asks for a confirmation (its
    segments stay, untyped).
  - The detail page shows the totals of all matching segments in the session screen's metrics panel, key numbers
    (sessions, segments, time, efforts, best peak speed), a trend chart and one row per session. Filters live in
    the URL like on the sessions list.
  - **Trend:** one point per session on a time axis, coloured by surface, joined in the drill type's colour; the
    metric is chosen from seven values of the session rows (best and mean peak speed, mean first 3 s speed, efforts
    per active minute, moving speed, active time, average heart rate), read from the API's metrics and only
    converted to display units.
  - The detail page is loaded on demand (it shares the ECharts chunk with the session screen).
- **Alternatives:** a dialog for create / edit; several metrics in one trend chart; a free colour field only.

## 2026-10-02 — Playwright smoke test (step 21)

- **Context:** spec 12 asks for an E2E smoke test: upload a golden file → session opens → brush a window → metrics
  panel shows values → save as segment.
- **Decision:**
  - `frontend/e2e/smoke.spec.ts` with `@playwright/test`, Chromium only (desktop only, spec 10.3). It uses the
    shortest golden file and checks real values: the window in the URL, the window's elapsed time in the metrics
    panel, and the saved segment through the API.
  - **Saving needs free time:** the laps of every golden file cover the whole session and segments must not overlap,
    so the test resets the segments to the laps and deletes lap 1 before *Save as segment*, as a user has to today.
    Whether saving should cut the window out of overlapped segments is open (discussed with the user, no change yet).
  - `infra/scripts/e2e.sh` starts a separate compose project (ports 18081 / 15433, image tag `e2e`, own volumes),
    runs the test and removes the project, so the local stack and its data are never touched; the compose file's
    image tag became `${APP_IMAGE_TAG:-local}` for this. A re-run against the same stack also passes.
  - CI runs it in an `e2e` job and keeps the Playwright report and traces of a failure. It is not part of
    `./gradlew build`, which needs no running stack.
  - Vitest skips `e2e/`.
- **Alternatives:** running against the dev stack (would change the user's data); Playwright's `webServer` starting
  `bootRun` (needs a database anyway); several browsers.

## 2026-10-03 — Map track by drill type, window only (backlog)

- **Context:** backlog: thinner map lines, the track coloured by drill type, and the map showing only the selected
  time frame. The user chose a toggle for the brushed window.
- **Decision:**
  - Track 1.5 px grey on a 3 px white casing; each segment's part of the track on top in its drill type's colour
    (2 px), untyped segments in the strip's neutral grey.
  - The window is a translucent blue halo under the track, so the drill colours stay readable.
  - With a window selected, *Whole track / Window only* appears on the map; *Window only* draws only the window's
    track and effort starts and zooms to them.
  - Effort starts are white rings with an orange edge, visible on any drill colour.
  - Map controls sit at the top, next to the zoom buttons: on the narrow session map the attribution covers the
    bottom.
- **Alternatives:** following the charts' zoom (not chosen by the user); hiding the window halo in window-only mode.

## 2026-10-03 — Rest and active periods on the charts (backlog)

- **Context:** backlog: show rest and active periods clearly; pauses were shaded on the speed chart only, at 7 %.
- **Decision:**
  - Rest (pauses, spec 6.3) is shaded on all three charts at 15 % grey; the tooltip names the activity of the
    hovered second (`50:43, rest`, `active` or `no data`).
  - An *Activity* band under the segment strip shows active time (dark), rest (light) and gaps (hatched), follows
    the zoom and selects a run as the window on click. Both strips carry a label in the charts' left margin; a
    legend explains the band.
  - One run model (`activityRuns`) feeds the shading, the tooltip and the band.
- **Alternatives:** shading active time instead of rest; a separate rest/active chart.

## 2026-10-03 — Map when picking a surface (backlog)

- **Context:** backlog: see where a training took place when choosing grass or sand in the upload dialog.
- **Decision:** a session at an unknown place shows a small map with its start, the existing geozones and, while
  *Create geozone* is open, a preview of the circle. Only one map is shown at a time (browsers allow few WebGL
  contexts and a batch upload can hold a dozen unknown places): the first unknown place, others on *Show on map*.
- **Alternatives:** one map per row; one shared map with all unknown starts as numbered points.

## 2026-10-03 — Deleting sessions (backlog)

- **Context:** backlog: delete sessions from the UI, also several at once; the API already had
  `DELETE /api/sessions/{id}`.
- **Decision:** checkboxes (and select-all of the page) in the sessions list with *Delete selected*, and *Delete* on
  the session screen, which returns to the list. Both ask first, saying that segments and notes are lost and the
  files can be uploaded again. Several sessions are deleted one request each; a partial failure says how many were
  not deleted. After a delete, every session query is refreshed except those of the deleted sessions: refetched they
  fail, and the session screen would show "does not exist" instead of navigating away.
- **Alternatives:** a batch delete endpoint (not needed for a handful of sessions); selection across pages.

## 2026-10-03 — Saving over segments, no segment for a single lap (backlog, spec changed)

- **Context:** *Save as segment* failed on every session: each FIT lap became a segment, laps cover the whole
  session, and segments must not overlap (409). The user chose both proposed fixes and approved changing the spec
  (sections 6.1 step 10, 9.1 and 10.2 updated).
- **Decision:**
  - `POST /api/sessions/{id}/segments?overwrite=true` cuts the new segment's time out of the segments it overlaps, in
    the same transaction: a segment around it is split (the first part keeps its id), one it reaches into is
    shortened, a covered one is removed; leftovers shorter than 10 s are removed too. Parts that changed bounds
    become MANUAL and lose their cached metrics; efforts are re-attributed. Without `overwrite` an overlap is still a
    409.
  - The UI always saves with `overwrite=true`; the form lists beforehand what happens to each segment in the way
    ("Lap 1 is split around it").
  - Import and *Reset from laps* create no segment from a single lap: it is the whole session and says nothing about
    drills. Sessions imported before keep their lap segment; saving over it now works.
  - The smoke test saves the window directly again.
- **Alternatives:** removing existing single-lap segments with a migration (it would change user data).
