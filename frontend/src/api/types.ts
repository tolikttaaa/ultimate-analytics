import type { components } from './schema'

/* Names for the generated API types; the types themselves come from openapi.json (no hand-written DTOs). */
type Schemas = components['schemas']

export type SessionSummary = Schemas['SessionSummary']
export type SessionDetail = Schemas['SessionDetail']
export type SessionPage = Schemas['PageSessionSummary']
export type SessionPatch = Schemas['SessionPatch']
export type Surface = SessionSummary['surface']
export type SurfaceSource = SessionSummary['surfaceSource']
export type UploadResult = Schemas['UploadResult']
export type GeozoneCreate = Schemas['GeozoneCreate']
export type GeozoneChange = Schemas['GeozoneChangeDto']
export type AnalysisParameters = Schemas['AnalysisParametersDto']
export type SessionSeries = Schemas['SessionSeries']
export type Effort = Schemas['EffortDto']
export type Segment = Schemas['SegmentDto']
export type DrillType = Schemas['DrillType']
export type WindowMetrics = Schemas['WindowMetrics']
export type MeanAndBest = Schemas['MeanAndBest']
