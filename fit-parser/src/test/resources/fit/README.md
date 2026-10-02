# Golden FIT files

Real Ultimate trainings provided by the user (Garmin Forerunner 965, Ultimate Disc activity profile), exported from
Garmin Connect. They are regular trainings, not the protocol recordings of spec 12 (calibration sprints, pause check,
edge cases), which are still to be recorded.

`expected/` holds the approved parser output of every file. After an intended change of the parser output, run
`./gradlew :fit-parser:test -PupdateGolden` and review the diff before committing.

## What the files show (relevant for spec 4.2)

- **Ultimate Disc profile:** `sport = disc_golf` (69), `sub_sport = ultimate` (92).
- **Smart recording, not every second:** records are 1–6 s apart; only 25–37 % of the intervals are 1 s.
  Spec 4.2 requires the "Every Second" recording interval.
- **Laps:** the Lap button was not used, except once; most files have a single lap covering the whole session.
- **Lap timestamps:** the lap `timestamp` field holds the activity start; lap ends are `start_time + total_elapsed_time`.
- **Fields:** `enhanced_speed` only (no `speed`), no altitude, positions from the first or second record on,
  heart rate in every record except one session.

| File | Date | Duration | Records | 1 s intervals | Longest interval | Notes |
| --- | --- | --- | --- | --- | --- | --- |
| `21766058119_ACTIVITY.fit` | 2026-01-31 | 126 min | 3182 | 37 % | 6 s | – |
| `22245986957_ACTIVITY.fit` | 2026-03-21 | 65 min | 1560 | 36 % | 6 s | timer stopped once (1 s) |
| `22296100401_ACTIVITY.fit` | 2026-03-25 | 130 min | 2907 | 32 % | 10 s | 2 laps (manual), timer stopped once (10 s) |
| `22401733552_ACTIVITY.fit` | 2026-04-04 | 137 min | 3264 | 34 % | 6 s | – |
| `22484870944_ACTIVITY.fit` | 2026-04-11 | 144 min | 3274 | 33 % | 6 s | – |
| `24039722147_ACTIVITY.fit` | 2026-08-19 | 114 min | 2873 | 35 % | 6 s | – |
| `24075127340_ACTIVITY.fit` | 2026-08-22 | 113 min | 2429 | 28 % | 6 s | timer stopped once (1 s), HR missing in 47 % of records |
| `24213864687_ACTIVITY.fit` | 2026-09-02 | 113 min | 2516 | 32 % | 6 s | – |
| `24249547764_ACTIVITY.fit` | 2026-09-05 | 157 min | 3015 | 25 % | 6 s | – |
| `24255189523_ACTIVITY.fit` | 2026-09-06 | 125 min | 2407 | 26 % | 6 s | – |
| `24387264896_ACTIVITY.fit` | 2026-09-16 | 116 min | 2766 | 35 % | 6 s | – |
| `24421586382_ACTIVITY.fit` | 2026-09-19 | 110 min | 2308 | 30 % | 6 s | – |
| `24557963847_ACTIVITY.fit` | 2026-09-30 | 109 min | 2662 | 36 % | 6 s | – |

Surface (grass / sand) and the drills done are not recorded here yet.
