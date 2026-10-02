import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, unwrap } from './client'
import { sessionKeys } from './sessions'
import type { GeozoneCreate, Schemas } from './types'

export const geozoneKeys = {
  all: ['geozones'] as const,
}

/** GET /api/geozones: all geozones; few enough to load at once. */
export function useGeozones() {
  return useQuery({
    queryKey: geozoneKeys.all,
    queryFn: () => unwrap(api.GET('/api/geozones')),
  })
}

/** Geozone changes match the sessions again (spec 6.6): geozones and sessions are refreshed. */
function useGeozoneMutation<T, R>(call: (input: T) => Promise<R>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: call,
    onSuccess: () => Promise.all([
      queryClient.invalidateQueries({ queryKey: geozoneKeys.all }),
      queryClient.invalidateQueries({ queryKey: sessionKeys.all }),
    ]),
  })
}

/** POST /api/geozones; the result tells how many sessions changed. */
export const useCreateGeozone = () =>
  useGeozoneMutation((geozone: GeozoneCreate) => unwrap(api.POST('/api/geozones', { body: geozone })))

/** PATCH /api/geozones/{id}: absent fields stay. */
export const useUpdateGeozone = () =>
  useGeozoneMutation(({ id, patch }: { id: string; patch: Schemas['GeozonePatch'] }) =>
    unwrap(api.PATCH('/api/geozones/{id}', { params: { path: { id } }, body: patch })))

/** DELETE /api/geozones/{id}: its sessions are matched again. */
export const useDeleteGeozone = () =>
  useGeozoneMutation((id: string) => unwrap(api.DELETE('/api/geozones/{id}', { params: { path: { id } } })))
