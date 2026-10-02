import { useQuery } from '@tanstack/react-query'
import { api, unwrap } from './client'

/** GET /api/analysis/parameters: the parameters change only with a new release. */
export function useAnalysisParameters() {
  return useQuery({
    queryKey: ['analysis', 'parameters'],
    queryFn: () => unwrap(api.GET('/api/analysis/parameters')),
    staleTime: Infinity,
  })
}
