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
}

/** GET /api/sessions */
export function useSessions(filter: SessionFilter = {}) {
  return useQuery({
    queryKey: sessionKeys.list(filter),
    queryFn: () => unwrap(api.GET('/api/sessions', { params: { query: filter } })),
  })
}

/** GET /api/sessions/{id} */
export function useSession(id: string) {
  return useQuery({
    queryKey: sessionKeys.detail(id),
    queryFn: () => unwrap(api.GET('/api/sessions/{id}', { params: { path: { id } } })),
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
