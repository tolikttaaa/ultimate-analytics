import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, unwrap } from './client'
import { sessionKeys } from './sessions'
import type { Schemas, Surface } from './types'

export interface DrillTypeStatsFilter {
  surface?: Surface
  from?: string
  to?: string
}

export const drillTypeKeys = {
  all: ['drill-types'] as const,
  stats: (id: string, filter: DrillTypeStatsFilter) => [...drillTypeKeys.all, 'stats', id, filter] as const,
}

/** GET /api/drill-types: names and colours of segment types (spec 10.3). */
export function useDrillTypes() {
  return useQuery({
    queryKey: drillTypeKeys.all,
    queryFn: () => unwrap(api.GET('/api/drill-types')),
  })
}

/** GET /api/drill-types/{id}/stats: totals over all its segments and one row per session (spec 6.5). */
export function useDrillTypeStats(id: string, filter: DrillTypeStatsFilter) {
  return useQuery({
    queryKey: drillTypeKeys.stats(id, filter),
    queryFn: () => unwrap(api.GET('/api/drill-types/{id}/stats', { params: { path: { id }, query: filter } })),
  })
}

/** Drill types colour and name the segments of every session: both are refreshed. */
function useDrillTypeMutation<T, R>(call: (input: T) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: call,
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: drillTypeKeys.all }),
      queryClient.invalidateQueries({ queryKey: sessionKeys.all }),
    ]),
  })
}

/** POST /api/drill-types; 409 when the code is taken. */
export const useCreateDrillType = () =>
  useDrillTypeMutation((body: Schemas['DrillTypeCreate']) => unwrap(api.POST('/api/drill-types', { body })))

/** PATCH /api/drill-types/{id}: absent fields stay; 409 when the code is taken. */
export const useUpdateDrillType = () =>
  useDrillTypeMutation(({ id, patch }: { id: string; patch: Schemas['DrillTypePatch'] }) =>
    unwrap(api.PATCH('/api/drill-types/{id}', { params: { path: { id } }, body: patch })))

/** DELETE /api/drill-types/{id}: its segments stay, without a type. */
export const useDeleteDrillType = () =>
  useDrillTypeMutation((id: string) => unwrap(api.DELETE('/api/drill-types/{id}', { params: { path: { id } } })))
