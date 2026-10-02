import type {
  DrillType,
  Effort,
  Geozone,
  Segment,
  SessionDetail,
  SessionPage,
  SessionSeries,
  SessionSummary,
  WindowMetrics,
} from '../api/types'
import type { components } from '../api/schema'

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

export function windowMetrics(overrides: Partial<WindowMetrics> = {}): WindowMetrics {
  return {
    time: { elapsedSec: 7800, activeSec: 1857, pausedSec: 5900, gapSec: 43, workRestRatio: 0.31 },
    distance: { distanceM: 7480, activeDistanceM: 6900, maxSpeed: 7.6, movingSpeed: 3.9, movingPaceSecPerKm: 256 },
    efforts: {
      count: 26,
      perActiveMin: 0.84,
      peakSpeed: { mean: 6.5, best: 7.6 },
      meanSpeed: { mean: 5.1, best: 6.0 },
      meanSpeedFirst3s: { mean: 3.9, best: 4.6 },
      timeTo80PctPeakSec: { mean: 2.4, best: 1.1 },
      meanAccel: { mean: 1.6, best: 2.3 },
      peakAccel: { mean: 2.8, best: 4.1 },
    },
    decelCount: 31,
    fatigue: { peakSpeedDropPct: 4.2 },
    heartRate: { avg: 148.6, max: 187, zonesPct: [10, 20, 30, 25, 15] },
    zones: (['STAND', 'WALK', 'JOG', 'RUN', 'HIGH_SPEED', 'SPRINT'] as const).map((zone, i) => ({
      zone,
      timeSec: 600 - i * 60,
      distanceM: 400 + i * 100,
    })),
    ...overrides,
  }
}

export function sessionDetail(overrides: Partial<SessionDetail> = {}): SessionDetail {
  return {
    id: 's1',
    fileName: '22296100401_ACTIVITY.fit',
    startTime: '2026-03-25T13:39:38Z',
    localTzOffsetSec: 7200,
    uploadedAt: '2026-10-02T10:00:00Z',
    elapsedSec: 7800,
    timerSec: 7700,
    distanceM: 7480,
    analysisVersion: 1,
    outdated: false,
    recordingMode: 'EVERY_SECOND',
    device: 'Forerunner 965',
    sport: 'training',
    subSport: null,
    startPosition: { lat: 34.70786, lon: 33.12787 },
    geozoneId: null,
    geozoneName: null,
    surface: 'UNKNOWN',
    surfaceSource: 'NONE',
    notes: null,
    laps: [],
    segments: [],
    metrics: windowMetrics(),
    ...overrides,
  }
}

/**
 * Ten seconds: a run up to 6 m/s at t = 3, a pause at t = 6..7 and a gap at t = 8.
 */
export function sessionSeries(): SessionSeries {
  return {
    sessionId: 's1',
    startTime: '2026-03-25T13:39:38Z',
    t: [0, 1, 2, 3, 4, 5, 6, 7, 8, 9],
    speed: [1, 3, 5, 6, 4, 2, 0, 0, null, 1],
    speedRaw: [1.2, 2.8, 5.1, 6.2, 3.9, 2.1, 0.1, 0, null, 1],
    accel: [0, 2, 2, 1, -2, -2, -2, 0, null, 0],
    hr: [120, 125, 140, 160, 170, 165, 150, 140, null, 130],
    lat: [34.7, 34.7, 34.7, 34.7, 34.7, 34.7, 34.7, 34.7, null, 34.7],
    lon: [33.1, 33.1, 33.1, 33.1, 33.1, 33.1, 33.1, 33.1, null, 33.1],
    inPause: [false, false, false, false, false, false, true, true, null, false],
    interpolated: [false, false, false, false, false, false, false, false, null, false],
  }
}

export function effort(overrides: Partial<Effort> = {}): Effort {
  return {
    id: 'e1',
    startT: 1,
    peakT: 3,
    endT: 5,
    segmentId: null,
    metrics: {
      durationSec: 4,
      distanceM: 18,
      startSpeed: 3,
      peakSpeed: 6,
      meanSpeed: 4.5,
      timeToPeakSec: 2,
      timeTo80PctPeakSec: 1.6,
      speedAt1s: 5,
      speedAt2s: 6,
      speedAt3s: 4,
      meanSpeedFirst3s: 5,
      distanceFirst3s: 15,
      meanAccel: 1.5,
      peakAccel: 2,
      maxDecelAfter: -2,
      hrStart: 125,
      hrMax: 170,
    },
    ...overrides,
  }
}

export function segment(overrides: Partial<Segment> = {}): Segment {
  return { id: 'seg1', startT: 0, endT: 6, drillTypeId: 'd1', label: null, source: 'LAP', ...overrides }
}

export function drillType(overrides: Partial<DrillType> = {}): DrillType {
  return { id: 'd1', code: 'sprints', name: 'Sprints', kind: 'DRILL', color: '#e65100', ...overrides }
}

export function geozone(overrides: Partial<Geozone> = {}): Geozone {
  return {
    id: 'g1',
    name: 'Akrotiri field',
    surface: 'GRASS',
    shape: { type: 'circle', lat: 34.70786, lon: 33.12787, radiusM: 150, coordinates: null },
    ...overrides,
  }
}

export function uiConfig(): components['schemas']['UiConfig'] {
  return { map: { vectorStyleUrl: 'https://tiles.openfreemap.org/styles/liberty', satellite: null } }
}
