# Ultimate Analytics

[![CI](https://github.com/tolikttaaa/ultimate-analytics/actions/workflows/ci.yml/badge.svg)](https://github.com/tolikttaaa/ultimate-analytics/actions/workflows/ci.yml)

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

The REST API lives under `/api` (spec 9). Its OpenAPI description is at `http://localhost:8080/v3/api-docs`, browsable
with Swagger UI at `http://localhost:8080/swagger-ui.html`. Upload FIT files or Garmin Connect `.zip` exports with
`curl -F files=@training.fit http://localhost:8080/api/sessions/upload`.

### Backend from the IDE

```sh
./infra/scripts/dev-up.sh --db-only  # only Postgres
./gradlew :app:bootRun               # app with the `local` profile and credentials from infra/docker/.env
```

### Frontend dev server

```sh
cd frontend && npm install && npm run dev   # http://localhost:5173, proxies /api to the app on :8080
```

`./gradlew build` builds the frontend into the app jar (it downloads Node.js itself) and runs its tests and lint.
The TypeScript API types are generated from `frontend/openapi.json`, a snapshot of the API that a backend test keeps
in sync: after an API change, run `./gradlew :app:test -PupdateGolden`, then fix what the frontend build reports.

## Build and test

```sh
./gradlew build                      # compile and run all tests (integration tests need a running Docker)
./infra/scripts/helm-check.sh        # helm lint and helm template of the chart skeleton
```

CI (GitHub Actions) runs both on every push and pull request.

## Base maps

The session map uses [OpenFreeMap](https://openfreemap.org/) vector tiles by default: free, no key, attribution shown
on the map. A satellite base map is optional because the satellite providers need an account key and have their own
terms. To add one, set an XYZ raster tile URL in `infra/docker/.env` (or the Helm values) and restart:

```
APP_MAP_SATELLITE_TILES_URL=https://api.maptiler.com/tiles/satellite-v4/{z}/{x}/{y}?key=YOUR_KEY
APP_MAP_SATELLITE_ATTRIBUTION=© MapTiler
```

This example is MapTiler, whose free plan covers personal and non-commercial use. Read the provider's terms for
attribution and limits, check its tile size (`APP_MAP_SATELLITE_TILE_SIZE`, 256 by default), and restrict the key to
your host: the browser sees it.

## Server deployment (future)

The POC runs locally only. `infra/helm/ultimate-analytics` is a chart skeleton for a later server deployment
(spec 7.5): the `server` profile (`infra/config/application-server.yml`), a pre-install / pre-upgrade migration Job,
probes on a separate management port and JSON logs. Until a login is implemented the `server` profile denies all API
requests.

```sh
helm upgrade --install analytics infra/helm/ultimate-analytics -f values-server.yaml \
  --set-file config.applicationServerYml=infra/config/application-server.yml
```

See `values-server.example.yaml` for the values to provide.

## License

[MIT](LICENSE)

## Repository layout

| Path | Contents |
| --- | --- |
| `domain/` | Core types and `AnalysisParameters`; no dependencies |
| `fit-parser/` | FIT file parsing with the Garmin FIT SDK |
| `analysis/` | Pure analysis pipeline: grid, smoothing, pauses, efforts, metrics, geo |
| `app/` | Spring Boot application: REST API, persistence, storage, security, static frontend |
| `infra/db-migrations/` | Flyway SQL migrations, packaged as a resources-only jar |
| `infra/docker/`, `infra/config/`, `infra/scripts/` | Image, local compose stack, profile configs, scripts |
| `infra/helm/ultimate-analytics/` | Helm chart skeleton for a future server deployment |
| `frontend/` | React + TypeScript SPA |
| `notebooks/` | Kotlin Notebooks for calibration |
