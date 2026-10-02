import { vi } from 'vitest'

export interface ApiCall {
  method: string
  path: string
  query: URLSearchParams
  body: unknown
}

type Handler = (call: ApiCall) => unknown

/** Replaces fetch with fixed answers per `METHOD /path` and records every call. */
export function fakeApi(handlers: Record<string, Handler>): ApiCall[] {
  const calls: ApiCall[] = []
  vi.stubGlobal('fetch', async (input: Request | string, init?: RequestInit) => {
    const request = typeof input === 'string' ? undefined : input
    const url = new URL(request?.url ?? (input as string), window.location.origin)
    const method = request?.method ?? init?.method ?? 'GET'
    let body: unknown = init?.body
    if (request && method !== 'GET') {
      const text = await request.text()
      body = text ? JSON.parse(text) : undefined
    }
    const call = { method, path: url.pathname, query: url.searchParams, body }
    calls.push(call)
    const handler = handlers[`${method} ${url.pathname}`]
    if (!handler) return problem(404, `No fake for ${method} ${url.pathname}`)
    const result = handler(call)
    return result instanceof Response ? result : json(result)
  })
  return calls
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export function problem(status: number, detail: string): Response {
  return new Response(JSON.stringify({ status, detail }), { status, headers: { 'Content-Type': 'application/problem+json' } })
}
