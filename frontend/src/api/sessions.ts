import { useQuery } from '@tanstack/react-query'
import { api, unwrap } from './client'
import type { Surface } from './types'

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
}

/** GET /api/sessions */
export function useSessions(filter: SessionFilter = {}) {
  return useQuery({
    queryKey: sessionKeys.list(filter),
    queryFn: () => unwrap(api.GET('/api/sessions', { params: { query: filter } })),
  })
}
