# Backlog

Future improvements beyond `docs/SPEC.md`, collected while building the POC. Not part of steps 0–21 unless the user
schedules them.

| Added | Idea | Notes |
| --- | --- | --- |
| 2026-10-02 | Show where a training took place when picking its surface: a map with its start point (or track) next to the *Grass* / *Sand* choice in the upload dialog. | Reuse the MapLibre map of step 17; the session detail already has `startPosition`. |
| 2026-10-02 | Light and dark themes for the web UI, chosen by the user; default follows the system (`prefers-color-scheme`). | Most colours are CSS variables in `styles.css` already; the chart colours are fixed in `sessionChartOptions.ts` and need theme-aware values (or ECharts' dark theme), as will the map style. |
| 2026-10-02 | A more modern visual design for the site. | Today: plain React and CSS, no component library (see `DECISIONS.md`, step 15). |
| 2026-10-02 | On the session map, show the path of the selected time frame only, instead of the whole track with the window highlighted on top. | Step 18 selects the window by brushing the charts. Open: should the zoomed chart range also count as "selected"? A toggle (whole track / selection only) would keep both views. |
| 2026-10-02 | A user guide (`docs/USER_GUIDE.md`): how to use the app and what each element means, with screenshots carrying numbered marks. | Playwright (step 21) can take the screenshots and draw the marks, so the guide can be regenerated when the UI changes. Screenshots with maps show training places, and the repo is public: decide whether to use the real tracks or demo data. |
| 2026-10-02 | *Save as segment* fails with "The window overlaps Lap 1" on every session: import turns each FIT lap into a segment, the laps cover the whole session, and segments must not overlap (409). Today the user has to delete or split the lap first. | Options discussed: (1) saving cuts the window out of the overlapped segments in one server transaction (splits or trims them), with the form showing what gets cut; (2) on import, skip a single lap that covers the whole session. Both change spec 10.2 / the import behaviour. The smoke test (`frontend/e2e/smoke.spec.ts`) works around it by deleting lap 1 first. |
