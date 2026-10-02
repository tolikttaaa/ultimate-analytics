# Ultimate Frisbee Training Analytics — working instructions

## Source of truth

- The specification is `docs/SPEC.md`. Read it fully before writing any code.
  Section numbers below (e.g. "6.4") refer to it.
- If the spec is ambiguous, contradictory or missing something you need:
  stop and ask the user. For small, low-risk choices, decide, record the
  decision in `docs/DECISIONS.md` (date, context, decision, alternatives) and
  mention it in your summary.
- Never change `docs/SPEC.md` without the user's explicit approval.

## Prerequisites (check at step 0, report anything missing)

- JDK 21, Docker with compose, Node.js LTS, git.
- Helm is optional locally (needed only for `helm lint` in step 13).

## How to work

- Implement the steps below in order. Each step is one logical part and ends
  with exactly one commit (Conventional Commits, e.g. `feat(analysis): effort detection`).
- Before every commit: `./gradlew build` passes (and `npm run build` + tests in
  `frontend/` once it exists). Never commit a red build.
- Never add a git remote or push unless the user asks.
- Never commit secrets. Only `.env.example` / `values-server.example.yaml`.
- Stop and give a short summary at the end of each milestone (M1–M4) and wait
  for the user before continuing.
- Respect the module dependency rules in spec 7.2:
  - `domain` depends on nothing.
  - `analysis` never imports Spring, does no I/O and has no FIT SDK.
  - SQL lives only in `infra:db-migrations`.
  - The frontend never computes metrics.
- Every threshold is a field of `AnalysisParameters` with a default.
  Changing a default bumps `ANALYSIS_VERSION`.
- Base package: ask the user at step 1 (suggest `com.<name>.ultimate`).

## Implementation steps

### Setup

0. **Repository init.**
   - `git init`, `.gitignore` (Gradle, IntelliJ, `node_modules`, `storage/`, `.env`), `.editorconfig`.
   - `README.md` with how-to-run.
   - Commit `docs/SPEC.md` and this file.
1. **Gradle skeleton.**
   - Wrapper, `settings.gradle.kts`, `gradle/libs.versions.toml`.
   - Empty modules `domain`, `fit-parser`, `analysis`, `infra:db-migrations`, `app` (Spring Boot starts).
   - JUnit 5 + Kotest configured.
2. **Infra (local).**
   - `infra/docker` (Dockerfile, `docker-compose.yml`, `.env.example`).
   - `infra/config/application-local.yml`.
   - `infra/scripts/dev-up.sh`, `infra/scripts/dev-down.sh`.
   - `V1__init.sql` (spec 8.1).
   - Check that the app starts against compose Postgres and Flyway applies V1.

### M1: core

3. **domain.** Types from spec 5 and `AnalysisParameters` from 6.2.
4. **fit-parser.**
   - Parsing per spec 4.1.
   - Until real files exist, tests use synthetic FIT files generated with the FIT SDK encoder.
   - Real golden files go to `fit-parser/src/test/resources/fit/`; ask the user for them.
5. **analysis: grid and gaps.** 1 Hz grid, interpolation, outlier removal (6.1 steps 2–4).
6. **analysis: smoothing.** Savitzky–Golay speed and acceleration (6.1 steps 5–6).
7. **analysis: pauses.** Pause detection, active time, moving speed (6.3).
8. **analysis: efforts.** Effort detection and per-effort metrics (6.4).
9. **analysis: window metrics.** Window metrics and drill-type aggregation (6.5).
10. **analysis: geo.** Haversine, point-in-polygon, geozone matching (6.6).
    - Add the property-based tests from spec 12.
    - Add a Kotlin Notebook scaffold in `notebooks/` that loads a FIT file and plots speed with detected efforts.
    - Stop: M1 summary.

### M2: backend

11. **app: persistence and storage.**
    - JdbcClient repositories.
    - `RawFileStore` with the filesystem implementation.
    - Upload flow (7.4) with Testcontainers integration tests.
12. **app: API.**
    - Endpoints of 9.1, area by area: sessions, series and metrics, segments, drill types, geozones, recompute. One commit per area.
    - springdoc-openapi.
    - Errors as RFC 7807 problem+json.
    - Spring Security with the open `local` `SecurityFilterChain`.
13. **Infra (server skeleton).**
    - `infra/config/application-server.yml`.
    - Helm chart skeleton (7.5).
    - CI workflow running `./gradlew build`, `helm lint` and `helm template`. Ask the user which CI system to use; default GitHub Actions.
    - Stop: M2 summary.

### M3: frontend

14. **Frontend scaffold.**
    - Vite + React + TS, TanStack Query, React Router.
    - Types generated from OpenAPI with openapi-typescript.
    - Frontend built into the app jar by Gradle.
15. **Sessions list and upload dialog** (10.1 screens 1–2).
16. **Session screen: charts.** Synchronised ECharts, pauses, segments, effort markers (10.2).
17. **Session screen: map.** MapLibre track, window highlight, cursor point.
18. **Session screen: interactions.**
    - Brush to window metrics, save as segment.
    - Segment strip editing.
    - Effort drawer.
    - Stop: M3 summary.

### M4: geozones and drill types

19. **Geozones screen.**
20. **Drill types screen** with stats.
21. **Playwright smoke test** (spec 12).
    - Stop: M4 summary against the acceptance criteria in spec 13.

## Common commands

- Start local stack: `./infra/scripts/dev-up.sh`
- Build and test backend: `./gradlew build`
- Frontend dev server: `cd frontend && npm run dev` (proxies `/api`)
