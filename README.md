# Ultimate Analytics

A single-user web app that stores Ultimate Frisbee training sessions recorded on a Garmin watch (FIT files)
and computes sprint-oriented analytics: pauses, efforts (sprints / cuts) and metrics per session, segment,
drill type and any selected time window.

- Specification: [docs/SPEC.md](docs/SPEC.md)
- Decisions taken during implementation: [docs/DECISIONS.md](docs/DECISIONS.md)

> Work in progress. The commands below describe the target setup; they become available step by step.

## Prerequisites

- JDK 21 (selected through the Gradle toolchain; Gradle itself may run on a newer JDK)
- Docker with Compose v2
- Node.js LTS (frontend)
- Helm (optional, only for `helm lint` / `helm template`)

## Run locally

```sh
./infra/scripts/dev-up.sh            # build the image, start Postgres + app on http://localhost:8080
./infra/scripts/dev-down.sh          # stop the stack
./infra/scripts/dev-down.sh --volumes  # stop and delete the database and stored raw files
```

On the first run `dev-up.sh` creates `infra/docker/.env` from `infra/docker/.env.example`.
The app and Postgres are bound to `127.0.0.1` only; if port 8080 or 5432 is taken, change `APP_PORT` /
`POSTGRES_PORT` in `infra/docker/.env`. Health: `http://localhost:8080/actuator/health`.

### Backend from the IDE

```sh
./infra/scripts/dev-up.sh --db-only  # only Postgres
./gradlew :app:bootRun               # app with the `local` profile and credentials from infra/docker/.env
```

### Frontend dev server

```sh
cd frontend && npm run dev           # Vite dev server, proxies /api to the app
```

## Build and test

```sh
./gradlew build                      # compile and run all tests (integration tests need a running Docker)
```

## Repository layout

| Path | Contents |
| --- | --- |
| `domain/` | Core types and `AnalysisParameters`; no dependencies |
| `fit-parser/` | FIT file parsing with the Garmin FIT SDK |
| `analysis/` | Pure analysis pipeline: grid, smoothing, pauses, efforts, metrics, geo |
| `app/` | Spring Boot application: REST API, persistence, storage, security, static frontend |
| `infra/db-migrations/` | Flyway SQL migrations, packaged as a resources-only jar |
| `infra/docker/`, `infra/config/`, `infra/scripts/` | Image, local compose stack, profile configs, scripts |
| `frontend/` | React + TypeScript SPA |
| `notebooks/` | Kotlin Notebooks for calibration |
