/*
 * Date filters: the date inputs give calendar days (YYYY-MM-DD) in the user's time zone; the API filters session start
 * times with ISO instants, `from` inclusive and `to` exclusive.
 */

/** The instant a calendar day starts in the user's time zone. */
export function startOfDay(day: string): string {
  const [year, month, date] = day.split('-').map(Number)
  return new Date(year, month - 1, date).toISOString()
}

/** The instant after the end of a calendar day: the exclusive `to` of a range that includes the day. */
export function endOfDay(day: string): string {
  const [year, month, date] = day.split('-').map(Number)
  return new Date(year, month - 1, date + 1).toISOString()
}
