import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api, unwrap } from './client'
import { sessionKeys } from './sessions'
import type { GeozoneCreate } from './types'

export const geozoneKeys = {
  all: ['geozones'] as const,
}

/** POST /api/geozones: also classifies the sessions again, so session queries are refreshed. */
export function useCreateGeozone() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (geozone: GeozoneCreate) => unwrap(api.POST('/api/geozones', { body: geozone })),
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: geozoneKeys.all }),
      queryClient.invalidateQueries({ queryKey: sessionKeys.all }),
    ]),
  })
}
