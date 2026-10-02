import type { SessionDetail, SessionPage, SessionSummary } from '../api/types'

export function sessionSummary(overrides: Partial<SessionSummary> = {}): SessionSummary {
  return {
    id: 's1',
    startTime: '2026-03-25T13:39:38Z',
    localTzOffsetSec: 7200,
    fileName: '22296100401_ACTIVITY.fit',
    geozoneId: null,
    geozoneName: null,
    surface: 'UNKNOWN',
    surfaceSource: 'NONE',
    recordingMode: 'EVERY_SECOND',
    elapsedSec: 7800,
    activeSec: 1857,
    effortCount: 26,
    maxSpeed: 7.6,
    movingSpeed: 3.9,
    movingPaceSecPerKm: 256,
    outdated: false,
    ...overrides,
  }
}

export function sessionPage(items: SessionSummary[], overrides: Partial<SessionPage> = {}): SessionPage {
  return { items, page: 0, size: 20, totalItems: items.length, totalPages: items.length > 0 ? 1 : 0, ...overrides }
}

/** The parts of a session detail the tests look at. */
export function sessionDetail(overrides: Partial<SessionDetail> = {}): SessionDetail {
  return {
    id: 's1',
    fileName: '22296100401_ACTIVITY.fit',
    startTime: '2026-03-25T13:39:38Z',
    startPosition: { lat: 34.70786, lon: 33.12787 },
    surface: 'UNKNOWN',
    surfaceSource: 'NONE',
    ...overrides,
  } as SessionDetail
}
