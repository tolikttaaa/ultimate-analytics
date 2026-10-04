# Golden FIT files

Real Ultimate trainings provided by the user (Garmin Forerunner 965, Ultimate Disc activity profile), exported from
Garmin Connect. Thirteen are regular trainings recorded with Smart recording; `24586714768_ACTIVITY.fit` is golden
file 4 of spec 12, the first recorded every second.

## Golden files of spec 12

| # | Spec 12 | File | Status |
| --- | --- | --- | --- |
| 1 | Calibration sprints (10 × 20 m / 40 m, lap before each) | – | to be recorded |
| 2 | Pause check (3 min each standing, walking, jogging, a lap each) | – | to be recorded |
| 3 | Typical grass training with laps per drill | – | to be recorded (the grass files below have no laps per drill and Smart recording) |
| 4 | Typical sand training with laps per drill | `24586714768_ACTIVITY.fit` | recorded 2026-10-03 on a beach, every second, 3 manual laps; the written protocol (what each lap was) is still to be added |
| 5 | Edge cases (GPS dropout, timer stop / start, non-Ultimate activity) | timer stops in `22245986957`, `22296100401`, `24075127340` | GPS dropout and a non-Ultimate activity to be recorded |

`expected/` holds the approved parser output of every file. After an intended change of the parser output, run
`./gradlew :fit-parser:test -PupdateGolden` and review the diff before committing.

## What the files show (relevant for spec 4.2)

- **Ultimate Disc profile:** `sport = disc_golf` (69), `sub_sport = ultimate` (92).
- **Smart recording, not every second:** records are 1–6 s apart; only 25–37 % of the intervals are 1 s.
  Spec 4.2 requires the "Every Second" recording interval; golden file 4 has it (every interval is 1 s).
- **Laps:** the Lap button was not used in the Smart recordings, except once; most of them have a single lap covering
  the whole session. Golden file 4 has three manual laps.
- **Distance:** the analysis counts distance only above `minDistanceSpeed`, ignoring standing (`docs/DECISIONS.md`). Every-second recordings
  collect more GPS jitter while standing, so the watch's distance is higher: 4.28 km against 3.45 km in golden file 4
  (about 7 % in the Smart recordings).
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
| `24586714768_ACTIVITY.fit` | 2026-10-03 | 116 min | 6968 | 100 % | 1 s | **golden file 4**: sand, every second, 3 manual laps (10:12, 31:36, 1:14:19), first 4 s without position |

Surface (grass / sand) and the drills done are not recorded here yet, except for golden file 4 (sand).
