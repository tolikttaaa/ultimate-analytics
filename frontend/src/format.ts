/*
 * Display units (spec 10.3): the API sends m, s, m/s and m/s²; the UI shows speed in km/h, pace in min/km and
 * durations as h:mm:ss. Every function returns '–' for a missing value.
 */

const MISSING = '–'

const pad = (value: number) => value.toString().padStart(2, '0')

/** Speed in km/h with one decimal, e.g. `21.6 km/h`. */
export function speedKmh(metersPerSecond: number | null | undefined): string {
  if (metersPerSecond == null) return MISSING
  return `${(metersPerSecond * 3.6).toFixed(1)} km/h`
}

/** Pace as min:ss per km, e.g. `4:10 /km`. */
export function pace(secondsPerKm: number | null | undefined): string {
  if (secondsPerKm == null || !Number.isFinite(secondsPerKm)) return MISSING
  const total = Math.round(secondsPerKm)
  return `${Math.floor(total / 60)}:${pad(total % 60)} /km`
}

/** A duration as h:mm:ss, or m:ss below an hour, e.g. `1:05:09` or `4:07`. */
export function duration(seconds: number | null | undefined): string {
  if (seconds == null) return MISSING
  const total = Math.round(seconds)
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const rest = total % 60
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(rest)}` : `${minutes}:${pad(rest)}`
}

/** Distance in m below 1 km, else km with two decimals, e.g. `850 m`, `5.34 km`. */
export function distance(meters: number | null | undefined): string {
  if (meters == null) return MISSING
  return meters < 1000 ? `${Math.round(meters)} m` : `${(meters / 1000).toFixed(2)} km`
}

/** GPS acceleration in m/s² with one decimal; always marked as GPS-derived (spec 4.3). */
export function gpsAccel(metersPerSecondSquared: number | null | undefined): string {
  if (metersPerSecondSquared == null) return MISSING
  return `${metersPerSecondSquared.toFixed(1)} m/s² (GPS)`
}

/** Start time of a session in the session's own local time (spec 11), e.g. `2026-09-30 19:07`. */
export function localDateTime(instant: string, offsetSec: number | null | undefined): string {
  const local = new Date(Date.parse(instant) + (offsetSec ?? 0) * 1000)
  return `${local.getUTCFullYear()}-${pad(local.getUTCMonth() + 1)}-${pad(local.getUTCDate())} ` +
    `${pad(local.getUTCHours())}:${pad(local.getUTCMinutes())}`
}
