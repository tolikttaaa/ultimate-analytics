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
