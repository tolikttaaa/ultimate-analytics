import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, postForm, unwrap } from './client'
import type { SessionPatch, Surface, UploadResult } from './types'

export interface SessionFilter {
  /** ISO-8601 instant, inclusive */
  from?: string
  /** ISO-8601 instant, exclusive */
  to?: string
  surface?: Surface
  page?: number
  size?: number
}

/** Query keys of the sessions API; mutations invalidate them (spec 10.3). */
export const sessionKeys = {
  all: ['sessions'] as const,
  list: (filter: SessionFilter) => [...sessionKeys.all, 'list', filter] as const,
  detail: (id: string) => [...sessionKeys.all, 'detail', id] as const,
  series: (id: string) => [...sessionKeys.all, 'series', id] as const,
  efforts: (id: string) => [...sessionKeys.all, 'efforts', id] as const,
  metrics: (id: string, from: number, to: number) => [...sessionKeys.all, 'metrics', id, from, to] as const,
}

/** GET /api/sessions */
export function useSessions(filter: SessionFilter = {}) {
  return useQuery({
    queryKey: sessionKeys.list(filter),
    queryFn: () => unwrap(api.GET('/api/sessions', { params: { query: filter } })),
  })
}

/** GET /api/sessions/{id}; waits while there is no id. */
export function useSession(id: string) {
  return useQuery({
    queryKey: sessionKeys.detail(id),
    queryFn: () => unwrap(api.GET('/api/sessions/{id}', { params: { path: { id } } })),
    enabled: id !== '',
  })
}

/** POST /api/sessions/upload: one result per FIT file, also for every file inside a .zip. */
export function useUploadSessions() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (files: File[]) => {
      const form = new FormData()
      files.forEach((file) => form.append('files', file))
      return postForm<UploadResult[]>('/api/sessions/upload', form)
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sessionKeys.all }),
  })
}

/** PATCH /api/sessions/{id}: a surface set here becomes MANUAL. */
export function useUpdateSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, patch }: { id: string; patch: SessionPatch }) =>
      unwrap(api.PATCH('/api/sessions/{id}', { params: { path: { id } }, body: patch })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sessionKeys.all }),
  })
}

/**
 * DELETE /api/sessions/{id} for each session: removes it with its samples, segments, efforts and stored FIT file.
 * Tries every session, then fails with a count when any could not be deleted.
 */
export function useDeleteSessions() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (ids: string[]) => {
      const failed: string[] = []
      for (const id of ids) {
        try {
          await unwrap(api.DELETE('/api/sessions/{id}', { params: { path: { id } } }))
        } catch {
          failed.push(id)
        }
      }
      if (failed.length > 0) throw new Error(`${failed.length} of ${ids.length} sessions could not be deleted.`)
    },
    // Also after a partial failure: some sessions are gone. Their own queries are left alone: refetched they would
    // fail and replace a session screen that is about to navigate away.
    onSettled: (_data, _error, ids) => Promise.all([
      queryClient.invalidateQueries({
        queryKey: sessionKeys.all,
        predicate: (query) => !ids.some((id) => query.queryKey.includes(id)),
      }),
      queryClient.invalidateQueries({ queryKey: ['drill-types'] }),
    ]),
  })
}

/** The confirmation before deleting sessions. */
export function confirmDeleteSessions(count: number): boolean {
  const what = count === 1 ? 'this session' : `${count} sessions`
  return window.confirm(
    `Delete ${what}? Segments and notes are lost and the FIT files are removed; you can upload the files again.`,
  )
}

/** GET /api/sessions/{id}/series: the 1 Hz series in columns, null in gaps. */
export function useSessionSeries(id: string) {
  return useQuery({
    queryKey: sessionKeys.series(id),
    queryFn: () => unwrap(api.GET('/api/sessions/{id}/series', { params: { path: { id } } })),
  })
}

/** GET /api/sessions/{id}/efforts */
export function useEfforts(id: string) {
  return useQuery({
    queryKey: sessionKeys.efforts(id),
    queryFn: () => unwrap(api.GET('/api/sessions/{id}/efforts', { params: { path: { id } } })),
  })
}

/** POST /api/sessions/{id}/recompute: replaces the series, efforts and metrics of the session. */
export function useRecomputeSession() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => unwrap(api.POST('/api/sessions/{id}/recompute', { params: { path: { id } } })),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sessionKeys.all }),
  })
}

/** GET /api/sessions/{id}/metrics?from&to: metrics of a window (spec 6.5); disabled without a window. */
export function useWindowMetrics(id: string, window: [number, number] | null) {
  const [from, to] = window ?? [0, 0]
  return useQuery({
    queryKey: sessionKeys.metrics(id, from, to),
    queryFn: () => unwrap(api.GET('/api/sessions/{id}/metrics', { params: { path: { id }, query: { from, to } } })),
    enabled: window !== null,
  })
}
