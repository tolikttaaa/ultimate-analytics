# Backlog

Future improvements beyond `docs/SPEC.md`, collected while building the POC. Not part of steps 0–21 unless the user
schedules them.

## Open

Nothing open.

## Done

Implemented on 2026-10-03; decisions in `docs/DECISIONS.md`.

| Added | Idea | Done in |
| --- | --- | --- |
| 2026-10-02 | Show where a training took place when picking its surface: a map with its start point (or track) next to the *Grass* / *Sand* choice in the upload dialog. | 6e2de56 |
| 2026-10-02 | Light and dark themes for the web UI, chosen by the user; default follows the system (`prefers-color-scheme`). | 1cd296c |
| 2026-10-02 | A more modern visual design for the site. | 1d9a8fe |
| 2026-10-02 | On the session map, show the path of the selected time frame only, instead of the whole track with the window highlighted on top. | 3831121 |
| 2026-10-02 | A user guide (`docs/USER_GUIDE.md`): how to use the app and what each element means, with screenshots carrying numbered marks. | this guide (docs/USER_GUIDE.md) |
| 2026-10-02 | *Save as segment* fails with "The window overlaps Lap 1" on every session: import turns each FIT lap into a segment, the laps cover the whole session, and segments must not overlap (409). Today the user has to delete or split the lap first. | fdd9d82 |
| 2026-10-03 | Delete sessions from the UI, also several at once (e.g. checkboxes in the sessions list, and a *Delete* action on the session screen). | 0cefd0c |
| 2026-10-03 | Thinner lines on the session map, and the track coloured by drill type: each segment's part of the track in its drill type's colour (as on the strip and the speed chart). | 3831121 |
| 2026-10-03 | Show rest and active periods clearly on the charts. | 995fe82 |
