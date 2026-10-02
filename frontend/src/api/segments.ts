import { type QueryClient, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, unwrap } from './client'
import { drillTypeKeys } from './drillTypes'
import { sessionKeys } from './sessions'
import type { Schemas } from './types'

/** Segments change the session detail, the effort-to-segment links and the drill type statistics. */
function refresh(queryClient: QueryClient, sessionId: string) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: sessionKeys.detail(sessionId) }),
    queryClient.invalidateQueries({ queryKey: sessionKeys.efforts(sessionId) }),
    queryClient.invalidateQueries({ queryKey: drillTypeKeys.all }),
  ])
}

function useSegmentMutation<T>(sessionId: string, call: (path: { id: string }, input: T) => Promise<unknown>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: T) => call({ id: sessionId }, input),
    onSuccess: () => refresh(queryClient, sessionId),
  })
}

/** POST /api/sessions/{id}/segments; 409 when it overlaps another segment. */
export const useCreateSegment = (sessionId: string) =>
  useSegmentMutation(sessionId, (path, body: Schemas['SegmentCreate']) =>
    unwrap(api.POST('/api/sessions/{id}/segments', { params: { path }, body })))

/** PATCH a segment: absent fields stay, null clears drill type or label; 409 on overlap. */
export const useUpdateSegment = (sessionId: string) =>
  useSegmentMutation(sessionId, (path, { segmentId, patch }: { segmentId: string; patch: Schemas['SegmentPatch'] }) =>
    unwrap(api.PATCH('/api/sessions/{id}/segments/{segmentId}', { params: { path: { ...path, segmentId } }, body: patch })))

export const useDeleteSegment = (sessionId: string) =>
  useSegmentMutation(sessionId, (path, segmentId: string) =>
    unwrap(api.DELETE('/api/sessions/{id}/segments/{segmentId}', { params: { path: { ...path, segmentId } } })))

export const useSplitSegment = (sessionId: string) =>
  useSegmentMutation(sessionId, (path, { segmentId, atT }: { segmentId: string; atT: number }) =>
    unwrap(api.POST('/api/sessions/{id}/segments/{segmentId}/split', { params: { path: { ...path, segmentId } }, body: { atT } })))

/** Merges consecutive segments; the first keeps its drill type and label. */
export const useMergeSegments = (sessionId: string) =>
  useSegmentMutation(sessionId, (path, segmentIds: string[]) =>
    unwrap(api.POST('/api/sessions/{id}/segments/merge', { params: { path }, body: { segmentIds } })))

/** Replaces all segments with the laps of the FIT file. */
export const useResetSegments = (sessionId: string) =>
  useSegmentMutation<void>(sessionId, (path) =>
    unwrap(api.POST('/api/sessions/{id}/segments/reset-from-laps', { params: { path, query: { confirm: true } } })))
