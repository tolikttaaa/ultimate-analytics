# Backlog

Future improvements beyond `docs/SPEC.md`, collected while building the POC. Not part of steps 0–21 unless the user
schedules them.

| Added | Idea | Notes |
| --- | --- | --- |
| 2026-10-02 | Show where a training took place when picking its surface: a map with its start point (or track) next to the *Grass* / *Sand* choice in the upload dialog. | Reuse the MapLibre map of step 17; the session detail already has `startPosition`. |
| 2026-10-02 | Light and dark themes for the web UI, chosen by the user; default follows the system (`prefers-color-scheme`). | Most colours are CSS variables in `styles.css` already; the chart colours are fixed in `sessionChartOptions.ts` and need theme-aware values (or ECharts' dark theme), as will the map style. |
| 2026-10-02 | A more modern visual design for the site. | Today: plain React and CSS, no component library (see `DECISIONS.md`, step 15). |
