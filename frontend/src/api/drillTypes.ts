import { useQuery } from '@tanstack/react-query'
import { api, unwrap } from './client'

export const drillTypeKeys = {
  all: ['drill-types'] as const,
}

/** GET /api/drill-types: names and colours of segment types (spec 10.3). */
export function useDrillTypes() {
  return useQuery({
    queryKey: drillTypeKeys.all,
    queryFn: () => unwrap(api.GET('/api/drill-types')),
  })
}
