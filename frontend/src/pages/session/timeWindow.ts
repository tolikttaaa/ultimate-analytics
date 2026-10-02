/** A time window of a session: [from, to] in seconds t (spec 5). */
export type TimeWindow = [number, number]

/**
 * The window in the URL (`?from=600&to=1200`, spec 10.2), or null when it is missing or does not fit a session of
 * `lastT` seconds.
 */
export function parseWindow(params: URLSearchParams, lastT: number): TimeWindow | null {
  const from = params.get('from')
  const to = params.get('to')
  if (!from || !to || !/^\d+$/.test(from) || !/^\d+$/.test(to)) return null
  const window: TimeWindow = [Number(from), Number(to)]
  return window[0] < window[1] && window[1] <= lastT ? window : null
}
