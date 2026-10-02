import { useQuery } from '@tanstack/react-query'
import { api, unwrap } from './client'

/** GET /api/config: settings of the deployment, e.g. the base maps; they change only with a restart. */
export function useUiConfig() {
  return useQuery({
    queryKey: ['config'],
    queryFn: () => unwrap(api.GET('/api/config')),
    staleTime: Infinity,
  })
}
