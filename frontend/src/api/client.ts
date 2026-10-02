import createClient from 'openapi-fetch'
import type { paths } from './schema'

/** Typed client of the backend API, generated from openapi.json (spec 10.3). Same origin; the dev server proxies /api. */
export const api = createClient<paths>({
  baseUrl: window.location.origin,
  // Resolved on every call, so tests can stub the global fetch.
  fetch: (request) => globalThis.fetch(request),
})

/** An RFC 7807 problem detail, as the API returns for every error (spec 9). */
export interface Problem {
  status?: number
  title?: string
  detail?: string
}

/** A failed API call. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: Problem | undefined

  constructor(status: number, problem: Problem | undefined) {
    super(problem?.detail ?? problem?.title ?? `Request failed with status ${status}`)
    this.status = status
    this.problem = problem
  }
}

/** The data of a successful call; throws [ApiError] otherwise, so TanStack Query sees the error. */
export async function unwrap<T>(call: Promise<{ data?: T; error?: unknown; response: Response }>): Promise<T> {
  const { data, error, response } = await call
  if (!response.ok || error !== undefined) {
    throw new ApiError(response.status, typeof error === 'object' && error !== null ? (error as Problem) : undefined)
  }
  return data as T
}
